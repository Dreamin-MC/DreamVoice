package fr.dreamin.dreamvoice.common.speaker.service;

import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.speaker.event.SpeakerPlaySoundEvent;
import fr.dreamin.dreamvoice.api.speaker.event.SpeakerRegisterEvent;
import fr.dreamin.dreamvoice.api.speaker.event.SpeakerStopSoundEvent;
import fr.dreamin.dreamvoice.api.speaker.event.SpeakerUnregisterEvent;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.recording.player.OpusAudioPlayer;
import fr.dreamin.dreamvoice.common.speaker.storage.SpeakersPersistence;
import fr.dreamin.dreamvoice.common.utils.RawUtils;
import fr.dreamin.dreamvoice.common.utils.audio.AudioLimiter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform-independent implementation of {@link VoiceSpeakerService} managing 3D locational speakers,
 * dual-channel audio streams, real-time microphone broadcast, and JSON persistence.
 */
public final class VoiceSpeakerServiceImpl implements VoiceSpeakerService {

  private static final long CLEANUP_INTERVAL_TICKS = 600L;
  private static final long INACTIVITY_TIMEOUT_MS = 30000L;
  private static final String CATEGORY_ID = "speaker_volume";
  private static final String CATEGORY_NAME = "Speaker";
  private static final String CATEGORY_DESC = "Speaker Volume";

  private final @NotNull VoicePlatform platform;
  private @NotNull VoicechatServerApi api;
  private VolumeCategory volumeCategory;

  private final Map<UUID, Speaker> speakers = new ConcurrentHashMap<>();
  private final Map<String, Speaker> speakersByName = new ConcurrentHashMap<>();
  private final Map<String, LocationalAudioChannel> listenerChannels = new ConcurrentHashMap<>();
  private final Map<String, Long> lastChannelActivity = new ConcurrentHashMap<>();
  private final Map<String, OpusEncoder> streamEncoders = new ConcurrentHashMap<>();

  public VoiceSpeakerServiceImpl(final @NotNull VoicePlatform platform) {
    this.platform = platform;
    this.platform.runTimer(this::cleanupIdleChannels, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
  }

  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;

    this.volumeCategory = api.volumeCategoryBuilder()
      .setId(CATEGORY_ID)
      .setName(CATEGORY_NAME)
      .setDescription(CATEGORY_DESC)
      .build();

    api.registerVolumeCategory(this.volumeCategory);
  }

  @Override
  public VoicechatServerApi getAPI() {
    return this.api;
  }

  @Override
  public VolumeCategory getVolumeCategory() {
    return this.volumeCategory;
  }

  @Override
  public ServerLevel getServerLevel(final @NotNull String worldName) {
    return this.platform.getServerLevel(worldName);
  }

  @Override
  public Collection<Speaker> getSpeakers() {
    return Collections.unmodifiableCollection(this.speakers.values());
  }

  @Override
  public @Nullable Speaker getSpeaker(final @NotNull UUID uuid) {
    return this.speakers.get(uuid);
  }

  @Override
  public @Nullable Speaker getSpeaker(final @NotNull String name) {
    return this.speakersByName.get(name.toLowerCase());
  }

  @Override
  public void register(final @NotNull Speaker speaker) {
    this.speakers.put(speaker.getUuid(), speaker);
    this.speakersByName.put(speaker.getName().toLowerCase(), speaker);
    new SpeakerRegisterEvent(speaker).callEvent();
  }

  @Override
  public void unregister(final @NotNull UUID uuid) {
    final var speaker = this.speakers.remove(uuid);
    if (speaker != null) {
      this.speakersByName.remove(speaker.getName().toLowerCase());
      speaker.stopPlaying();
      new SpeakerUnregisterEvent(speaker).callEvent();
    }
  }

