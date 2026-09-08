package fr.dreamin.dreamvoice.core.recording.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.recording.model.AudioExportFormat;
import fr.dreamin.dreamvoice.api.recording.model.TimedAudioFrame;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.recording.event.CassetteCreateEvent;
import fr.dreamin.dreamvoice.api.recording.event.CassettePlayEvent;
import fr.dreamin.dreamvoice.api.recording.event.VoiceRecordingStartEvent;
import fr.dreamin.dreamvoice.api.recording.event.VoiceRecordingStopEvent;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.core.recording.item.CassetteItem;
import fr.dreamin.dreamvoice.core.recording.player.OpusAudioPlayer;
import fr.dreamin.dreamvoice.core.recording.storage.VoiceRecordingPersistence;
import fr.dreamin.dreamvoice.core.utils.RawUtils;
import fr.dreamin.dreamvoice.core.utils.audio.AudioLimiter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link VoiceRecordingService} managing live voice recording capture,
 * Opus encoding/decoding, slice extractions, cassette binding, and disk persistence.
 */
public final class VoiceRecordingServiceImpl implements VoiceRecordingService, Listener {

  // ###############################################################
  // ----------------------- STATIC FIELDS -------------------------
  // ###############################################################

  private static final String CATEGORY_ID = "rec_volume";
  private static final String CATEGORY_NAME = "Recording";
  private static final String CATEGORY_DESC = "Recording Volume";
  private static final int FRAME_SIZE_SAMPLES = 960; // 20ms at 48kHz mono
  private static final long FRAME_DURATION_MS = 20L;

  // ###############################################################
  // --------------------- INSTANCE FIELDS -------------------------
  // ###############################################################

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;
  private VolumeCategory volumeCategory;

  private final @NotNull Map<UUID, VoiceRecording> voiceRecordings = new ConcurrentHashMap<>();
  private final @NotNull Map<UUID, OpusEncoder> recordingEncoders = new ConcurrentHashMap<>();
  private boolean voiceServiceMissingLogged = false;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceRecordingServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  // ###############################################################
  // ------------------- PUBLIC SERVICE METHODS --------------------
  // ###############################################################

  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;

    this.volumeCategory = this.api.volumeCategoryBuilder()
      .setId(CATEGORY_ID)
      .setName(CATEGORY_NAME)
      .setDescription(CATEGORY_DESC)
      .build();

    this.api.registerVolumeCategory(this.volumeCategory);

    final var recordingsDir = new File(this.plugin.getDataFolder(), "recordings");
    final var loaded = VoiceRecordingPersistence.loadAll(recordingsDir);
    for (final var rec : loaded)
      this.voiceRecordings.put(rec.getUuid(), rec);

