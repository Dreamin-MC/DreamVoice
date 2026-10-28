package fr.dreamin.dreamvoice.common.recording.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.recording.event.VoiceRecordingStartEvent;
import fr.dreamin.dreamvoice.api.recording.event.VoiceRecordingStopEvent;
import fr.dreamin.dreamvoice.api.recording.model.AudioExportFormat;
import fr.dreamin.dreamvoice.api.recording.model.TimedAudioFrame;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.recording.player.OpusAudioPlayer;
import fr.dreamin.dreamvoice.common.recording.storage.VoiceRecordingPersistence;
import fr.dreamin.dreamvoice.common.utils.RawUtils;
import fr.dreamin.dreamvoice.common.utils.audio.AudioLimiter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform-independent implementation of {@link VoiceRecordingService} managing live voice recording capture,
 * Opus encoding/decoding, slice extractions, and disk persistence.
 */
public final class VoiceRecordingServiceImpl implements VoiceRecordingService {

  private static final String CATEGORY_ID = "rec_volume";
  private static final String CATEGORY_NAME = "Recording";
  private static final String CATEGORY_DESC = "Recording Volume";
  private static final int FRAME_SIZE_SAMPLES = 960;
  private static final long FRAME_DURATION_MS = 20L;

  private final @NotNull VoicePlatform platform;
  private @NotNull VoicechatServerApi api;
  private VolumeCategory volumeCategory;

  private final @NotNull Map<UUID, VoiceRecording> voiceRecordings = new ConcurrentHashMap<>();
  private final @NotNull Map<UUID, OpusEncoder> recordingEncoders = new ConcurrentHashMap<>();
  private boolean voiceServiceMissingLogged = false;

  public VoiceRecordingServiceImpl(final @NotNull VoicePlatform platform) {
    this.platform = platform;
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

    final var recordingsDir = getRecordingsDir();
    final var legacyDir = new File(this.platform.getDataDirectory().toFile(), "modules/recording");
    if (legacyDir.exists() && legacyDir.isDirectory()) {
      final var legacyFiles = legacyDir.listFiles((dir, name) -> name.endsWith(".dv"));
      if (legacyFiles != null) {
        for (final var file : legacyFiles) {
          final var dest = new File(recordingsDir, file.getName());
          if (!dest.exists()) {
            file.renameTo(dest);
          }
        }
      }
    }
    final var loaded = VoiceRecordingPersistence.loadAll(recordingsDir);
    for (final var rec : loaded)
      this.voiceRecordings.put(rec.getUuid(), rec);

    if (!loaded.isEmpty())
      this.platform.logInfo("Loaded " + loaded.size() + " voice recordings from disk.");
  }

  @Override
  public VoicechatServerApi getAPI() {
    return this.api;
  }

  @Override
  public Collection<VoiceRecording> getVoiceRecordings() {
    return this.voiceRecordings.values();
  }

  @Override
  public @Nullable VoiceRecording getVoiceRecording(final @NotNull UUID uuid) {
    return this.voiceRecordings.get(uuid);
  }

  @Override
  public void register(final @NotNull VoiceRecording voiceRecording) {
    this.voiceRecordings.put(voiceRecording.getUuid(), voiceRecording);
  }

  @Override
  public void unregister(final @NotNull VoiceRecording voiceRecording) {
    unregister(voiceRecording.getUuid());
  }

  @Override
  public void unregister(final @NotNull UUID uuid) {
    this.voiceRecordings.remove(uuid);
  }

  @Override
  public void unregisterAll() {
    getVoiceRecordings().forEach(this::unregister);
  }

  @Override
  public void playRecordingTo(final @NotNull VoicechatConnection connection, final @NotNull VoiceRecording recording) {
    playRecordingTo(List.of(connection), recording);
  }