  @Override
  public void unregister(final @NotNull String name) {
    final var speaker = this.speakersByName.remove(name.toLowerCase());
    if (speaker != null) {
      this.speakers.remove(speaker.getUuid());
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
    for (final var speaker : this.speakers.values())
      speaker.stopPlaying();

    this.speakers.clear();
    this.speakersByName.clear();
    this.listenerChannels.clear();
    this.lastChannelActivity.clear();
    this.streamEncoders.values().forEach(enc -> {
      if (!enc.isClosed()) {
        try {
          enc.close();
        } catch (Throwable ignored) {}
      }
    });
    this.streamEncoders.clear();
  }

  @Override
  public void playRecording(final @NotNull Speaker speaker, final @NotNull VoiceRecording recording, final boolean loop) {
    final var playEvent = new SpeakerPlaySoundEvent(speaker, "recording:" + recording.getUuid(), loop);
    if (!playEvent.callEvent())
      return;
    speaker.stopPlaying();

    final var player = new OpusAudioPlayer(List.of(speaker.getSpeakerChannel()), recording.getFrames(), loop);
    speaker.setActiveAudioPlayer(player);
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
          this.platform.runSync(() -> {
            if (this.speakers.containsKey(speaker.getUuid()))
              playSound(speaker, pcm, true);
          });
        }
      });
      player.startPlaying();
    } catch (Exception e) {
      this.platform.logError("Error playing audio on speaker: " + e.getMessage(), e);
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

    this.platform.runAsync(() -> {
      try {
        final var soundDir = new File(this.platform.getDataDirectory().toFile(), "sounds");
        if (!soundDir.exists())
          soundDir.mkdirs();

        final var soundFile = new File(soundDir, fileName);
        if (!soundFile.exists()) {
          this.platform.logWarning("Sound file not found: " + soundFile.getAbsolutePath());
          return;
        }

        final var pcm = RawUtils.fileToShorts48Hz(soundFile);
        if (pcm.length > 0) {
          this.platform.runSync(() -> {
            for (final var speaker : speakers)
              playSound(speaker, pcm, loop);
          });
        }
      } catch (Exception e) {
        this.platform.logError("Error loading sound file: " + e.getMessage(), e);
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

    this.platform.runAsync(() -> {
      try {
        final var pcm = RawUtils.urlToShorts48Hz(url);
        if (pcm.length > 0) {
          this.platform.runSync(() -> {
            for (final var speaker : speakers)
              playSound(speaker, pcm, loop);
          });
        }
      } catch (Exception e) {
        this.platform.logError("Error streaming sound from URL: " + e.getMessage(), e);
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
    final var moduleDir = new File(this.platform.getDataDirectory().toFile(), "modules/speaker");
    SpeakersPersistence.save(this, moduleDir, this.platform);
  }

  @Override
  public void load() {
    unregisterAll();
    final var moduleDir = new File(this.platform.getDataDirectory().toFile(), "modules/speaker");
    SpeakersPersistence.load(moduleDir, this.platform);
  }

  @Override
  public void save(final @NotNull UUID uuid) {
    save();
  }

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

  private void processSingleListenerStream(
    final @NotNull Speaker speaker,
    final @NotNull UUID senderUuid,
    final @NotNull UUID listenerUuid,
    final @NotNull VoiceLocation listenerLoc,
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
    final var listenerConn = this.api.getConnectionOf(listenerUuid);
    if (listenerConn == null)
      return;

    final var speakerFilter = speaker.getFilter();
    if (speakerFilter != null && !speakerFilter.test(listenerConn.getPlayer()))
      return;

    var totalDbLoss = 0.0;
    if (isWallEnabled) {
      if (!this.platform.hasLineOfSight(speaker.getLocation(), listenerLoc))
        totalDbLoss = this.platform.computeAcousticOcclusion(speaker.getLocation(), listenerLoc, null);
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

        final var streamKey = speaker.getUuid() + ":" + senderUuid + ":" + listenerUuid;
        var encoder = this.streamEncoders.computeIfAbsent(streamKey, _ -> this.api.createEncoder());
        if (encoder.isClosed()) {
          encoder = this.api.createEncoder();
          this.streamEncoders.put(streamKey, encoder);
        }

        audioToSend = encoder.encode(processedPcm);
      } catch (Exception e) {
        this.platform.logWarning("Error processing listener DSP audio: " + e.getMessage());
        return;
      }
    }

    final var streamKey = speaker.getUuid() + ":" + senderUuid + ":" + listenerUuid;
    var ch = this.listenerChannels.get(streamKey);
    if (ch == null || ch.isClosed()) {
      final var spkLoc = speaker.getLocation();
      final var sLevel = this.platform.getServerLevel(spkLoc.world());
      if (sLevel != null) {
        final var pos = this.api.createPosition(spkLoc.x(), spkLoc.y(), spkLoc.z());
        final var channelUuid = UUID.nameUUIDFromBytes(streamKey.getBytes(StandardCharsets.UTF_8));
        ch = this.api.createLocationalAudioChannel(channelUuid, sLevel, pos);
        if (ch != null) {
          ch.setFilter(sp -> sp.getUuid().equals(listenerUuid));
          ch.setDistance(maxDist);
          if (this.volumeCategory != null)
            ch.setCategory(this.volumeCategory.getId());
          this.listenerChannels.put(streamKey, ch);
        }
      }
    }

    if (ch != null) {
      final var spkLoc = speaker.getLocation();
      ch.updateLocation(this.api.createPosition(spkLoc.x(), spkLoc.y(), spkLoc.z()));
      ch.send(audioToSend);
      this.lastChannelActivity.put(streamKey, now);
    }
  }

  @Override
  public void broadcastVoice(
    final @NotNull Collection<Speaker> speakers,
    final @NotNull UUID senderUuid,
    final byte[] rawOpus,
    final @Nullable String extraFilterId
  ) {
    if (speakers.isEmpty() || rawOpus == null || rawOpus.length == 0)
      return;

    final var common = DreamVoiceCommon.getInstance();
    final var wallService = common != null ? common.getService(VoiceWallService.class) : null;
    final var filterService = common != null ? common.getService(VoiceFilterService.class) : null;
    final var voiceService = common != null ? common.getService(VoiceService.class) : null;
    final var hasExtraFilter = extraFilterId != null && !extraFilterId.equalsIgnoreCase("none");
    final var hasPlayerFilters = filterService != null && filterService.hasActiveFilters(senderUuid);
    final var hasFilters = hasExtraFilter || hasPlayerFilters;
    final var isWallEnabled = wallService != null && wallService.isEnable();

    if (!isWallEnabled && !hasFilters) {
      for (final var speaker : speakers) {
        final var vc = speaker.getVoiceChannel();
        final var ch = Objects.requireNonNullElseGet(vc, speaker::getSpeakerChannel);
        ch.setFilter(speaker.getFilter());
        ch.send(rawOpus);
      }
      return;
    }

    final var now = System.currentTimeMillis();

    short[] basePcm = null;
    if (voiceService != null) {
      try {
        final var decoder = voiceService.getDecoder(senderUuid);
        basePcm = decoder.decode(rawOpus);
      } catch (Exception e) {
        this.platform.logWarning("Error decoding speaker mic packet: " + e.getMessage());
      }
    }

    if (basePcm == null || basePcm.length == 0)
      return;

    if (hasExtraFilter && filterService != null) {
      final var filter = filterService.getFilter(extraFilterId);
      if (filter != null)
        basePcm = filter.process(basePcm.clone(), null);
    }

    for (final var speaker : speakers) {
      final var spkLoc = speaker.getLocation();

      final var maxDist = (float) (speaker.getDistance() != null ? speaker.getDistance() : 16.0);

      for (final var listenerUuid : this.platform.getOnlinePlayers()) {
        final var listenerLoc = this.platform.getPlayerLocation(listenerUuid).orElse(null);
        if (listenerLoc == null || !listenerLoc.world().equals(spkLoc.world()))
          continue;

        final var dist = spkLoc.distance(listenerLoc);
        if (dist > maxDist)
          continue;

        processSingleListenerStream(speaker, senderUuid, listenerUuid, listenerLoc, rawOpus, basePcm, voiceService, wallService, filterService, hasPlayerFilters, isWallEnabled, maxDist, dist, now);
      }
    }
  }

  public void onMicrophone(final @NotNull MicrophonePacketEvent event) {
    final var sender = event.getSenderConnection();
    if (sender == null)
      return;

    final var senderUuid = sender.getPlayer().getUuid();
    final var matchingSpeakers = this.speakers.values().stream()
      .filter(s -> s.isSpeakerAllowed(senderUuid))
      .toList();

    if (matchingSpeakers.isEmpty())
      return;

    final var rawOpus = event.getPacket().getOpusEncodedData();
    if (rawOpus == null || rawOpus.length == 0)
      return;

    broadcastVoice(matchingSpeakers, senderUuid, rawOpus, null);
  }

  public void onPlayerDisconnect(final @NotNull UUID uuid) {
    final var uidStr = uuid.toString();
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