    if (!loaded.isEmpty())
      this.plugin.getLogger().info("Loaded " + loaded.size() + " voice recordings from disk.");
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
      this.plugin.getLogger().warning("No audio frames to play in recording " + recording.getUuid());
      return;
    }

    final var channel = this.api.createStaticAudioChannel(UUID.randomUUID());
    if (channel == null) {
      this.plugin.getLogger().severe("Failed to create static audio channel for playback.");
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
  public VoiceRecording startRecording(final @NonNull UUID speakerUUID) {
    final var event = new VoiceRecordingStartEvent(speakerUUID);
    if (!event.callEvent())
      return null;
    final var rec = new VoiceRecording(speakerUUID);
    rec.start();
    register(rec);
    return rec;
  }

  @Override
  public void stopRecording(final @NonNull UUID recId) {
    final var rec = this.voiceRecordings.get(recId);
    if (rec != null && rec.isRecording()) {
      rec.stop();
      new VoiceRecordingStopEvent(rec).callEvent();
      final var recordingsDir = new File(this.plugin.getDataFolder(), "recordings");
      CompletableFuture.runAsync(() -> VoiceRecordingPersistence.save(rec, recordingsDir));
    }
  }

  @Override
  public @NonNull ItemStack linkItem(final @NotNull ItemStack item, final @NotNull VoiceRecording recording) {
    return CassetteItem.linkItem(item, recording);
  }

  @Override
  public @NonNull ItemStack linkItem(final @NotNull ItemStack item, final @NotNull UUID recordingUuid) {
    return CassetteItem.linkItem(item, recordingUuid);
  }

  @Override
  public @NonNull ItemStack createCassette(final @NotNull VoiceRecording recording) {
    final var baseItem = CassetteItem.create(recording);
    final var event = new CassetteCreateEvent(recording, baseItem);
    return event.callEvent() ? event.getItemStack() : baseItem;
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

      final var recordingsDir = new File(this.plugin.getDataFolder(), "recordings");
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
        throw new CompletionException(e);
      }
    });
  }

  @Override
  public CompletableFuture<VoiceRecording> createRecordingFromFile(final @NotNull String fileName) {
    final var soundDir = new File(this.plugin.getDataFolder(), "sounds");
    final var file = new File(soundDir, fileName);
    return createRecordingFromFile(file, fileName);
  }

  @Override
  public CompletableFuture<VoiceRecording> createRecordingFromUrl(final @NotNull String url, final @Nullable String name) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        final var pcm = RawUtils.urlToShorts48Hz(url);
        final var speakerUuid = UUID.randomUUID();
        return createRecordingFromPcm(pcm, speakerUuid).join();
      } catch (Exception e) {
        throw new CompletionException(e);
      }
    });
  }

  @Override
  public @Nullable VoiceRecording sliceRecording(final @NotNull UUID recordingUuid, final @NotNull Instant timestamp, final @NotNull Duration duration) {
    final var rec = this.voiceRecordings.get(recordingUuid);
    if (rec == null)
      return null;

    final var sliced = rec.slice(timestamp, duration);
    register(sliced);
    final var recordingsDir = new File(this.plugin.getDataFolder(), "recordings");
    CompletableFuture.runAsync(() -> VoiceRecordingPersistence.save(sliced, recordingsDir));
    return sliced;
  }

  @Override
  public @Nullable VoiceRecording sliceRecording(final @NotNull UUID recordingUuid, final long startOffsetMs, final long durationMs) {
    final var rec = this.voiceRecordings.get(recordingUuid);
    if (rec == null)
      return null;

    final var sliced = rec.slice(startOffsetMs, durationMs);
    register(sliced);
    final var recordingsDir = new File(this.plugin.getDataFolder(), "recordings");
    CompletableFuture.runAsync(() -> VoiceRecordingPersistence.save(sliced, recordingsDir));
    return sliced;
  }

  @Override
  public @Nullable VoiceRecording sliceLastRecording(final @NotNull UUID recordingUuid, final @NotNull Duration duration) {
    return sliceLastRecording(recordingUuid, duration.toMillis());
  }

  @Override
  public @Nullable VoiceRecording sliceLastRecording(final @NotNull UUID recordingUuid, final long durationMs) {
    final var rec = this.voiceRecordings.get(recordingUuid);
    if (rec == null)
      return null;

    final var sliced = rec.sliceLast(durationMs);
    register(sliced);
    final var recordingsDir = new File(this.plugin.getDataFolder(), "recordings");
    CompletableFuture.runAsync(() -> VoiceRecordingPersistence.save(sliced, recordingsDir));
    return sliced;
  }

  @Override
  public CompletableFuture<File> exportRecording(final @NotNull UUID recordingUuid, final @NotNull AudioExportFormat format) {
    return exportRecording(recordingUuid, format, null);
  }

  @Override
  public CompletableFuture<File> exportRecording(final @NotNull UUID recordingUuid, final @NotNull AudioExportFormat format, final @Nullable String fileName) {
    final var rec = this.voiceRecordings.get(recordingUuid);
    if (rec == null)
      return CompletableFuture.failedFuture(new IllegalArgumentException("Recording not found: " + recordingUuid));

    return exportRecording(rec, format, fileName);
  }

  @Override
  public CompletableFuture<File> exportRecording(final @NotNull VoiceRecording recording, final @NotNull AudioExportFormat format) {
    return exportRecording(recording, format, null);
  }

  @Override
  public CompletableFuture<File> exportRecording(final @NotNull VoiceRecording recording, final @NotNull AudioExportFormat format, final @Nullable String fileName) {
    return CompletableFuture.supplyAsync(() -> {
      final var frames = recording.getAudioFrames();
      if (frames.isEmpty())
        throw new IllegalStateException("No audio data to export for recording: " + recording.getUuid());

      final var pcm = decodeRecordingFrames(frames);
      if (pcm == null || pcm.length == 0)
        throw new IllegalStateException("Failed to decode audio frames for recording: " + recording.getUuid());

      final var exportDir = new File(this.plugin.getDataFolder(), "exports");
      if (!exportDir.exists())
        exportDir.mkdirs();

      final var baseName = (fileName != null && !fileName.isBlank())
        ? fileName.trim().replaceAll("[^a-zA-Z0-9._-]", "_")
        : recording.getUuid().toString();

      final var targetFile = new File(exportDir, baseName + "." + format.getExtension());
      try {
        RawUtils.pcmToAudioFile(pcm, targetFile, format.getExtension());
        return targetFile;
      } catch (Exception e) {
        throw new CompletionException("Failed to export recording to " + format.name() + ": " + e.getMessage(), e);
      }
    });
  }

  // ###############################################################
  // ------------------- PRIVATE HELPER METHODS --------------------
  // ###############################################################

  private short[] decodeRecordingFrames(final @NotNull List<TimedAudioFrame> frames) {
    final var pcmList = new ArrayList<short[]>();
    var totalSamples = 0;
    var currentStreamTimeMs = 0L;

    final var decoder = this.api.createDecoder();
    try {
      for (final var frame : frames) {
        final var data = frame.data();
        if (data == null || data.length == 0)
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

        final var pcm = decoder.decode(data);
        if (pcm != null && pcm.length > 0) {
          pcmList.add(pcm);
          totalSamples += pcm.length;
          currentStreamTimeMs += (pcm.length / 48);
        }
      }
    } catch (Exception e) {
      this.plugin.getLogger().severe("Error decoding Opus frames for export: " + e.getMessage());
      return null;
    } finally {
      if (!decoder.isClosed()) {
        try {
          decoder.close();
        } catch (Throwable ignored) {}
      }
    }

    if (totalSamples == 0)
      return null;

    final var fullPcm = new short[totalSamples];
    var offset = 0;
    for (final var chunk : pcmList) {
      System.arraycopy(chunk, 0, fullPcm, offset, chunk.length);
      offset += chunk.length;
    }

    return fullPcm;
  }


  // ###############################################################
  // ---------------------- EVENT LISTENERS ------------------------
  // ###############################################################

  @EventHandler
  private void onPlayerInteract(final @NotNull PlayerInteractEvent event) {
    if (!event.getAction().name().contains("RIGHT_CLICK"))
      return;

    final var item = event.getItem();
    final var recUuid = CassetteItem.getRecordingUuid(item);
    if (recUuid == null)
      return;

    event.setCancelled(true);
    final var player = event.getPlayer();
    final var recording = this.voiceRecordings.get(recUuid);
    if (recording != null) {
      final var playEvent = new CassettePlayEvent(player, recording, item);
      if (!playEvent.callEvent())
        return;
    }
    if (recording == null) {
      player.sendMessage(Component.text("[SVC] Recording not found on the server!", NamedTextColor.RED));
      return;
    }

    final var conn = this.api.getConnectionOf(player.getUniqueId());
    if (conn == null) {
      player.sendMessage(Component.text("[SVC] You are not connected to voice chat!", NamedTextColor.RED));
      return;
    }

    player.sendMessage(
      Component.text("Playing voice cassette (", NamedTextColor.GREEN)
        .append(Component.text(String.format("%.1f", recording.getDurationSeconds()) + "s", NamedTextColor.YELLOW))
        .append(Component.text(")...", NamedTextColor.GREEN))
    );

    playRecordingTo(conn, recording);
  }

  @EventHandler
  private void onMicrophone(final @NotNull MicrophonePacketEvent event) {
    final var sender = event.getSender();
    if (sender == null)
      return;

    final var speakerUUID = sender.getPlayer().getUuid();
    final var activeRecordings = getVoiceRecordings().stream()
      .filter(rec -> rec.getSpeakerUUID().equals(speakerUUID) && rec.isRecording())
      .toList();

    if (activeRecordings.isEmpty())
      return;

    var opusData = event.getPacket().getOpusEncodedData();
    final var filterService = DreamVoice.getService(VoiceFilterService.class);

    if (filterService != null && filterService.hasActiveFilters(speakerUUID)) {
      final var voiceService = DreamVoice.getService(VoiceService.class);
      if (voiceService == null) {
        if (!this.voiceServiceMissingLogged) {
          this.voiceServiceMissingLogged = true;
          this.plugin.getLogger().warning("VoiceService is unavailable. Recording filters are skipped.");
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
        this.plugin.getLogger().warning("Error filtering recording audio: " + e.getMessage());
      }
    }

    final var finalOpusData = opusData;
    activeRecordings.forEach(rec -> rec.addAudio(finalOpusData));
  }

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    final var enc = this.recordingEncoders.remove(event.getPlayer().getUniqueId());
    if (enc != null && !enc.isClosed()) {
      try {
        enc.close();
      } catch (Throwable ignored) {}
    }
  }

}