  @Override
  public void playRecordingTo(final @NotNull Collection<VoicechatConnection> connections, final @NotNull VoiceRecording recording) {
    if (connections.isEmpty() || (!recording.isFinished() && !recording.isRecording()))
      return;

    final var frames = recording.getAudioFrames();
    if (frames.isEmpty()) {
      this.platform.logWarning("No audio frames to play in recording " + recording.getUuid());
      return;
    }

    final var channel = this.api.createStaticAudioChannel(UUID.randomUUID());
    if (channel == null) {
      this.platform.logError("Failed to create static audio channel for playback.", null);
      return;
    }

    for (final var connection : connections)
      channel.addTarget(connection);

    if (this.volumeCategory != null)
      channel.setCategory(this.volumeCategory.getId());

    final var totalDurationMs = (long) (recording.getDurationSeconds() * 1000L);
    final var player = new OpusAudioPlayer(List.of(channel), frames, totalDurationMs, false);
    player.startPlaying();
  }

  @Override
  public VoiceRecording startRecording(final @NotNull UUID speakerUUID) {
    final var event = new VoiceRecordingStartEvent(speakerUUID);
    event.callEvent();
    if (event.isCancelled())
      return null;
    final var rec = new VoiceRecording(speakerUUID);
    rec.start();
    register(rec);
    return rec;
  }

  @Override
  public void stopRecording(final @NotNull UUID recId) {
    final var rec = this.voiceRecordings.get(recId);
    if (rec != null && rec.isRecording()) {
      rec.stop();
      new VoiceRecordingStopEvent(rec).callEvent();
      final var recordingsDir = getRecordingsDir();
      this.platform.runAsync(() -> VoiceRecordingPersistence.save(rec, recordingsDir));
    }
  }

  @Override
  public CompletableFuture<VoiceRecording> createRecordingFromPcm(final short @NotNull [] pcm, final @NotNull UUID speakerUuid) {
    return CompletableFuture.supplyAsync(() -> {
      final var recording = new VoiceRecording(speakerUuid);
      recording.start();

      final var encoder = this.api.createEncoder();
      var offset = 0;
      var timestamp = 0L;

      while (offset < pcm.length) {
        final var length = Math.min(FRAME_SIZE_SAMPLES, pcm.length - offset);
        final var chunk = new short[FRAME_SIZE_SAMPLES];
        System.arraycopy(pcm, offset, chunk, 0, length);
        final var opus = encoder.encode(chunk);
        if (opus != null && opus.length > 0)
          recording.getAudioFrames().add(new TimedAudioFrame(timestamp, opus));
        offset += length;
        timestamp += FRAME_DURATION_MS;
      }

      recording.stop();
      register(recording);

      final var recordingsDir = getRecordingsDir();
      VoiceRecordingPersistence.save(recording, recordingsDir);
      return recording;
    });
  }

