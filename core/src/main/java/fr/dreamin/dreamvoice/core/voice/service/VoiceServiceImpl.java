package fr.dreamin.dreamvoice.core.voice.service;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreaminvoice.api.codex.service.CodexService;
import fr.dreamin.dreaminvoice.api.player.model.PlayerState;
import fr.dreamin.dreaminvoice.api.player.service.PlayerService;
import fr.dreamin.dreaminvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreaminvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreaminvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreaminvoice.api.voice.model.VoiceSoundBuilder;
import fr.dreamin.dreaminvoice.api.voice.service.VoiceService;
import fr.dreamin.dreaminvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@RequiredArgsConstructor
public final class VoiceServiceImpl implements VoiceService, VoicechatPlugin {

  private static final int MAX_POOL_SIZE = 1024;

  private final @NotNull DreamVoice plugin;
  private VoicechatServerApi api;

  private boolean debug = true;

  private final @NotNull CodexService codexService;
  private final @NotNull PlayerService playerService;
  private final @NotNull VoiceWallService voiceWallService;

  // Pool + cache players actifs
  private final Queue<UUID> channelIdPool = new ConcurrentLinkedQueue<>();
  private final Map<UUID, AudioPlayer> activePlayers = new ConcurrentHashMap<>();

  private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
  private final Map<UUID, OpusEncoder> encoders = new ConcurrentHashMap<>();

  // ###############################################################
  // ------------------------ SVC METHODS --------------------------
  // ###############################################################

  @Override
  public String getPluginId() {
    return "DreamVoice";
  }

  @Override
  public void registerEvents(EventRegistration registration) {
    registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
    registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    registration.registerEvent(EntitySoundPacketEvent.class, this::onEntitySoundPacket);
  }

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // #############################################################

  @Override
  public boolean isDebug() {
    return this.debug;
  }

  @Override
  public void setDebug(boolean value) {
    this.debug = value;
  }

  @Override
  public VoicechatServerApi getAPI() {
    return this.api;
  }

  @Override
  public void playSound(final @NotNull VoiceSoundBuilder builder) {
    if (this.api == null) return;

    final var raw = builder.getRawAudioData();
    if (raw == null || raw.length == 0) return;

    final var samples = audioToShorts(raw);
    if (samples.length == 0) return;

    if (builder.getLocation() == null)
      playStatic(builder, samples);
    else
      playLocational(builder, samples);
  }

  public int getActiveSoundCount() {
    return this.activePlayers.size();
  }

  public Set<UUID> getActiveSoundIds() {
    return Set.copyOf(this.activePlayers.keySet());
  }

  public boolean stopSound(@NotNull UUID channelId) {
    final var p = this.activePlayers.get(channelId);
    if (p == null) return false;
    try {
      p.stopPlaying();
    } catch (Throwable t) {
      // fallback hard cleanup
      this.activePlayers.remove(channelId);
      releaseChannelId(channelId);
      this.plugin.getLogger().warning("stopSound failed for " + channelId + ": " + t.getMessage());
    }
    return true;
  }

  public void clearAllSounds() {
    final var snapshot = new ArrayList<>(this.activePlayers.entrySet());
    for (final var e : snapshot) {
      final var id = e.getKey();
      final var p = e.getValue();
      try {
        p.stopPlaying();
      } catch (Throwable t) {
        this.activePlayers.remove(id);
        releaseChannelId(id);
        this.plugin.getLogger().warning("clearAllSounds: stop failed for " + id + ": " + t.getMessage());
      }
    }
  }

  @Override
  public boolean isPlayerConnected(@NotNull UUID uuid) {
    return this.api.getConnectionOf(uuid) != null;
  }

  @Override
  public OpusDecoder getDecoder(@NotNull UUID uuid) {
    return this.decoders.computeIfAbsent(uuid, id -> this.api.createDecoder());
  }

  @Override
  public OpusEncoder getEncoder(@NotNull UUID uuid) {
    return this.encoders.computeIfAbsent(uuid, id -> this.api.createEncoder());
  }

  // ###############################################################
  // ----------------------- PRIVATE METHODS -----------------------
  // ###############################################################

  private boolean canHear(PlayerState speaker, PlayerState listener) {
    return switch (listener) {
      case ALIVE, SPECTATE ->
        speaker == PlayerState.ALIVE;
      case DEAD ->
        speaker == PlayerState.ALIVE
          || speaker == PlayerState.DEAD;
    };
  }

  private boolean hasValidConnections(VoicechatConnection sender, VoicechatConnection receiver) {
    return sender != null && receiver != null;
  }
  // ###############################################################
  // --------------------- PLAY SOUND METHODS ----------------------
  // ###############################################################

  private UUID acquireChannelId() {
    final var id = this.channelIdPool.poll();
    return id != null ? id : UUID.randomUUID();
  }

  private void releaseChannelId(@NotNull UUID id) {
    if (this.channelIdPool.size() < MAX_POOL_SIZE) this.channelIdPool.offer(id);
  }

