package fr.dreamin.dreamvoice.core.speaker.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.core.recording.player.OpusAudioPlayer;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import fr.dreamin.dreamvoice.api.speaker.event.SpeakerPlaySoundEvent;
import fr.dreamin.dreamvoice.api.speaker.event.SpeakerRegisterEvent;
import fr.dreamin.dreamvoice.api.speaker.event.SpeakerStopSoundEvent;
import fr.dreamin.dreamvoice.api.speaker.event.SpeakerUnregisterEvent;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.speaker.storage.SpeakersPersistence;
import fr.dreamin.dreamvoice.core.utils.RawUtils;
import fr.dreamin.dreamvoice.core.utils.audio.AudioLimiter;
import fr.dreamin.dreamvoice.core.utils.raycast.VoiceRayCast;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link VoiceSpeakerService} managing 3D locational speakers,
 * dual-channel audio streams, real-time microphone broadcast, and JSON persistence.
 */
public final class VoiceSpeakerServiceImpl implements VoiceSpeakerService, Listener {

  // ###############################################################
  // ----------------------- STATIC FIELDS -------------------------
  // ###############################################################

  private static final long CLEANUP_INTERVAL_TICKS = 600L;
  private static final long INACTIVITY_TIMEOUT_MS = 30000L;
  private static final String CATEGORY_ID = "speaker_volume";
  private static final String CATEGORY_NAME = "Speaker";
  private static final String CATEGORY_DESC = "Speaker Volume";

  // ###############################################################
  // --------------------- INSTANCE FIELDS -------------------------
  // ###############################################################

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;

  private VolumeCategory volumeCategory;

  private final @NotNull Map<UUID, Speaker> speakers = new ConcurrentHashMap<>();
  private final @NotNull Map<String, LocationalAudioChannel> listenerChannels = new ConcurrentHashMap<>();
  private final @NotNull Map<String, OpusEncoder> streamEncoders = new ConcurrentHashMap<>();
  private final @NotNull Map<String, Long> lastChannelActivity = new ConcurrentHashMap<>();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceSpeakerServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;