  @Override
  public CompletableFuture<VoiceRecording> createRecordingFromFile(final @NotNull File file, final @Nullable String name) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        final var pcm = RawUtils.fileToShorts48Hz(file);
        final var speakerUuid = UUID.randomUUID();
        return createRecordingFromPcm(pcm, speakerUuid).join();
      } catch (Exception e) {
        throw new CompletionException("Failed to load recording from file: " + file.getName(), e);
      }
    });
  }

  @Override
  public CompletableFuture<VoiceRecording> createRecordingFromFile(final @NotNull String fileName) {
    final var soundDir = new File(this.platform.getDataDirectory().toFile(), "sounds");
    return createRecordingFromFile(new File(soundDir, fileName), fileName);
  }

  @Override
  public CompletableFuture<VoiceRecording> createRecordingFromUrl(final @NotNull String url, final @Nullable String name) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        final var pcm = RawUtils.urlToShorts48Hz(url);
        final var speakerUuid = UUID.randomUUID();
        return createRecordingFromPcm(pcm, speakerUuid).join();
      } catch (Exception e) {
        throw new CompletionException("Failed to stream recording from URL: " + url, e);
      }
    });
  }

  @Override
  public @Nullable VoiceRecording sliceRecording(final @NotNull UUID recordingUuid, final @NotNull Instant timestamp, final @NotNull Duration duration) {
    final var recording = getVoiceRecording(recordingUuid);
    if (recording == null)
      return null;

    if (recording.getStartTime() == null)
      return null;

    final var startOffsetMs = timestamp.toEpochMilli() - recording.getStartTime().toEpochMilli();
    return sliceRecording(recordingUuid, startOffsetMs, duration.toMillis());
  }

  @Override
  public @Nullable VoiceRecording sliceRecording(final @NotNull UUID recordingUuid, final long startOffsetMs, final long durationMs) {
    final var recording = getVoiceRecording(recordingUuid);
    if (recording == null)
      return null;

    final var sliced = new VoiceRecording(recording.getSpeakerUUID());
    sliced.start();

    final var endOffsetMs = startOffsetMs + durationMs;
    for (final var frame : recording.getAudioFrames()) {
      if (frame.timestampMs() >= startOffsetMs && frame.timestampMs() <= endOffsetMs)
        sliced.getAudioFrames().add(new TimedAudioFrame(frame.timestampMs() - startOffsetMs, frame.data().clone(), frame.speakerUuid()));
    }

    sliced.stop();
    register(sliced);
    return sliced;
  }

  @Override
  public @Nullable VoiceRecording sliceLastRecording(final @NotNull UUID recordingUuid, final @NotNull Duration duration) {
    return sliceLastRecording(recordingUuid, duration.toMillis());
  }

  @Override
  public @Nullable VoiceRecording sliceLastRecording(final @NotNull UUID recordingUuid, final long durationMs) {
    final var recording = getVoiceRecording(recordingUuid);
    if (recording == null)
      return null;

    final var totalDurationMs = (long) (recording.getDurationSeconds() * 1000L);
    final var startOffsetMs = Math.max(0L, totalDurationMs - durationMs);
    return sliceRecording(recordingUuid, startOffsetMs, durationMs);
  }

  @Override
  public CompletableFuture<File> exportRecording(
    final @NotNull UUID recordingUuid,
    final @NotNull AudioExportFormat format,
    final @Nullable UUID speakerUuid,
    final @Nullable String fileName
  ) {
    final var recording = getVoiceRecording(recordingUuid);
    if (recording == null)
      return CompletableFuture.failedFuture(new IllegalArgumentException("Recording " + recordingUuid + " not found."));

    return exportRecording(recording, format, speakerUuid, fileName);
  }

  @Override
  public CompletableFuture<File> exportRecording(
    final @NotNull VoiceRecording recording,
    final @NotNull AudioExportFormat format,
    final @Nullable UUID speakerUuid,
    final @Nullable String fileName
  ) {
    return CompletableFuture.supplyAsync(() -> {
      final var pcm = decodeToPcm(recording, speakerUuid);
      if (pcm == null || pcm.length == 0)
        throw new CompletionException("No audio frames to export.", new IllegalStateException());

      final var exportsDir = getExportsDir();
      final String baseName;
      if (fileName != null && !fileName.isBlank()) {
        baseName = fileName;
      } else if (speakerUuid != null) {
        baseName = recording.getUuid().toString().substring(0, 8) + "_" + speakerUuid.toString().substring(0, 8);
      } else {
        baseName = recording.getUuid().toString();
      }

      final var targetFile = new File(exportsDir, baseName + "." + format.getExtension());

      try {
        RawUtils.pcmToAudioFile(pcm, targetFile, format.getExtension());
        return targetFile;
      } catch (Exception e) {
        throw new CompletionException("Audio export failed: " + e.getMessage(), e);
      }
    });
  }

  @Override
  public short @Nullable [] decodeToPcm(final @NotNull VoiceRecording recording, final @Nullable UUID speakerUuid) {
    final List<TimedAudioFrame> frames;
    if (speakerUuid != null) {
      frames = recording.getFramesOfSpeaker(speakerUuid);
    } else {
      frames = recording.getAudioFrames();
    }

    if (frames.isEmpty())
      return null;

    final var decoder = this.api.createDecoder();

    long maxSampleEnd = 0L;
    for (final var frame : frames) {
      final long end = (frame.timestampMs() * 48L) + FRAME_SIZE_SAMPLES;
      if (end > maxSampleEnd)
        maxSampleEnd = end;
    }

    final long durationSamples = (long) (recording.getDurationSeconds() * 48000.0F);
    final int totalSamples = (int) Math.max(maxSampleEnd, durationSamples);
    if (totalSamples <= 0) {
      try {
        if (!decoder.isClosed()) decoder.close();
      } catch (Throwable ignored) {}
      return null;
    }

    final int[] mixBuffer = new int[totalSamples];

    for (final var frame : frames) {
      final var decoded = decoder.decode(frame.data());
      if (decoded != null && decoded.length > 0) {
        final int startSample = (int) (frame.timestampMs() * 48L);
        for (int i = 0; i < decoded.length; i++) {
          final int idx = startSample + i;
          if (idx < mixBuffer.length)
            mixBuffer[idx] += decoded[i];
        }
      }
    }

    try {
      if (!decoder.isClosed())
        decoder.close();
    } catch (Throwable ignored) {}

    final short[] pcmSamples = new short[totalSamples];
    for (int i = 0; i < totalSamples; i++) {
      pcmSamples[i] = (short) Math.clamp(mixBuffer[i], Short.MIN_VALUE, Short.MAX_VALUE);
    }

    return AudioLimiter.process(pcmSamples);
  }

  public void onMicrophone(final @NotNull MicrophonePacketEvent event) {
    final var sender = event.getSenderConnection();
    if (sender == null)
      return;

    final var speakerUUID = sender.getPlayer().getUuid();
    final var activeRecordings = getVoiceRecordings().stream()
      .filter(rec -> rec.getSpeakerUUID().equals(speakerUUID) && rec.isRecording())
      .toList();

    if (activeRecordings.isEmpty())
      return;

    var opusData = event.getPacket().getOpusEncodedData();
    final var common = DreamVoiceCommon.getInstance();
    final var filterService = common != null ? common.getService(VoiceFilterService.class) : null;

    if (filterService != null && filterService.hasActiveFilters(speakerUUID)) {
      final var voiceService = common.getService(VoiceService.class);
      if (voiceService == null) {
        if (!this.voiceServiceMissingLogged) {
          this.voiceServiceMissingLogged = true;
          this.platform.logWarning("VoiceService is unavailable. Recording filters are skipped.");
        }
      } else try {
        final var decoder = voiceService.getDecoder(speakerUUID);
        final var encoder = this.recordingEncoders.computeIfAbsent(speakerUUID, _ -> this.api.createEncoder());
        final var pcm = decoder.decode(opusData);
        if (pcm != null && pcm.length > 0) {
          var filteredPcm = filterService.applyFilters(speakerUUID, pcm);
          filteredPcm = AudioLimiter.process(filteredPcm);
          opusData = encoder.encode(filteredPcm);
        }
      } catch (Exception e) {
        this.platform.logWarning("Error filtering recording audio: " + e.getMessage());
      }
    }

    final var finalOpusData = opusData;
    activeRecordings.forEach(rec -> rec.addAudio(speakerUUID, finalOpusData));
  }

  public void onPlayerDisconnect(final @NotNull UUID uuid) {
    final var enc = this.recordingEncoders.remove(uuid);
    if (enc != null && !enc.isClosed()) {
      try {
        enc.close();
      } catch (Throwable ignored) {}
    }
  }

  private @NotNull File getRecordingsDir() {
    final var dir = new File(this.platform.getDataDirectory().toFile(), "recordings");
    if (!dir.exists())
      dir.mkdirs();

    return dir;
  }

  private @NotNull File getExportsDir() {
    final var dir = new File(this.platform.getDataDirectory().toFile(), "exports");
    if (!dir.exists())
      dir.mkdirs();

    return dir;
  }

}