  private void track(final @NotNull UUID channelId, final @NotNull AudioPlayer player, final @NotNull VoiceSoundBuilder builder, short[] samples) {
    this.activePlayers.put(channelId, player);
    player.setOnStopped(() -> {
      this.activePlayers.remove(channelId);
      releaseChannelId(channelId);

      if (builder.getOnStopped() != null) {
        try {
          builder.getOnStopped().run();
        } catch (Throwable t) {
          this.plugin.getLogger().warning("onStopped callback failed: " + t.getMessage());
        }
      }

      if (builder.isLoop()) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
          // on relance le son avec un nouveau channelId
          if (builder.getLocation() == null) {
            playStatic(builder, samples);
          } else {
            playLocational(builder, samples);
          }
        });
      }

    });
  }

  private short[] audioToShorts(byte[] rawData) {
    if (rawData.length % 2 != 0 || rawData.length == 0) {
      this.plugin.getLogger().warning("Audio data invalide: " + rawData.length + " bytes");
      return new short[0];
    }

    final var buffer = ByteBuffer.allocate(rawData.length).order(ByteOrder.LITTLE_ENDIAN);
    buffer.put(rawData);
    buffer.flip();

    final var samples = new short[rawData.length / 2];
    buffer.asShortBuffer().get(samples);
    return samples;
  }

  private void playStatic(final @NotNull VoiceSoundBuilder builder, final short[] samples) {
    final var filter = builder.getPlayerFilter();

    for (final var target : Bukkit.getOnlinePlayers()) {
      final var conn = this.api.getConnectionOf(target.getUniqueId());
      if (conn == null) continue;
      if (filter != null && !filter.test(conn.getPlayer())) continue;

      final var channelId = acquireChannelId();

      final var channel = this.api.createStaticAudioChannel(
        channelId
      );

      if (channel == null) {
        releaseChannelId(channelId);
        continue;
      }

      channel.addTarget(conn);

      final var audioPlayer = this.api.createAudioPlayer(channel, this.api.createEncoder(), samples);
      track(channelId, audioPlayer, builder, samples);
      audioPlayer.startPlaying();
    }
  }

  private void playLocational(final @NotNull VoiceSoundBuilder builder, final short[] samples) {
    final var loc = builder.getLocation();
    if (loc == null || loc.getWorld() == null) return;

    final var channelId = acquireChannelId();

    final var channel = this.api.createLocationalAudioChannel(
      channelId,
      this.api.fromServerLevel(loc.getWorld()),
      this.api.createPosition(loc.getX(), loc.getY(), loc.getZ())
    );

    if (channel == null) {
      releaseChannelId(channelId);
      return;
    }

    channel.setDistance(builder.getDistance());
    channel.setFilter(builder.getPlayerFilter());

    final var audioPlayer = this.api.createAudioPlayer(channel, this.api.createEncoder(), samples);
    track(channelId, audioPlayer, builder, samples);
    audioPlayer.startPlaying();
  }

  // ###############################################################
  // -------------------- SIMPLE VOICE METHODS ---------------------
  // ###############################################################

  private void onServerStarted(final @NotNull VoicechatServerStartedEvent event) {
    this.api = event.getVoicechat();

    this.plugin.getLogger().info("SVC API ready ! Init DreamVoice...");

    DreamVoice.getService(VoiceWallService.class).init(this.api);
    DreamVoice.getService(VoiceSpeakerService.class).init(this.api);
    DreamVoice.getService(VoiceRecordingService.class).init(this.api);
    DreamVoice.getService(VoiceTransmitterService.class).init(this.api);

    this.plugin.getLogger().info("DreamVoice is ready !");

  }

  private void onEntitySoundPacket(final @NotNull EntitySoundPacketEvent event) {
    final var senderConn = event.getSenderConnection();
    final var receiverCon = event.getReceiverConnection();

    if (!hasValidConnections(senderConn, receiverCon)) return;

    final var senderUUID = senderConn.getPlayer().getUuid();
    final var receiverUUID = receiverCon.getPlayer().getUuid();

    final var vSender = this.playerService.getPlayer(senderUUID);
    final var vReceiver = this.playerService.getPlayer(receiverUUID);
    if (vSender == null || vReceiver == null) return;

    if (!canHear(vSender.getState(), vReceiver.getState())) {
      event.cancel();
      return;
    }

    Bukkit.getScheduler().runTask(this.plugin, () -> new fr.dreamin.dreaminvoice.api.voice.event.EntitySoundPacketEvent(
      event,
      senderConn,
      vSender,
      receiverCon,
      vReceiver,
      event.getPacket()
    ).callEvent());

  }


  private void onMicrophonePacket(final @NotNull MicrophonePacketEvent event) {
    Bukkit.getScheduler().runTask(this.plugin, () -> new fr.dreamin.dreaminvoice.api.voice.event.MicrophonePacketEvent(
      event,
      event.getSenderConnection(),
      event.getReceiverConnection(),
      event.getPacket()
    ).callEvent());
  }

}