    Bukkit.getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupIdleChannels, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
  }

  // ###############################################################
  // ------------------- PUBLIC SERVICE METHODS --------------------
  // ###############################################################

  @Override
  public VoicechatServerApi getAPI() {
    return this.api;
  }

  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;

    this.volumeCategory = this.api.volumeCategoryBuilder()
      .setId(CATEGORY_ID)
      .setName(CATEGORY_NAME)
      .setDescription(CATEGORY_DESC)
      .build();

    this.api.registerVolumeCategory(this.volumeCategory);
  }

  @Override
  public Collection<Speaker> getSpeakers() {
    return this.speakers.values();
  }

  @Override
  public @Nullable Speaker getSpeaker(final @NotNull UUID uuid) {
    return this.speakers.get(uuid);
  }

  @Override
  public @Nullable Speaker getSpeaker(final @NotNull String name) {
    return this.speakers.values().stream()
      .filter(s -> s.getName().equalsIgnoreCase(name))
      .findFirst()
      .orElse(null);
  }

  @Override
  public void register(final @NotNull Speaker speaker) {
    final var event = new SpeakerRegisterEvent(speaker);
    if (!event.callEvent())
      return;
    this.speakers.put(speaker.getUuid(), speaker);
  }

  @Override
  public void unregister(final @NotNull UUID uuid) {
    final var speaker = this.speakers.remove(uuid);
    if (speaker != null) {
      speaker.stopPlaying();
      new SpeakerUnregisterEvent(speaker).callEvent();
    }
  }

  @Override
  public void unregister(final @NotNull Speaker speaker) {
    unregister(speaker.getUuid());
  }

  @Override
  public void unregisterAll() {
    getSpeakers().forEach(this::unregister);
  }

  @Override
  public VolumeCategory getVolumeCategory() {
    return this.volumeCategory;
  }

  @Override
  public void playRecording(final @NotNull Speaker speaker, final @NotNull VoiceRecording recording) {
    playRecording(List.of(speaker), recording, false);
  }

  @Override
  public void playRecording(final @NotNull Speaker speaker, final @NotNull VoiceRecording recording, final boolean loop) {
    playRecording(List.of(speaker), recording, loop);
  }

  @Override
  public void playRecording(final @NotNull Collection<Speaker> speakers, final @NotNull VoiceRecording recording) {
    playRecording(speakers, recording, false);
  }

  @Override
  public void playRecording(final @NotNull Collection<Speaker> speakers, final @NotNull VoiceRecording recording, final boolean loop) {
    final var frames = recording.getAudioFrames();
    if (frames.isEmpty() || speakers.isEmpty())
      return;

    final var channels = speakers.stream()
      .map(Speaker::getSpeakerChannel)
      .filter(ch -> !ch.isClosed())
      .toList();

    if (channels.isEmpty())
      return;

    for (final var speaker : speakers) {
      speaker.stopPlaying();
    }

    final var totalDurationMs = (long) (recording.getDurationSeconds() * 1000L);
    final var player = new OpusAudioPlayer(channels, frames, totalDurationMs, loop);
    for (final var speaker : speakers) {
      speaker.setActiveAudioPlayer(player);
    }

    player.setOnStopped(() -> {
      for (final var speaker : speakers) {
        if (speaker.getActiveAudioPlayer() == player) {
          speaker.setActiveAudioPlayer(null);
        }
      }
    });

    player.startPlaying();
  }

  @Override
  public void playSound(final @NotNull Speaker speaker, final short @NotNull [] pcm) {
    playSound(speaker, pcm, false);
  }

  @Override
  public void playSound(final @NotNull Speaker speaker, final short @NotNull [] pcm, final boolean loop) {
    final var playEvent = new SpeakerPlaySoundEvent(speaker, "pcm", loop);
    if (!playEvent.callEvent())
      return;
    speaker.stopPlaying();

    try {
      final var encoder = this.api.createEncoder();
      final var player = this.api.createAudioPlayer(speaker.getSpeakerChannel(), encoder, pcm);

      speaker.setActiveAudioPlayer(player);
      player.setOnStopped(() -> {
        speaker.setActiveAudioPlayer(null);
        if (loop) {
          Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (this.speakers.containsKey(speaker.getUuid()))
              playSound(speaker, pcm, true);
          });
        }
      });
      player.startPlaying();
    } catch (Exception e) {
      this.plugin.getLogger().severe("Error playing audio on speaker: " + e.getMessage());
    }
  }

  @Override
  public void playSoundFile(final @NotNull Speaker speaker, final @NotNull String fileName, final boolean loop) {
    playSoundFile(List.of(speaker), fileName, loop);
  }

  @Override
  public void playSoundFile(final @NotNull Collection<Speaker> speakers, final @NotNull String fileName, final boolean loop) {
    if (speakers.isEmpty())
      return;

    CompletableFuture.runAsync(() -> {
      try {
        final var soundDir = new File(this.plugin.getDataFolder(), "sounds");
        if (!soundDir.exists())
          soundDir.mkdirs();

        final var soundFile = new File(soundDir, fileName);
        if (!soundFile.exists()) {
          this.plugin.getLogger().warning("Sound file not found: " + soundFile.getAbsolutePath());
          return;
        }

        final var pcm = RawUtils.fileToShorts48Hz(soundFile);
        if (pcm.length > 0) {
          Bukkit.getScheduler().runTask(this.plugin, () -> {
            for (final var speaker : speakers) {
              playSound(speaker, pcm, loop);
            }
          });
        }
      } catch (Exception e) {
        this.plugin.getLogger().severe("Error loading sound file: " + e.getMessage());
      }
    });
  }

  @Override
  public void playSoundUrl(final @NotNull Speaker speaker, final @NotNull String url, final boolean loop) {
    playSoundUrl(List.of(speaker), url, loop);
  }

  @Override
  public void playSoundUrl(final @NotNull Collection<Speaker> speakers, final @NotNull String url, final boolean loop) {
    if (speakers.isEmpty())
      return;

    CompletableFuture.runAsync(() -> {
      try {
        final var pcm = RawUtils.urlToShorts48Hz(url);
        if (pcm.length > 0) {
          Bukkit.getScheduler().runTask(this.plugin, () -> {
            for (final var speaker : speakers) {
              playSound(speaker, pcm, loop);
            }
          });
        }
      } catch (Exception e) {
        this.plugin.getLogger().severe("Error streaming sound from URL: " + e.getMessage());
      }
    });
  }

  @Override
  public void stopSound(final @NotNull Speaker speaker) {
    speaker.stopPlaying();
    new SpeakerStopSoundEvent(speaker).callEvent();
  }

  @Override
  public void save() {
    SpeakersPersistence.save(this, new File(this.plugin.getDataFolder(), "data"));
  }

  @Override
  public void load() {
    unregisterAll();
    SpeakersPersistence.load(new File(this.plugin.getDataFolder(), "data"));
  }

  @Override
  public void save(final @NotNull UUID uuid) {
    save();
  }

  // ###############################################################
  // ------------------- PRIVATE HELPER METHODS --------------------
  // ###############################################################

  private void cleanupIdleChannels() {
    final var now = System.currentTimeMillis();
    this.lastChannelActivity.entrySet().removeIf(entry -> {
      if (now - entry.getValue() > INACTIVITY_TIMEOUT_MS) {
        this.listenerChannels.remove(entry.getKey());
        final var enc = this.streamEncoders.remove(entry.getKey());
        if (enc != null && !enc.isClosed()) {
          try {
            enc.close();
          } catch (Throwable ignored) {}
        }
        return true;
      }
      return false;
    });
  }

  private void broadcastToDedicatedChannels(final @NotNull List<Speaker> speakers, final @NotNull MicrophonePacketEvent event) {
    final var rawOpus = event.getPacket().getOpusEncodedData();
    if (rawOpus == null || rawOpus.length == 0)
      return;

    speakers.forEach(speaker -> {
      final var vc = speaker.getVoiceChannel();
      final var ch = Objects.requireNonNullElseGet(vc, speaker::getSpeakerChannel);
      ch.setFilter(speaker.getFilter());
      ch.send(rawOpus);
    });
  }

  private void processSingleListenerStream(
    final @NotNull Speaker speaker,
    final @NotNull UUID senderUuid,
    final @NotNull Player listener,
    final byte @NotNull [] rawOpus,
    final short @Nullable [] basePcm,
    final @Nullable VoiceService voiceService,
    final @Nullable VoiceWallService wallService,
    final @Nullable VoiceFilterService filterService,
    final boolean hasFilters,
    final boolean isWallEnabled,
    final float maxDist,
    final double dist,
    final long now
  ) {
    final var listenerConn = this.api.getConnectionOf(listener.getUniqueId());
    if (listenerConn == null)
      return;

    final var speakerFilter = speaker.getFilter();
    if (speakerFilter != null && !speakerFilter.test(listenerConn.getPlayer()))
      return;

    var totalDbLoss = 0.0;
    if (isWallEnabled) {
      final var ray = VoiceRayCast.check(speaker.getLocation(), listener);
      if (!ray.lineOfSight())
        totalDbLoss = ray.totalAttenuation();
    }

    if (totalDbLoss >= 99.0)
      return;

    final var hasAttenuation = totalDbLoss > 0.001;

    byte[] audioToSend = rawOpus;

    if (hasFilters || hasAttenuation) {
      if (basePcm == null || basePcm.length == 0)
        return;

      try {
        var processedPcm = basePcm.clone();
        if (hasFilters && filterService != null)
          processedPcm = filterService.applyFilters(senderUuid, processedPcm);

        if (hasAttenuation) {
          final var wallGain = (float) Math.pow(10.0, -totalDbLoss / 20.0);
          for (int i = 0; i < processedPcm.length; i++)
            processedPcm[i] = (short) Math.clamp(Math.round(processedPcm[i] * wallGain), Short.MIN_VALUE, Short.MAX_VALUE);
        }

        processedPcm = AudioLimiter.process(processedPcm);

        final var streamKey = speaker.getUuid() + ":" + senderUuid + ":" + listener.getUniqueId();
        var encoder = this.streamEncoders.computeIfAbsent(streamKey, _ -> this.api.createEncoder());
        if (encoder.isClosed()) {
          encoder = this.api.createEncoder();
          this.streamEncoders.put(streamKey, encoder);
        }

        audioToSend = encoder.encode(processedPcm);
      } catch (Exception e) {
        this.plugin.getLogger().warning("Error processing listener DSP audio: " + e.getMessage());
        return;
      }
    }

    final var streamKey = speaker.getUuid() + ":" + senderUuid + ":" + listener.getUniqueId();
    var ch = this.listenerChannels.get(streamKey);
    if (ch == null || ch.isClosed()) {
      final var spkLoc = speaker.getLocation();
      final var sLevel = this.api.fromServerLevel(spkLoc.getWorld());
      final var pos = this.api.createPosition(spkLoc.getX(), spkLoc.getY(), spkLoc.getZ());
      final var channelUuid = UUID.nameUUIDFromBytes(streamKey.getBytes(StandardCharsets.UTF_8));
      ch = this.api.createLocationalAudioChannel(channelUuid, sLevel, pos);
      if (ch != null) {
        final var targetUuid = listener.getUniqueId();
        ch.setFilter(sp -> sp.getUuid().equals(targetUuid));
        ch.setDistance(maxDist);
        if (this.volumeCategory != null)
          ch.setCategory(this.volumeCategory.getId());
        this.listenerChannels.put(streamKey, ch);
      }
    }

    if (ch != null) {
      final var spkLoc = speaker.getLocation();
      ch.updateLocation(this.api.createPosition(spkLoc.getX(), spkLoc.getY(), spkLoc.getZ()));
      ch.send(audioToSend);
      this.lastChannelActivity.put(streamKey, now);
    }
  }

  // ###############################################################
  // ---------------------- EVENT LISTENERS ------------------------
  // ###############################################################

  @EventHandler
  private void onMicrophonePacket(final @NotNull MicrophonePacketEvent event) {
    final var sender = event.getSender();
    if (sender == null)
      return;

    final var senderUuid = sender.getPlayer().getUuid();
    final var matchingSpeakers = this.speakers.values().stream()
      .filter(s -> s.isSpeakerAllowed(senderUuid))
      .toList();

    if (matchingSpeakers.isEmpty())
      return;

    final var wallService = DreamVoice.getService(VoiceWallService.class);
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    final var voiceService = DreamVoice.getService(VoiceService.class);
    final var hasFilters = filterService != null && filterService.hasExplicitFilters(senderUuid);
    final var isWallEnabled = wallService != null && wallService.isEnable();

    if (!isWallEnabled && !hasFilters) {
      broadcastToDedicatedChannels(matchingSpeakers, event);
      return;
    }

    final var rawOpus = event.getPacket().getOpusEncodedData();
    if (rawOpus == null || rawOpus.length == 0)
      return;

    final var now = System.currentTimeMillis();

    short[] basePcm = null;
    if (voiceService != null) {
      try {
        final var decoder = voiceService.getDecoder(senderUuid);
        basePcm = decoder.decode(rawOpus);
      } catch (Exception e) {
        this.plugin.getLogger().warning("Error decoding speaker mic packet: " + e.getMessage());
      }
    }

    for (final var speaker : matchingSpeakers) {
      final var spkLoc = speaker.getLocation();
      final var spkWorld = spkLoc.getWorld();
      if (spkWorld == null)
        continue;

      final var maxDist = speaker.getDistance() != null ? speaker.getDistance() : 16.0f;

      for (final var listener : Bukkit.getOnlinePlayers()) {
        if (!listener.getWorld().equals(spkWorld))
          continue;

        final var dist = spkLoc.distance(listener.getLocation());
        if (dist > maxDist)
          continue;

        processSingleListenerStream(speaker, senderUuid, listener, rawOpus, basePcm, voiceService, wallService, filterService, hasFilters, isWallEnabled, maxDist, dist, now);
      }
    }
  }

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    final var uidStr = event.getPlayer().getUniqueId().toString();
    this.listenerChannels.keySet().removeIf(k -> k.contains(uidStr));
    this.lastChannelActivity.keySet().removeIf(k -> k.contains(uidStr));
    this.streamEncoders.entrySet().removeIf(e -> {
      if (e.getKey().contains(uidStr)) {
        if (!e.getValue().isClosed()) {
          try {
            e.getValue().close();
          } catch (Throwable ignored) {}
        }
        return true;
      }
      return false;
    });
  }

}
