package fr.dreamin.dreamvoice.common.wall.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.api.room.service.VoiceRoomService;
import fr.dreamin.dreamvoice.api.wall.event.VoiceWallOcclusionEvent;
import fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.player.manager.VoiceWallManager;
import fr.dreamin.dreamvoice.common.utils.audio.AudioLimiter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform-independent implementation of {@link VoiceWallService} managing real-time acoustic soundproofing,
 * diffraction through apertures, air absorption damping, and raycast diagnostics.
 */
public final class VoiceWallServiceImpl implements VoiceWallService {

  private static final double DEFAULT_MOVEMENT_THRESHOLD = 0.5;
  private static final int DEFAULT_CACHE_CLEANUP_INTERVAL = 200;
  private static final int DEFAULT_CHECK_INTERVAL = 2;
  private static final int DEBUG_RENDER_INTERVAL = 4;
  private static final double MAX_DEBUG_DISTANCE = 32.0;
  private static final long STREAM_INACTIVITY_TIMEOUT_MS = 30000L;

  private final @NotNull VoicePlatform platform;
  private @NotNull VoicechatServerApi api;
  private final @NotNull PlayerService playerService;

  private int currentTick = 0;
  private boolean enable = false;
  private boolean debug = false;
  private boolean airDamping = true;
  private @NotNull VoiceWallMode mode = VoiceWallMode.REALISTIC;

  private final @NotNull Set<UUID> debugPlayers = ConcurrentHashMap.newKeySet();
  private final @NotNull Map<String, CachedLineOfSight> lineOfSightCache = new ConcurrentHashMap<>();
  private final @NotNull Map<String, OpusDecoder> streamDecoders = new ConcurrentHashMap<>();
  private final @NotNull Map<String, OpusEncoder> streamEncoders = new ConcurrentHashMap<>();
  private final @NotNull Map<String, Long> lastStreamActivity = new ConcurrentHashMap<>();

  private boolean codexServiceMissingLogged = false;

  public VoiceWallServiceImpl(final @NotNull VoicePlatform platform, final @NotNull PlayerService playerService) {
    this.platform = platform;
    this.playerService = playerService;
    this.platform.runTimer(this::tick, 1L, 1L);
  }

