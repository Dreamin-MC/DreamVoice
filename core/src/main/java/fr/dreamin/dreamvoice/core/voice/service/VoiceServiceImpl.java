package fr.dreamin.dreamvoice.core.voice.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.api.persistence.service.VoicePersistenceService;
import fr.dreamin.dreamvoice.api.player.model.PlayerState;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.voice.model.VoiceSoundBuilder;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Core implementation of {@link VoiceService} and {@link VoicechatPlugin} bridging
 * Simple Voice Chat lifecycle, packet routing, audio streaming channels, and Opus codecs.
 */
public final class VoiceServiceImpl implements VoiceService, VoicechatPlugin, Listener {

  // ###############################################################
  // ----------------------- STATIC FIELDS -------------------------
  // ###############################################################

  private static final int MAX_POOL_SIZE = 1024;
  private static final String PLUGIN_ID = "DreamVoice";

  // ###############################################################
  // --------------------- INSTANCE FIELDS -------------------------
  // ###############################################################

  private final @NotNull DreamVoice plugin;
  private VoicechatServerApi api;

  private boolean debug = true;

  private final @NotNull PlayerService playerService;
  private final @NotNull VoiceWallService voiceWallService;
  private boolean projectionServiceMissingLogged = false;

  private final Queue<UUID> channelIdPool = new ConcurrentLinkedQueue<>();
  private final Map<UUID, AudioPlayer> activePlayers = new ConcurrentHashMap<>();

