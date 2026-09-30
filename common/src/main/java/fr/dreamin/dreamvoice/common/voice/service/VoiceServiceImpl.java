package fr.dreamin.dreamvoice.common.voice.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.persistence.service.VoicePersistenceService;
import fr.dreamin.dreamvoice.api.player.model.PlayerState;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
import fr.dreamin.dreamvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.voice.model.VoiceSoundBuilder;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.common.broadcast.service.VoiceBroadcastServiceImpl;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.projection.service.VoiceProjectionServiceImpl;
import fr.dreamin.dreamvoice.common.radio.service.VoiceRadioServiceImpl;
import fr.dreamin.dreamvoice.common.recording.service.VoiceRecordingServiceImpl;
import fr.dreamin.dreamvoice.common.speaker.service.VoiceSpeakerServiceImpl;
import fr.dreamin.dreamvoice.common.speech.service.VoiceSpeechServiceImpl;
import fr.dreamin.dreamvoice.common.transmitter.service.VoiceTransmitterServiceImpl;
import fr.dreamin.dreamvoice.common.wiretap.service.VoiceWiretapServiceImpl;
import org.jetbrains.annotations.NotNull;

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
 * Core platform-independent implementation of {@link VoiceService} and {@link VoicechatPlugin}
 * bridging Simple Voice Chat lifecycle, packet routing, audio streaming channels, and Opus codecs.
 */
public final class VoiceServiceImpl implements VoiceService, VoicechatPlugin {

  private static final int MAX_POOL_SIZE = 1024;
  private static final String PLUGIN_ID = "DreamVoice";

  private final @NotNull VoicePlatform platform;
  private VoicechatServerApi api;

  private boolean debug = true;

  private final @NotNull PlayerService playerService;
  private final @NotNull VoiceWallService voiceWallService;
  private boolean projectionServiceMissingLogged = false;

  private final Queue<UUID> channelIdPool = new ConcurrentLinkedQueue<>();
  private final Map<UUID, AudioPlayer> activePlayers = new ConcurrentHashMap<>();

  private final Map<UUID, OpusDecoder> decoders = new ConcurrentHashMap<>();
  private final Map<UUID, OpusEncoder> encoders = new ConcurrentHashMap<>();

  public VoiceServiceImpl(
    final @NotNull VoicePlatform platform,
    final @NotNull PlayerService playerService,
    final @NotNull VoiceWallService voiceWallService
  ) {
    this.platform = platform;
    this.playerService = playerService;
    this.voiceWallService = voiceWallService;
  }

  @Override
  public String getPluginId() {
    return PLUGIN_ID;
  }