  public void tick() {
    this.currentTick++;

    if (this.enable) {
      if (this.currentTick % DEFAULT_CACHE_CLEANUP_INTERVAL == 0)
        cleanupCache();

      if (this.currentTick % DEFAULT_CHECK_INTERVAL == 0)
        processVoiceConnections();
    }

    if (!this.debugPlayers.isEmpty() && this.currentTick % DEBUG_RENDER_INTERVAL == 0)
      renderVisualDebug();
  }

  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;
  }

  @Override
  public boolean isEnable() {
    return this.enable;
  }

  @Override
  public void setEnable(final boolean value) {
    this.enable = value;
    if (!value)
      this.mode = VoiceWallMode.OFF;
    else if (this.mode == VoiceWallMode.OFF)
      this.mode = VoiceWallMode.REALISTIC;
  }

  @Override
  public @NotNull VoiceWallMode getMode() {
    return this.mode;
  }

  @Override
  public void setMode(final @NotNull VoiceWallMode mode) {
    this.mode = mode;
    this.enable = (mode != VoiceWallMode.OFF);
    this.lineOfSightCache.clear();
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
  public boolean toggleDebugPlayer(final @NotNull UUID playerUuid) {
    if (this.debugPlayers.contains(playerUuid)) {
      this.debugPlayers.remove(playerUuid);
      return false;
    }
    this.debugPlayers.add(playerUuid);
    return true;
  }

  @Override
  public boolean hasDebugPlayer(final @NotNull UUID playerUuid) {
    return this.debugPlayers.contains(playerUuid);
  }

  @Override
  public void setDebugPlayer(final @NotNull UUID playerUuid, final boolean enabled) {
    if (enabled)
      this.debugPlayers.add(playerUuid);
    else
      this.debugPlayers.remove(playerUuid);
  }

  @Override
  public boolean isAirDampingEnabled() {
    return this.airDamping;
  }

  @Override
  public void setAirDampingEnabled(final boolean value) {
    this.airDamping = value;
  }

  @Override
  public void processEntitySoundPacket(
    final @NotNull EntitySoundPacketEvent event,
    final @NotNull VPlayer vSender,
    final @NotNull VPlayer vReceiver,
    final @NotNull VoicechatConnection receiverConn
  ) {
    final var senderUuid = vSender.getUuid();
    final var common = DreamVoiceCommon.getInstance();
    final var filterService = common != null ? common.getService(VoiceFilterService.class) : null;
    final var hasFilters = filterService != null && filterService.hasActiveFilters(senderUuid);

    var totalDbLoss = 0.0;

    final var roomService = common != null ? common.getService(VoiceRoomService.class) : null;
    if (roomService != null) {
      final var roomLoss = roomService.calculateRoomAttenuationDb(senderUuid, vReceiver.getUuid());
      totalDbLoss += roomLoss;
    }

    if (this.enable && totalDbLoss < 99.0) {
      final var wallManager = vReceiver.getManager(VoiceWallManager.class);
      if (wallManager != null)
        totalDbLoss += wallManager.getTotalAttenuationDb(vSender);

      if (Math.abs(totalDbLoss) > 0.001) {
        final var occlusionEvent = new VoiceWallOcclusionEvent(
          vSender,
          vReceiver,
          totalDbLoss,
          totalDbLoss,
          totalDbLoss >= 99.0
        );
        occlusionEvent.callEvent();
        if (occlusionEvent.isCancelled())
          totalDbLoss = 0.0;
        else {
          totalDbLoss = occlusionEvent.getLossDb();
          if (occlusionEvent.isBlocked())
            totalDbLoss = 100.0;
        }
      }
    }

    final var receiverIsDebugging = this.debugPlayers.contains(vReceiver.getUuid());
    final var senderName = this.platform.getPlayerName(senderUuid);

    if (totalDbLoss >= 99.0) {
      event.cancel();
      if (receiverIsDebugging)
        this.platform.sendMessage(vReceiver.getUuid(), "voicewall.debug.blocked", senderName, totalDbLoss);
      return;
    }

    final var hasAttenuation = Math.abs(totalDbLoss) > 0.001;
    final var distance = calculateDistance(vSender, vReceiver);
    final var hasAirDamping = this.airDamping && distance > 5.0;

    if (!hasAttenuation && !hasFilters && !hasAirDamping) {
      if (receiverIsDebugging)
        this.platform.sendMessage(vReceiver.getUuid(), "voicewall.debug.direct", senderName);
      return;
    }

    try {
      if (this.debug)
        this.platform.logInfo("Audio processing for " + senderUuid + " (attenuation=" + totalDbLoss + "dB, filters=" + hasFilters + ", dist=" + distance + "m)");

      final var packet = event.getPacket();
      final var opusData = packet.getOpusEncodedData();
      if (opusData == null || opusData.length == 0)
        return;

      final var receiverUUID = receiverConn.getPlayer().getUuid();
      final var streamKey = senderUuid + ":" + receiverUUID;
      final var decoder = this.streamDecoders.computeIfAbsent(streamKey, _ -> this.api.createDecoder());
      final var encoder = this.streamEncoders.computeIfAbsent(streamKey, _ -> this.api.createEncoder());
      final var lastTime = this.lastStreamActivity.put(streamKey, System.currentTimeMillis());
      if (lastTime != null && (System.currentTimeMillis() - lastTime > 400L)) {
        decoder.resetState();
        encoder.resetState();
      }

      var pcm = decoder.decode(opusData);
      if (pcm == null || pcm.length == 0)
        return;

      final var rawRms = computeRms(pcm);
      pcm = applyDspGainAndFilters(pcm, senderUuid, filterService, hasFilters, hasAttenuation, totalDbLoss);
      final var postRms = computeRms(pcm);

      final var newOpus = encoder.encode(pcm);
      if (newOpus == null || newOpus.length == 0)
        return;

      final var newPacket = packet.entitySoundPacketBuilder()
        .opusEncodedData(newOpus)
        .build();

      event.cancel();
      this.api.sendEntitySoundPacketTo(receiverConn, newPacket);

      if (receiverIsDebugging) {
        final var gainFactor = Math.pow(10.0, -totalDbLoss / 20.0);
        final var volPercent = Math.round(gainFactor * 100.0);
        this.platform.sendMessage(vReceiver.getUuid(), "voicewall.debug.attenuated", senderName, totalDbLoss, volPercent, rawRms, postRms);
      }

    } catch (Exception e) {
      this.platform.logWarning("Audio processing error : " + e.getMessage());
    }
  }

  private static int computeRms(final short[] pcm) {
    if (pcm == null || pcm.length == 0)
      return 0;
    long sum = 0;
    for (final short s : pcm)
      sum += (long) s * s;

    return (int) Math.sqrt((double) sum / pcm.length);
  }

  @Override
  public double getAttenuationDb(final @NotNull VoiceLocation from, final @NotNull VoiceLocation to) {
    if (this.platform.hasLineOfSight(from, to))
      return 0.0;
    return this.platform.computeAcousticOcclusion(from, to, null);
  }

  @Override
  public double getAttenuationDb(final @NotNull UUID speakerUuid, final @NotNull UUID listenerUuid) {
    var totalDbLoss = 0.0;

    final var common = DreamVoiceCommon.getInstance();
    final var roomService = common != null ? common.getService(VoiceRoomService.class) : null;
    if (roomService != null) {
      final var roomLoss = roomService.calculateRoomAttenuationDb(speakerUuid, listenerUuid);
      totalDbLoss += roomLoss;
      if (totalDbLoss >= 99.0)
        return 100.0;
    }

    if (this.enable) {
      final var vReceiver = this.playerService.getPlayer(listenerUuid);
      final var vSender = this.playerService.getPlayer(speakerUuid);
      if (vReceiver != null && vSender != null) {
        final var wallManager = vReceiver.getManager(VoiceWallManager.class);
        if (wallManager != null)
          totalDbLoss += wallManager.getTotalAttenuationDb(vSender);
        else {
          final var sLoc = this.platform.getPlayerEyeLocation(speakerUuid).orElse(null);
          final var rLoc = this.platform.getPlayerEyeLocation(listenerUuid).orElse(null);
          if (sLoc != null && rLoc != null && sLoc.world().equals(rLoc.world())) {
            if (!this.platform.hasLineOfSight(sLoc, rLoc))
              totalDbLoss += this.platform.computeAcousticOcclusion(sLoc, rLoc, null);
          }
        }
      }
    }

    return totalDbLoss;
  }

  private double calculateDistance(final @NotNull VPlayer a, final @NotNull VPlayer b) {
    final var locA = this.platform.getPlayerLocation(a.getUuid()).orElse(null);
    final var locB = this.platform.getPlayerLocation(b.getUuid()).orElse(null);
    if (locA == null || locB == null || !locA.world().equals(locB.world()))
      return Double.MAX_VALUE;

    return locA.distance(locB);
  }

  private short[] applyDspGainAndFilters(
    final short @NotNull [] rawPcm,
    final @NotNull UUID senderUuid,
    final @Nullable VoiceFilterService filterService,
    final boolean hasFilters,
    final boolean hasAttenuation,
    final double totalDbLoss
  ) {
    var pcm = rawPcm;
    if (hasFilters && filterService != null)
      pcm = filterService.applyFilters(senderUuid, pcm);

    if (hasAttenuation) {
      final var gainFactor = (float) Math.pow(10.0, -totalDbLoss / 20.0);
      final var filtered = new short[pcm.length];
      for (int i = 0; i < pcm.length; i++) {
        final var sample = Math.round(pcm[i] * gainFactor);
        filtered[i] = (short) Math.clamp(sample, Short.MIN_VALUE, Short.MAX_VALUE);
      }
      pcm = AudioLimiter.process(filtered);
    }

    return pcm;
  }

  private void processVoiceConnections() {
    final var onlinePlayers = new ArrayList<>(this.playerService.getPlayers());
    if (onlinePlayers.size() < 2)
      return;

    for (int i = 0; i < onlinePlayers.size(); i++) {
      final var player1 = onlinePlayers.get(i);
      for (int j = i + 1; j < onlinePlayers.size(); j++) {
        final var player2 = onlinePlayers.get(j);
        processPlayerPair(player1, player2);
      }
    }
  }

  private void processPlayerPair(final @NotNull VPlayer vPlayer, final @NotNull VPlayer otherVPlayer) {
    if (isPlayerInvalid(vPlayer) || isPlayerInvalid(otherVPlayer))
      return;

    final var locA = this.platform.getPlayerLocation(vPlayer.getUuid()).orElse(null);
    final var locB = this.platform.getPlayerLocation(otherVPlayer.getUuid()).orElse(null);
    if (locA == null || locB == null || !locA.world().equals(locB.world()))
      return;

    final var cacheKey = generateCacheKey(vPlayer, otherVPlayer);
    final var common = DreamVoiceCommon.getInstance();
    final var codexService = common != null ? common.getService(CodexService.class) : null;
    if (codexService == null) {
      if (!this.codexServiceMissingLogged) {
        this.codexServiceMissingLogged = true;
        this.platform.logWarning("CodexService is unavailable. VoiceWall pair processing is skipped.");
      }
      return;
    }

    final var maxVoiceDistance = codexService.getConfig().getEffectiveDistance();
    final var distance = locA.distance(locB);
    if (distance > maxVoiceDistance) {
      blockBoth(vPlayer, otherVPlayer, 0.0, VoiceWallManager.WallBlockReason.DISTANCE);
      this.lineOfSightCache.remove(cacheKey);
      return;
    }

    unblockBoth(vPlayer, otherVPlayer);

    final var los = getLineOfSightCached(vPlayer, otherVPlayer, cacheKey);
    if (los == null || los.lineOfSight())
      return;

    final var attenuation = los.totalAttenuation();
    blockBoth(vPlayer, otherVPlayer, attenuation, VoiceWallManager.WallBlockReason.WALL);
  }

  private CachedLineOfSight getLineOfSightCached(final @NotNull VPlayer vPlayer, final @NotNull VPlayer otherVPlayer, final @NotNull String cacheKey) {
    final var cached = this.lineOfSightCache.get(cacheKey);
    if (cached != null && !cached.isExpired(this.currentTick, 40)) {
      if (this.enable) {
        vPlayer.consumeManager(VoiceWallManager.class, VoiceWallManager::incrementCacheHits);
        otherVPlayer.consumeManager(VoiceWallManager.class, VoiceWallManager::incrementCacheHits);
      }
      return cached;
    }

    final var eyeA = this.platform.getPlayerEyeLocation(vPlayer.getUuid()).orElse(null);
    final var eyeB = this.platform.getPlayerEyeLocation(otherVPlayer.getUuid()).orElse(null);
    if (eyeA == null || eyeB == null)
      return null;

    final var hasLos = this.platform.hasLineOfSight(eyeA, eyeB);
    final var attenuation = hasLos ? 0.0 : this.platform.computeAcousticOcclusion(eyeA, eyeB, null);
    final var computed = new CachedLineOfSight(hasLos, attenuation, this.currentTick);
    this.lineOfSightCache.put(cacheKey, computed);

    if (this.enable) {
      vPlayer.consumeManager(VoiceWallManager.class, VoiceWallManager::incrementRaycastCount);
      otherVPlayer.consumeManager(VoiceWallManager.class, VoiceWallManager::incrementRaycastCount);
    }

    return computed;
  }

  private void blockBoth(final @NotNull VPlayer vPlayer, final @NotNull VPlayer otherVPlayer, final double totalAttenuation, final @NotNull VoiceWallManager.WallBlockReason reason) {
    vPlayer.consumeManager(VoiceWallManager.class, m -> m.addBlockedPlayer(otherVPlayer, totalAttenuation, reason));
    otherVPlayer.consumeManager(VoiceWallManager.class, m -> m.addBlockedPlayer(vPlayer, totalAttenuation, reason));
  }

  private void unblockBoth(final @NotNull VPlayer vPlayer, final @NotNull VPlayer otherVPlayer) {
    vPlayer.consumeManager(VoiceWallManager.class, m -> m.removeBlockedPlayer(otherVPlayer));
    otherVPlayer.consumeManager(VoiceWallManager.class, m -> m.removeBlockedPlayer(vPlayer));
  }

  private boolean isPlayerInvalid(final @NotNull VPlayer vPlayer) {
    final var manager = vPlayer.getManager(VoiceWallManager.class);
    return manager == null || !manager.isValidClient() || !this.platform.isPlayerOnline(vPlayer.getUuid());
  }

  private static String generateCacheKey(final @NotNull VPlayer player1, final @NotNull VPlayer player2) {
    final var id1 = player1.getUuid().toString();
    final var id2 = player2.getUuid().toString();
    return id1.compareTo(id2) < 0 ? id1 + ":" + id2 : id2 + ":" + id1;
  }

  private void invalidateCacheForPlayer(final @NotNull VPlayer player) {
    final var playerId = player.getUuid().toString();
    this.lineOfSightCache.entrySet().removeIf(entry -> entry.getKey().contains(playerId));
  }

  private void cleanupCache() {
    this.lineOfSightCache.entrySet().removeIf(entry -> entry.getValue().isExpired(this.currentTick, 100));

    final var now = System.currentTimeMillis();
    this.lastStreamActivity.entrySet().removeIf(entry -> {
      if (now - entry.getValue() > STREAM_INACTIVITY_TIMEOUT_MS) {
        final var key = entry.getKey();
        final var dec = this.streamDecoders.remove(key);
        if (dec != null && !dec.isClosed()) {
          try {
            dec.close();
          } catch (Throwable ignored) {}
        }
        final var enc = this.streamEncoders.remove(key);
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

  private void renderVisualDebug() {
    for (final var viewerUuid : this.debugPlayers) {
      if (!this.platform.isPlayerOnline(viewerUuid)) {
        this.debugPlayers.remove(viewerUuid);
        continue;
      }

      final var viewerEye = this.platform.getPlayerEyeLocation(viewerUuid).orElse(null);
      if (viewerEye == null)
        continue;

      final var closestPlayerUuid = findClosestPlayer(viewerUuid, viewerEye);
      if (closestPlayerUuid == null)
        continue;

      final var targetEye = this.platform.getPlayerEyeLocation(closestPlayerUuid).orElse(null);
      if (targetEye == null)
        continue;

      final var hasLos = this.platform.hasLineOfSight(viewerEye, targetEye);
      this.platform.renderDebugRay(viewerUuid, viewerEye, targetEye, hasLos);
    }
  }

  private @Nullable UUID findClosestPlayer(final @NotNull UUID viewerUuid, final @NotNull VoiceLocation viewerLoc) {
    var closestUuid = (UUID) null;
    var closestDist = Double.MAX_VALUE;

    final var common = DreamVoiceCommon.getInstance();
    final var codexService = common != null ? common.getService(CodexService.class) : null;
    final var maxDist = codexService != null ? codexService.getConfig().getEffectiveDistance() : MAX_DEBUG_DISTANCE;

    for (final var targetUuid : this.platform.getOnlinePlayers()) {
      if (targetUuid.equals(viewerUuid))
        continue;

      final var targetLoc = this.platform.getPlayerLocation(targetUuid).orElse(null);
      if (targetLoc == null || !targetLoc.world().equals(viewerLoc.world()))
        continue;

      final var d = viewerLoc.distance(targetLoc);
      if (d < closestDist && d <= maxDist) {
        closestDist = d;
        closestUuid = targetUuid;
      }
    }
    return closestUuid;
  }

  public void onPlayerDisconnect(final @NotNull UUID uuid) {
    this.debugPlayers.remove(uuid);
    final var vPlayer = this.playerService.getPlayer(uuid);
    if (vPlayer != null)
      invalidateCacheForPlayer(vPlayer);

    final var uidStr = uuid.toString();
    this.lastStreamActivity.keySet().removeIf(k -> k.contains(uidStr));
    this.streamDecoders.entrySet().removeIf(e -> {
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

  private record CachedLineOfSight(boolean lineOfSight, double totalAttenuation, long timestamp, int tickCreated) {
    public CachedLineOfSight(final boolean hasLineOfSight, final double totalAttenuation, final int tickCreated) {
      this(hasLineOfSight, totalAttenuation, System.currentTimeMillis(), tickCreated);
    }

    public boolean isExpired(final int currentTick, final int maxAge) {
      return (currentTick - this.tickCreated) > maxAge;
    }
  }

}
