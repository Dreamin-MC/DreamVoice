package fr.dreamin.dreamvoice.core.speaker.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
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
  private boolean voiceServiceMissingLogged = false;

  private final @NotNull Map<UUID, Speaker> speakers = new ConcurrentHashMap<>();
  private final @NotNull Map<String, StaticAudioChannel> listenerChannels = new ConcurrentHashMap<>();
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
    this.speakers.put(speaker.getUuid(), speaker);
  }

  @Override
  public void unregister(final @NotNull UUID uuid) {
    final var speaker = this.speakers.remove(uuid);
    if (speaker != null)
      speaker.stopPlaying();
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
    final var frames = recording.getAudioFrames();
    if (frames.isEmpty())
      return;

    final var pcmList = new ArrayList<short[]>();
    var totalSamples = 0;
    var currentStreamTimeMs = 0L;

    try {
      final var decoder = this.api.createDecoder();

      for (final var frame : frames) {
        if (frame.data().length == 0)
          continue;

        final var frameTime = frame.timestampMs();

        if (frameTime - currentStreamTimeMs >= 60) {
          final var silenceMs = frameTime - currentStreamTimeMs;
          final var silenceSamples = (int) (silenceMs * 48);
          if (silenceSamples > 0) {
            pcmList.add(new short[silenceSamples]);
            totalSamples += silenceSamples;
          }
          currentStreamTimeMs = frameTime;
        }

        final var pcm = decoder.decode(frame.data());
        if (pcm != null && pcm.length > 0) {
          pcmList.add(pcm);
          totalSamples += pcm.length;
          currentStreamTimeMs += (pcm.length / 48);
        }
      }
    } catch (Exception e) {
      this.plugin.getLogger().severe("Error decoding Opus frames for speaker: " + e.getMessage());
      return;
    }

    if (totalSamples == 0)
      return;

    final var fullPcm = new short[totalSamples];
    var offset = 0;
    for (final var chunk : pcmList) {
      System.arraycopy(chunk, 0, fullPcm, offset, chunk.length);
      offset += chunk.length;
    }

    playSound(speaker, fullPcm, false);
  }

  @Override
  public void playSound(final @NotNull Speaker speaker, final short @NotNull [] pcm) {
    playSound(speaker, pcm, false);
  }

  @Override
  public void playSound(final @NotNull Speaker speaker, final short @NotNull [] pcm, final boolean loop) {
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
        if (pcm.length > 0)
          Bukkit.getScheduler().runTask(this.plugin, () -> playSound(speaker, pcm, loop));
      } catch (Exception e) {
        this.plugin.getLogger().severe("Error loading sound file: " + e.getMessage());
      }
    });
  }

  @Override
  public void playSoundUrl(final @NotNull Speaker speaker, final @NotNull String url, final boolean loop) {
    CompletableFuture.runAsync(() -> {
      try {
        final var pcm = RawUtils.urlToShorts48Hz(url);
        if (pcm.length > 0)
          Bukkit.getScheduler().runTask(this.plugin, () -> playSound(speaker, pcm, loop));
      } catch (Exception e) {
        this.plugin.getLogger().severe("Error streaming sound from URL: " + e.getMessage());
      }
    });
  }

  @Override
  public void stopSound(final @NotNull Speaker speaker) {
    speaker.stopPlaying();
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
        return true;
      }
      return false;
    });
  }

  private void broadcastToDedicatedChannels(final @NotNull List<Speaker> speakers, final @NotNull MicrophonePacketEvent event) {
    speakers.forEach(speaker -> {
      final var vc = speaker.getVoiceChannel();
      Objects.requireNonNullElseGet(vc, speaker::getSpeakerChannel).send(event.getPacket());
    });
  }

  private void processSingleListenerStream(
    final @NotNull Speaker speaker,
    final @NotNull UUID senderUuid,
    final @NotNull Player listener,
    final @NotNull short[] pcm,
    final @Nullable VoiceWallService wallService,
    final @Nullable VoiceFilterService filterService,
    final boolean hasFilters,
    final boolean isWallEnabled,
    final float maxDist,
    final double dist,
    final @NotNull de.maxhenkel.voicechat.api.opus.OpusEncoder encoder,
    final long now
  ) {
    final var listenerConn = this.api.getConnectionOf(listener.getUniqueId());
    if (listenerConn == null)
      return;

    var totalDbLoss = 0.0;
    if (isWallEnabled) {
      final var ray = VoiceRayCast.check(speaker.getLocation(), listener);
      if (ray.isBlocked())
        totalDbLoss = ray.totalAttenuation();
    }

    if (totalDbLoss >= 99.0)
      return;

    final var distRatio = Math.min(1.0, dist / maxDist);
    final var distGain = (float) Math.max(0.05, 1.0 - (distRatio * 0.85));
    final var wallGain = (float) Math.pow(10.0, -totalDbLoss / 20.0);
    final var totalGain = wallGain * distGain;

    var processedPcm = pcm.clone();
    if (hasFilters && filterService != null)
      processedPcm = filterService.applyFilters(senderUuid, processedPcm);

    for (int i = 0; i < processedPcm.length; i++)
      processedPcm[i] = (short) Math.clamp(Math.round(processedPcm[i] * totalGain), Short.MIN_VALUE, Short.MAX_VALUE);

    if (wallService != null && wallService.isAirDampingEnabled() && dist > 5.0) {
      final var alpha = Math.max(0.10f, 1.0f - (float) (dist - 5.0) * 0.038f);
      var smooth = (float) processedPcm[0];
      for (int i = 0; i < processedPcm.length; i++) {
        smooth = smooth + alpha * (processedPcm[i] - smooth);
        processedPcm[i] = (short) Math.clamp(Math.round(smooth), Short.MIN_VALUE, Short.MAX_VALUE);
      }
    }

    processedPcm = AudioLimiter.process(processedPcm);

    final var listenerOpus = encoder.encode(processedPcm);
    final var streamKey = speaker.getUuid() + ":" + senderUuid + ":" + listener.getUniqueId();
    final var ch = this.listenerChannels.computeIfAbsent(streamKey, _ -> {
      final var sc = this.api.createStaticAudioChannel(UUID.randomUUID());
      if (sc != null) {
        sc.addTarget(listenerConn);
        if (this.volumeCategory != null)
          sc.setCategory(this.volumeCategory.getId());
      }
      return sc;
    });

    if (ch != null) {
      ch.send(listenerOpus);
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
    final var hasFilters = filterService != null && filterService.hasActiveFilters(senderUuid);
    final var isWallEnabled = wallService != null && wallService.isEnable();

    if (!isWallEnabled && !hasFilters) {
      broadcastToDedicatedChannels(matchingSpeakers, event);
      return;
    }

    if (voiceService == null) {
      if (!this.voiceServiceMissingLogged) {
        this.voiceServiceMissingLogged = true;
        this.plugin.getLogger().warning("VoiceService is unavailable. Speaker audio processing is skipped.");
      }
      return;
    }

    try {
      final var decoder = voiceService.getDecoder(senderUuid);
      final var encoder = voiceService.getEncoder(senderUuid);
      final var pcm = decoder.decode(event.getPacket().getOpusEncodedData());
      if (pcm == null || pcm.length == 0)
        return;

      final var now = System.currentTimeMillis();

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

          processSingleListenerStream(speaker, senderUuid, listener, pcm, wallService, filterService, hasFilters, isWallEnabled, maxDist, dist, encoder, now);
        }
      }
    } catch (Exception e) {
      this.plugin.getLogger().warning("Error processing speaker audio: " + e.getMessage());
    }
  }

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    final var uidStr = event.getPlayer().getUniqueId().toString();
    this.listenerChannels.keySet().removeIf(k -> k.contains(uidStr));
    this.lastChannelActivity.keySet().removeIf(k -> k.contains(uidStr));
  }

}
