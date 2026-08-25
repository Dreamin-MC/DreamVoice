package fr.dreamin.dreamvoice.core.speaker.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class VoiceSpeakerServiceImpl implements VoiceSpeakerService, Listener {

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;

  private VolumeCategory volumeCategory;

  private final @NotNull Map<UUID, Speaker> speakers = new ConcurrentHashMap<>();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceSpeakerServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;

    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public VoicechatServerApi getAPI() {
    return this.api;
  }

  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;

    this.volumeCategory = this.api.volumeCategoryBuilder()
      .setId("speaker_volume")
      .setName("Speaker")
      .setDescription("Speaker Volume")
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
  public void playSound(final @NotNull Speaker speaker, final @NotNull short[] pcm) {
    playSound(speaker, pcm, false);
  }

  @Override
  public void playSound(final @NotNull Speaker speaker, final @NotNull short[] pcm, final boolean loop) {
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
        final var soundDir = new java.io.File(this.plugin.getDataFolder(), "sounds");
        if (!soundDir.exists())
          soundDir.mkdirs();

        final var soundFile = new java.io.File(soundDir, fileName);
        if (!soundFile.exists()) {
          this.plugin.getLogger().warning("Sound file not found: " + soundFile.getAbsolutePath());
          return;
        }

        final var pcm = fr.dreamin.dreamvoice.core.utils.RawUtils.fileToShorts48Hz(soundFile);
        if (pcm.length > 0) {
          Bukkit.getScheduler().runTask(this.plugin, () -> playSound(speaker, pcm, loop));
        }
      } catch (Exception e) {
        this.plugin.getLogger().severe("Error loading sound file: " + e.getMessage());
      }
    });
  }

  @Override
  public void playSoundUrl(final @NotNull Speaker speaker, final @NotNull String url, final boolean loop) {
    CompletableFuture.runAsync(() -> {
      try {
        final var pcm = fr.dreamin.dreamvoice.core.utils.RawUtils.urlToShorts48Hz(url);
        if (pcm.length > 0) {
          Bukkit.getScheduler().runTask(this.plugin, () -> playSound(speaker, pcm, loop));
        }
      } catch (Exception e) {
        this.plugin.getLogger().severe("Error streaming sound from URL: " + e.getMessage());
      }
    });
  }

  @Override
  public void stopSound(final @NotNull Speaker speaker) {
    speaker.stopPlaying();
  }


  // ###############################################################
  // ---------------------- LISTENER METHODS -----------------------
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

    final var filterService = DreamVoice.getService(VoiceFilterService.class);

    if (filterService != null && filterService.hasActiveFilters(senderUuid)) {
      try {
        final var voiceService = DreamVoice.getService(VoiceService.class);
        final var decoder = voiceService.getDecoder(senderUuid);
        final var encoder = voiceService.getEncoder(senderUuid);
        final var pcm = decoder.decode(event.getPacket().getOpusEncodedData());
        if (pcm != null && pcm.length > 0) {
          final var filteredPcm = filterService.applyFilters(senderUuid, pcm);
          final var newOpus = encoder.encode(filteredPcm);
          matchingSpeakers.forEach(speaker -> speaker.getSpeakerChannel().send(newOpus));
          return;
        }
      } catch (Exception e) {
        this.plugin.getLogger().warning("Error filtering speaker audio: " + e.getMessage());
      }
    }

    matchingSpeakers.forEach(speaker -> speaker.getSpeakerChannel().send(event.getPacket()));
  }

}




