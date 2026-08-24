package fr.dreamin.dreamvoice.core.recording.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.core.DreamVoice;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VoiceRecordingServiceImpl implements VoiceRecordingService, Listener {

  private static final int CHUNK_MS = 20, BYTES_PER_MS = 6, CHUNK_SIZE = CHUNK_MS * BYTES_PER_MS;

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;

  private VolumeCategory volumeCategory;

  private final @NotNull Map<UUID, VoiceRecording> voiceRecordings = new HashMap<>();

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
      .setId("recording_volume")
      .setName("Recording")
      .setDescription("Recording Volume")
      .build();

    this.api.registerVolumeCategory(this.volumeCategory);
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
    if (!recording.isFinished())
      return;

    final var speakerUUID = recording.getSpeakerUUID();
    final var bukkitPlayer = Bukkit.getPlayer(speakerUUID);
    if (bukkitPlayer == null) {
      this.plugin.getLogger().warning("Speaker offline, cannot determine world for playback.");
      return;
    }

    final var channelId = UUID.randomUUID();
    final var level = this.api.fromServerLevel(bukkitPlayer.getWorld());
    final var channel = this.api.createStaticAudioChannel(channelId, level, connection);

    if (channel == null) {
      this.plugin.getLogger().severe("Failed to create static audio channel.");
      return;
    }
    channel.setCategory(this.volumeCategory.getId());

    final var opusFrames = recording.getAudio();
    if (opusFrames.isEmpty())
      return;

    final var decoder = this.api.createDecoder();
    final var pcmFrames = new ArrayList<short[]>();
    var totalSamples = 0;

    try {
      for (final var frame : opusFrames) {
        if (frame.length == 0)
          continue;
        final var pcm = decoder.decode(frame);
        if (pcm.length > 0) {
          pcmFrames.add(pcm);
          totalSamples += pcm.length;
        }
      }
    } catch (Exception e) {
      this.plugin.getLogger().severe("Error decoding Opus frame: " + e.getMessage());
      return;
    }

    if (totalSamples == 0) {
      this.plugin.getLogger().warning("Decoded audio is empty.");
      return;
    }

    final var fullPcm = new short[totalSamples];
    var offset = 0;
    for (final var frame : pcmFrames) {
      System.arraycopy(frame, 0, fullPcm, offset, frame.length);
      offset += frame.length;
    }

    try {
      final var encoder = this.api.createEncoder();
      final var player = this.api.createAudioPlayer(channel, encoder, fullPcm);

      player.startPlaying();
    } catch (Exception e) {
      this.plugin.getLogger().severe("Error creating audio player: " + e.getMessage());
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
    if (rec != null && rec.isRecording())
      rec.stop();
  }

  // ###############################################################
  // ---------------------- LISTENER METHODS -----------------------
  // ###############################################################

  @EventHandler
  private void onMicrophone(final @NotNull MicrophonePacketEvent event) {
    final var sender = event.getSender();
    if (sender == null)
      return;

    final var speaderUUID = sender.getPlayer().getUuid();

    getVoiceRecordings().stream()
      .filter(rec -> rec.getSpeakerUUID().equals(speaderUUID) && rec.isRecording())
      .forEach(rec -> rec.addAudio(event.getPacket().getOpusEncodedData()));
  }

}