  @Override
  public void registerEvents(final EventRegistration registration) {
    registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
    registration.registerEvent(de.maxhenkel.voicechat.api.events.MicrophonePacketEvent.class, this::onMicrophonePacket);
    registration.registerEvent(de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent.class, this::onEntitySoundPacket);
    registration.registerEvent(PlayerDisconnectedEvent.class, this::onPlayerDisconnected);
  }

  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;
  }

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
      this.platform.logWarning("stopSound failed for " + channelId + ": " + t.getMessage());
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
        this.platform.logWarning("clearAllSounds: stop failed for " + id + ": " + t.getMessage());
      }
    }
  }

  @Override
  public boolean isPlayerConnected(final @NotNull UUID uuid) {
    return this.api != null && this.api.getConnectionOf(uuid) != null;
  }

  @Override
  public OpusDecoder getDecoder(final @NotNull UUID uuid) {
    return this.decoders.computeIfAbsent(uuid, _ -> this.api.createDecoder());
  }

  @Override
  public OpusEncoder getEncoder(final @NotNull UUID uuid) {
    return this.encoders.computeIfAbsent(uuid, _ -> this.api.createEncoder());
  }

  @Override
  public boolean canHear(final @NotNull UUID speakerUuid, final @NotNull UUID listenerUuid) {
    if (speakerUuid.equals(listenerUuid))
      return true;

    final var common = DreamVoiceCommon.getInstance();
    // 1. Point-to-point transmitter channel
    final var transmitterService = common != null ? common.getService(VoiceTransmitterService.class) : null;
    if (transmitterService != null && transmitterService.isTransmitter(speakerUuid)) {
      for (final var cfg : transmitterService.getReceivers(speakerUuid)) {
        if (cfg.getUuid().equals(listenerUuid)) {
          if (!cfg.hasMaxDistance())
            return true;
          final var sLoc = this.platform.getPlayerLocation(speakerUuid).orElse(null);
          final var lLoc = this.platform.getPlayerLocation(listenerUuid).orElse(null);
          if (sLoc != null && lLoc != null && sLoc.world().equals(lLoc.world())) {
            if (cfg.getMaxDistance() != null && sLoc.distance(lLoc) <= cfg.getMaxDistance())
              return true;
          }
        }
      }
    }

    // 2. Shared radio channel
    final var radioService = common != null ? common.getService(VoiceRadioService.class) : null;
    if (radioService != null) {
      final var sChan = radioService.getChannelOfPlayer(speakerUuid);
      final var lChan = radioService.getChannelOfPlayer(listenerUuid);
      if (sChan != null && lChan != null && sChan.getName().equalsIgnoreCase(lChan.getName()))
        return true;
    }

    // 3. Proximity voice chat
    return canHearProximity(speakerUuid, listenerUuid);
  }

  @Override
  public boolean canHearProximity(final @NotNull UUID speakerUuid, final @NotNull UUID listenerUuid) {
    if (speakerUuid.equals(listenerUuid))
      return true;

    if (!this.platform.isPlayerOnline(speakerUuid) || !this.platform.isPlayerOnline(listenerUuid))
      return false;

    // SVC connection check
    if (this.api != null) {
      final var senderConn = this.api.getConnectionOf(speakerUuid);
      final var receiverConn = this.api.getConnectionOf(listenerUuid);
      if (!hasValidConnections(senderConn, receiverConn))
        return false;
    }

    // Dead / Alive / Spectator states
    final var vSender = this.playerService.getPlayer(speakerUuid);
    final var vReceiver = this.playerService.getPlayer(listenerUuid);
    if (vSender == null || vReceiver == null)
      return false;

    if (!canHear(vSender.getState(), vReceiver.getState()))
      return false;

    // Body anchor projection constraints
    final var common = DreamVoiceCommon.getInstance();
    final var projectionService = common != null ? common.getService(VoiceProjectionService.class) : null;
    var effectiveSpeakerLoc = this.platform.getPlayerLocation(speakerUuid).orElse(null);
    final var listenerLoc = this.platform.getPlayerLocation(listenerUuid).orElse(null);
    if (effectiveSpeakerLoc == null || listenerLoc == null)
      return false;

    if (projectionService != null) {
      final var projection = projectionService.getProjection(speakerUuid);
      if (projection != null) {
        if (!projection.isEmitVoiceAtPlayer() && !projection.isEmitVoiceAtAnchor())
          return false;
        if (!projection.isEmitVoiceAtPlayer())
          effectiveSpeakerLoc = projection.getAnchorLocation();
      }

      final var receiverProjection = projectionService.getProjection(listenerUuid);
      if (receiverProjection != null && !receiverProjection.isHearPlayerEnvironment())
        return false;
    }

    if (!effectiveSpeakerLoc.world().equals(listenerLoc.world()))
      return false;

    // Distance check
    final var codexService = common != null ? common.getService(CodexService.class) : null;
    final var maxDist = codexService != null ? codexService.getConfig().getEffectiveDistance() : 16.0;
    final var dist = effectiveSpeakerLoc.distance(listenerLoc);
    if (dist > maxDist)
      return false;

    // Acoustic attenuation (VoiceWall & soundproof rooms)
    final var attenuation = getEffectiveAttenuationDb(speakerUuid, listenerUuid);
    return attenuation < 99.0;
  }

  @Override
  public double getEffectiveAttenuationDb(final @NotNull UUID speakerUuid, final @NotNull UUID listenerUuid) {
    if (speakerUuid.equals(listenerUuid))
      return 0.0;

    final var common = DreamVoiceCommon.getInstance();
    final var wallService = common != null ? common.getService(VoiceWallService.class) : null;
    if (wallService != null)
      return wallService.getAttenuationDb(speakerUuid, listenerUuid);

    return 0.0;
  }

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
          this.platform.logWarning("onStopped callback failed: " + t.getMessage());
        }
      }

      if (builder.isLoop()) {
        this.platform.runSync(() -> {
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
      this.platform.logWarning("Invalid audio data: " + rawData.length + " bytes");
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
    for (final var targetUuid : this.platform.getOnlinePlayers()) {
      final var conn = this.api.getConnectionOf(targetUuid);
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
    if (loc == null)
      return;

    final var level = this.platform.getServerLevel(loc.world());
    if (level == null)
      return;

    final var channelId = acquireChannelId();

    final var channel = this.api.createLocationalAudioChannel(
      channelId,
      level,
      this.api.createPosition(loc.x(), loc.y(), loc.z())
    );

    if (channel == null) {
      releaseChannelId(channelId);
      return;
    }

    channel.setDistance((float) builder.getDistance());
    if (builder.getPlayerFilter() != null)
      channel.setFilter(builder.getPlayerFilter());

    final var audioPlayer = this.api.createAudioPlayer(channel, this.api.createEncoder(), samples);
    track(channelId, audioPlayer, builder, samples);
    audioPlayer.startPlaying();
  }

  public void onServerStarted(final @NotNull VoicechatServerStartedEvent event) {
    this.api = event.getVoicechat();
    this.platform.logInfo("SVC API ready ! Init DreamVoice...");

    final var common = DreamVoiceCommon.getInstance();
    if (common != null) {
      common.onSvcStarted(this.api);
      final var persistenceService = common.getService(VoicePersistenceService.class);
      if (persistenceService != null)
        persistenceService.loadAll();
    }

    this.platform.logInfo("DreamVoice is ready !");
  }

  public void onEntitySoundPacket(final @NotNull de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent event) {
    if (!SoundPacketEvent.SOURCE_PROXIMITY.equals(event.getSource()))
      return;

    final var senderConn = event.getSenderConnection();
    final var receiverCon = event.getReceiverConnection();

    if (!hasValidConnections(senderConn, receiverCon))
      return;

    final var senderUUID = senderConn.getPlayer().getUuid();
    final var receiverUUID = receiverCon.getPlayer().getUuid();

    final var common = DreamVoiceCommon.getInstance();
    final var projectionService = common != null ? common.getService(VoiceProjectionService.class) : null;
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
      this.platform.logWarning("VoiceProjectionService is unavailable. Projection constraints are skipped.");
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

  public void onMicrophonePacket(final @NotNull de.maxhenkel.voicechat.api.events.MicrophonePacketEvent event) {
    final var common = DreamVoiceCommon.getInstance();
    if (common != null) {
      if (common.getBroadcastService() instanceof VoiceBroadcastServiceImpl bImpl) {
        try {
          bImpl.onMicrophone(event);
        } catch (final Throwable t) {
          this.platform.logError("Error in broadcastService.onMicrophone", t);
        }
      }
      if (common.getSpeakerService() instanceof VoiceSpeakerServiceImpl sImpl) {
        try {
          sImpl.onMicrophone(event);
        } catch (final Throwable t) {
          this.platform.logError("Error in speakerService.onMicrophone", t);
        }
      }
      if (common.getRadioService() instanceof VoiceRadioServiceImpl rImpl) {
        try {
          rImpl.onMicrophone(event);
        } catch (final Throwable t) {
          this.platform.logError("Error in radioService.onMicrophone", t);
        }
      }
      if (common.getRecordingService() instanceof VoiceRecordingServiceImpl recImpl) {
        try {
          recImpl.onMicrophone(event);
        } catch (final Throwable t) {
          this.platform.logError("Error in recordingService.onMicrophone", t);
        }
      }
      if (common.getWiretapService() instanceof VoiceWiretapServiceImpl wImpl) {
        try {
          wImpl.onMicrophone(event);
        } catch (final Throwable t) {
          this.platform.logError("Error in wiretapService.onMicrophone", t);
        }
      }
      if (common.getTransmitterService() instanceof VoiceTransmitterServiceImpl tImpl) {
        try {
          tImpl.onMicrophone(event);
        } catch (final Throwable t) {
          this.platform.logError("Error in transmitterService.onMicrophone", t);
        }
      }
      if (common.getProjectionService() instanceof VoiceProjectionServiceImpl pImpl) {
        try {
          pImpl.onMicrophone(event);
        } catch (final Throwable t) {
          this.platform.logError("Error in projectionService.onMicrophone", t);
        }
      }
      if (common.getSpeechService() instanceof VoiceSpeechServiceImpl spImpl) {
        try {
          spImpl.onMicrophone(event);
        } catch (final Throwable t) {
          this.platform.logError("Error in speechService.onMicrophone", t);
        }
      }
    }

    final var micEvent = new MicrophonePacketEvent(
      event,
      event.getSenderConnection(),
      event.getReceiverConnection(),
      event.getPacket()
    );
    micEvent.callEvent();
  }

  public void onPlayerDisconnected(final @NotNull PlayerDisconnectedEvent event) {
    final var uuid = event.getPlayerUuid();
    this.decoders.remove(uuid);
    this.encoders.remove(uuid);
    final var common = DreamVoiceCommon.getInstance();
    if (common != null)
      common.onPlayerDisconnect(uuid);
  }

}