  private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
  private final Map<UUID, OpusEncoder> encoders = new ConcurrentHashMap<>();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceServiceImpl(
    final @NotNull DreamVoice plugin,
    final @NotNull PlayerService playerService,
    final @NotNull VoiceWallService voiceWallService
  ) {
    this.plugin = plugin;
    this.playerService = playerService;
    this.voiceWallService = voiceWallService;

    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  // ###############################################################
  // ------------------------ SVC METHODS --------------------------
  // ###############################################################

  @Override
  public String getPluginId() {
    return PLUGIN_ID;
  }

  @Override
  public void registerEvents(final EventRegistration registration) {
    registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
    registration.registerEvent(de.maxhenkel.voicechat.api.events.MicrophonePacketEvent.class, this::onMicrophonePacket);
    registration.registerEvent(de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent.class, this::onEntitySoundPacket);
  }

  // ###############################################################
  // ------------------- PUBLIC SERVICE METHODS --------------------
  // ###############################################################

  @Override
  public boolean isDebug() {
    return this.debug;
  }

  @Override
  public void setDebug(final boolean value) {
    this.debug = value;
  }

  @Override
  public VoicechatServerApi getAPI() {
    return this.api;
  }

  @Override
  public void playSound(final @NotNull VoiceSoundBuilder builder) {
    if (this.api == null)
      return;

    final var raw = builder.getRawAudioData();
    if (raw == null || raw.length == 0)
      return;

    final var samples = audioToShorts(raw);
    if (samples.length == 0)
      return;

    if (builder.getLocation() == null)
      playStatic(builder, samples);
    else
      playLocational(builder, samples);
  }

  @Override
  public int getActiveSoundCount() {
    return this.activePlayers.size();
  }

  @Override
  public Set<UUID> getActiveSoundIds() {
    return Set.copyOf(this.activePlayers.keySet());
  }

  @Override
  public boolean stopSound(final @NotNull UUID channelId) {
    final var p = this.activePlayers.get(channelId);
    if (p == null)
      return false;
    try {
      p.stopPlaying();
    } catch (Throwable t) {
      this.activePlayers.remove(channelId);
      releaseChannelId(channelId);
      this.plugin.getLogger().warning("stopSound failed for " + channelId + ": " + t.getMessage());
    }
    return true;
  }

  @Override
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
  public boolean isPlayerConnected(final @NotNull UUID uuid) {
    return this.api.getConnectionOf(uuid) != null;
  }

  @Override
  public OpusDecoder getDecoder(final @NotNull UUID uuid) {
    return this.decoders.computeIfAbsent(uuid, _ -> this.api.createDecoder());
  }

  @Override
  public OpusEncoder getEncoder(final @NotNull UUID uuid) {
    return this.encoders.computeIfAbsent(uuid, _ -> this.api.createEncoder());
  }

  // ###############################################################
  // ------------------- PRIVATE HELPER METHODS --------------------
  // ###############################################################

  private static boolean canHear(final PlayerState speaker, final PlayerState listener) {
    return switch (listener) {
      case ALIVE, SPECTATE -> speaker == PlayerState.ALIVE;
      case DEAD -> speaker == PlayerState.ALIVE || speaker == PlayerState.DEAD;
    };
  }

  private static boolean hasValidConnections(final VoicechatConnection sender, final VoicechatConnection receiver) {
    return sender != null && receiver != null;
  }

  private UUID acquireChannelId() {
    final var id = this.channelIdPool.poll();
    return id != null ? id : UUID.randomUUID();
  }

  private void releaseChannelId(final @NotNull UUID id) {
    if (this.channelIdPool.size() < MAX_POOL_SIZE)
      this.channelIdPool.offer(id);
  }

  private void track(final @NotNull UUID channelId, final @NotNull AudioPlayer player, final @NotNull VoiceSoundBuilder builder, final short[] samples) {
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
          if (builder.getLocation() == null)
            playStatic(builder, samples);
          else
            playLocational(builder, samples);
        });
      }
    });
  }

  private short[] audioToShorts(final byte[] rawData) {
    if (rawData.length % 2 != 0 || rawData.length == 0) {
      this.plugin.getLogger().warning("Invalid audio data: " + rawData.length + " bytes");
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
    final var channelId = acquireChannelId();
    final var channel = this.api.createStaticAudioChannel(channelId);

    if (channel == null) {
      releaseChannelId(channelId);
      return;
    }

    var hasTarget = false;
    for (final var target : Bukkit.getOnlinePlayers()) {
      final var conn = this.api.getConnectionOf(target.getUniqueId());
      if (conn == null)
        continue;
      if (filter != null && !filter.test(conn.getPlayer()))
        continue;

      channel.addTarget(conn);
      hasTarget = true;
    }

    if (!hasTarget) {
      releaseChannelId(channelId);
      return;
    }

    final var audioPlayer = this.api.createAudioPlayer(channel, this.api.createEncoder(), samples);
    track(channelId, audioPlayer, builder, samples);
    audioPlayer.startPlaying();
  }

  private void playLocational(final @NotNull VoiceSoundBuilder builder, final short[] samples) {
    final var loc = builder.getLocation();
    if (loc == null || loc.getWorld() == null)
      return;

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

  private <T> @Nullable T requireService(final @NotNull Class<T> serviceClass) {
    final var service = DreamVoice.getService(serviceClass);
    if (service == null)
      this.plugin.getLogger().severe("Missing required service " + serviceClass.getSimpleName() + " during voice startup.");
    return service;
  }

  // ###############################################################
  // -------------------- SVC EVENT CALLBACKS ----------------------
  // ###############################################################

  private void onServerStarted(final @NotNull VoicechatServerStartedEvent event) {
    this.api = event.getVoicechat();

    this.plugin.getLogger().info("SVC API ready ! Init DreamVoice...");

    final var wallService = requireService(VoiceWallService.class);
    final var speakerService = requireService(VoiceSpeakerService.class);
    final var recordingService = requireService(VoiceRecordingService.class);
    final var transmitterService = requireService(VoiceTransmitterService.class);
    final var radioService = requireService(VoiceRadioService.class);
    final var projectionService = requireService(VoiceProjectionService.class);
    final var wiretapService = requireService(VoiceWiretapService.class);

    if (wallService == null || speakerService == null || recordingService == null
      || transmitterService == null || radioService == null || projectionService == null || wiretapService == null) {
      this.plugin.getLogger().severe("DreamVoice startup aborted: one or more required services are missing.");
      Bukkit.getPluginManager().disablePlugin(this.plugin);
      return;
    }

    wallService.init(this.api);
    speakerService.init(this.api);
    recordingService.init(this.api);
    transmitterService.init(this.api);
    radioService.init(this.api);
    projectionService.init(this.api);
    wiretapService.init(this.api);

    final var persistenceService = DreamVoice.getService(VoicePersistenceService.class);
    if (persistenceService != null)
      persistenceService.loadAll();

    this.plugin.getLogger().info("DreamVoice is ready !");
  }

  private void onEntitySoundPacket(final @NotNull de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent event) {
    final var senderConn = event.getSenderConnection();
    final var receiverCon = event.getReceiverConnection();

    if (!hasValidConnections(senderConn, receiverCon))
      return;

    final var senderUUID = senderConn.getPlayer().getUuid();
    final var receiverUUID = receiverCon.getPlayer().getUuid();

    final var projectionService = DreamVoice.getService(VoiceProjectionService.class);
    if (projectionService != null) {
      final var projection = projectionService.getProjection(senderUUID);
      if (projection != null && !projection.isEmitVoiceAtPlayer()) {
        event.cancel();
        return;
      }
      final var receiverProjection = projectionService.getProjection(receiverUUID);
      if (receiverProjection != null && !receiverProjection.isHearPlayerEnvironment()) {
        event.cancel();
        return;
      }
    } else if (!this.projectionServiceMissingLogged) {
      this.projectionServiceMissingLogged = true;
      this.plugin.getLogger().warning("VoiceProjectionService is unavailable. Projection constraints are skipped.");
    }

    final var vSender = this.playerService.getPlayer(senderUUID);
    final var vReceiver = this.playerService.getPlayer(receiverUUID);
    if (vSender == null || vReceiver == null)
      return;

    if (!canHear(vSender.getState(), vReceiver.getState())) {
      event.cancel();
      return;
    }

    this.voiceWallService.processEntitySoundPacket(event, vSender, vReceiver, receiverCon);
  }

  private void onMicrophonePacket(final @NotNull de.maxhenkel.voicechat.api.events.MicrophonePacketEvent event) {
    Bukkit.getScheduler().runTask(this.plugin, () -> new MicrophonePacketEvent(
      event,
      event.getSenderConnection(),
      event.getReceiverConnection(),
      event.getPacket()
    ).callEvent());
  }

  // ###############################################################
  // ---------------------- EVENT LISTENERS ------------------------
  // ###############################################################

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    final var uuid = event.getPlayer().getUniqueId();
    this.decoders.remove(uuid);
    this.encoders.remove(uuid);
  }

}
