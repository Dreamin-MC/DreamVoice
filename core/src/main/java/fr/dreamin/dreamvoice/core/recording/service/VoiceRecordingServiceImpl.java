package fr.dreamin.dreamvoice.core.recording.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.recording.model.TimedAudioFrame;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.recording.item.CassetteItem;
import fr.dreamin.dreamvoice.core.recording.storage.VoiceRecordingPersistence;
import fr.dreamin.dreamvoice.core.utils.RawUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class VoiceRecordingServiceImpl implements VoiceRecordingService, Listener {

  private static final int CHUNK_MS = 20, BYTES_PER_MS = 6, CHUNK_SIZE = CHUNK_MS * BYTES_PER_MS;

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;

  private VolumeCategory volumeCategory;

  private final @NotNull Map<UUID, VoiceRecording> voiceRecordings = new HashMap<>();
  private boolean voiceServiceMissingLogged = false;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceRecordingServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;

    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;

    this.volumeCategory = this.api.volumeCategoryBuilder()
      .setId("rec_volume")
      .setName("Recording")
      .setDescription("Recording Volume")
      .build();


    this.api.registerVolumeCategory(this.volumeCategory);

    // Load saved recordings from disk
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
    if (!recording.isFinished() && !recording.isRecording())
      return;

    final var frames = recording.getAudioFrames();
    if (frames.isEmpty()) {
      this.plugin.getLogger().warning("No audio frames in recording " + recording.getUuid());
      return;
    }

    final var channelId = UUID.randomUUID();
    final var channel = this.api.createStaticAudioChannel(channelId);
    if (channel == null) {
      this.plugin.getLogger().severe("Failed to create static audio channel for playback.");
      return;
    }

    channel.addTarget(connection);
    if (this.volumeCategory != null)
      channel.setCategory(this.volumeCategory.getId());

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
      this.plugin.getLogger().severe("Error decoding Opus frames: " + e.getMessage());
      return;
    }

    if (totalSamples == 0) {
      this.plugin.getLogger().warning("Recording decoded to 0 samples: " + recording.getUuid());
      return;
    }

    final var fullPcm = new short[totalSamples];
    var offset = 0;
    for (final var chunk : pcmList) {
      System.arraycopy(chunk, 0, fullPcm, offset, chunk.length);
      offset += chunk.length;
    }

    try {
      final var encoder = this.api.createEncoder();
      final var player = this.api.createAudioPlayer(channel, encoder, fullPcm);
      player.startPlaying();
    } catch (Exception e) {
      this.plugin.getLogger().severe("Error creating AudioPlayer: " + e.getMessage());
    }
  }



  @Override
  public VoiceRecording startRecording(final @NonNull UUID speakerUUID) {
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
      final var recordingsDir = new File(this.plugin.getDataFolder(), "recordings");
      VoiceRecordingPersistence.save(rec, recordingsDir);
    }
  }

  // ###############################################################
  // ---------------------- LISTENER METHODS -----------------------
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
    if (recording == null) {
      player.sendMessage(Component.text("[SVC] Enregistrement introuvable sur le serveur !", NamedTextColor.RED));
      return;
    }

    final var conn = this.api.getConnectionOf(player.getUniqueId());
    if (conn == null) {
      player.sendMessage(Component.text("[SVC] Vous n'êtes pas connecté au chat vocal !", NamedTextColor.RED));
      return;
    }

    player.sendMessage(
      Component.text("▶ Lecture de la cassette vocale (", NamedTextColor.GREEN)
        .append(Component.text(String.format("%.1f", recording.getDurationSeconds()) + "s", NamedTextColor.YELLOW))
        .append(Component.text(")...", NamedTextColor.GREEN))
    );

    playRecordingTo(conn, recording);
  }

  @Override
  public ItemStack linkItem(final @NotNull ItemStack item, final @NotNull VoiceRecording recording) {
    return CassetteItem.linkItem(item, recording);
  }

  @Override
  public ItemStack linkItem(final @NotNull ItemStack item, final @NotNull UUID recordingUuid) {
    return CassetteItem.linkItem(item, recordingUuid);
  }

  @Override
  public ItemStack createCassette(final @NotNull VoiceRecording recording) {
    return CassetteItem.create(recording);
  }

  @Override
  public CompletableFuture<VoiceRecording> createRecordingFromPcm(final @NotNull short[] pcm, final @NotNull UUID speakerUuid) {
    return CompletableFuture.supplyAsync(() -> {
      final var recording = new VoiceRecording(speakerUuid);
      recording.start();

      final var encoder = this.api.createEncoder();
      final var frameSize = 960; // 20ms at 48kHz mono
      var offset = 0;
      var timestamp = 0L;

      while (offset < pcm.length) {
        final var length = Math.min(frameSize, pcm.length - offset);
        final var chunk = new short[frameSize];
        System.arraycopy(pcm, offset, chunk, 0, length);
        final var opus = encoder.encode(chunk);
        if (opus != null && opus.length > 0) {
          recording.getAudioFrames().add(new TimedAudioFrame(timestamp, opus));
        }
        offset += length;
        timestamp += 20L;
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
    VoiceRecordingPersistence.save(sliced, recordingsDir);
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
    VoiceRecordingPersistence.save(sliced, recordingsDir);
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
    VoiceRecordingPersistence.save(sliced, recordingsDir);
    return sliced;
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
        final var encoder = voiceService.getEncoder(speakerUUID);
        final var pcm = decoder.decode(opusData);
        if (pcm != null && pcm.length > 0) {
          final var filteredPcm = filterService.applyFilters(speakerUUID, pcm);
          opusData = encoder.encode(filteredPcm);
        }
      } catch (Exception e) {
        this.plugin.getLogger().warning("Error filtering recording audio: " + e.getMessage());
      }
    }

    final var finalOpusData = opusData;
    activeRecordings.forEach(rec -> rec.addAudio(finalOpusData));
  }

}




