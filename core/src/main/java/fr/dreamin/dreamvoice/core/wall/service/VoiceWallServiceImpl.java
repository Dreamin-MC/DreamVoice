package fr.dreamin.dreamvoice.core.wall.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamapi.core.time.Tick;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.api.voice.event.EntitySoundPacketEvent;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.player.manager.VoiceWallManager;
import fr.dreamin.dreamvoice.core.utils.raycast.VoiceRayCast;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VoiceWallServiceImpl extends Tick implements VoiceWallService, Listener {

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;
  private final @NotNull PlayerService playerService;

  private boolean enable = false, enableStats, debug = false;

  private double
    wallBypassWidth = 2.0,
    movementThreshold = 0.5,
    cacheCleanupInterval = 200; // tick

  private int checkInterval = 2; // runs every 2 ticks (10 times/sec)

  private final @NotNull Map<String, CachedLineOfSight> lineOfSightCache = new ConcurrentHashMap<>();
  private final @NotNull Map<String, Long> lastProcessedPairs = new ConcurrentHashMap<>();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceWallServiceImpl(final @NotNull DreamVoice plugin, final @NotNull PlayerService playerService) {
    this.plugin = plugin;
    this.playerService = playerService;

    startTick();

    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  // ###############################################################
  // -------------------------- METHODS ----------------------------
  // ###############################################################

  @Override
  public void tick() {
    super.tick();

    if (!this.enable)
      return;
    if (getActualTick() % this.cacheCleanupInterval == 0)
      cleanupCache();

    if (getActualTick() % this.checkInterval == 0)
      processVoiceConnections();
  }


  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

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
  }

  @Override
  public boolean isDebug() {
    return this.debug;
  }

  @Override
  public void setDebug(final boolean value) {
    this.debug = value;
  }

  // #################################################################
  // ---------------------- PRIVATE METHOD ---------------------------
  // #################################################################

  private void processVoiceConnections() {
    final var players = new ArrayList<>(this.playerService.getPlayers());

    // Phase 1: Mettre à jour l'état de mouvement de chaque joueur
    for (final var vPlayer : players) {
      if (isPlayerInvalid(vPlayer))
        continue;

      final var manager = vPlayer.getManager(VoiceWallManager.class);
      if (manager == null)
        continue;

      if (manager.hasMovedSignificantly(this.movementThreshold)) {
        manager.updatePosition();
        manager.setMoved(true);
        invalidateCacheForPlayer(vPlayer);
      } else
        manager.setMoved(false);
    }

    // Phase 2: Traitement par paire uniquement si au moins un joueur a bougé ou s'il n'est pas encore en cache
    for (int i = 0; i < players.size(); i++) {
      final var vPlayer = players.get(i);
      if (isPlayerInvalid(vPlayer))
        continue;

      final var managerA = vPlayer.getManager(VoiceWallManager.class);
      if (managerA == null)
        continue;

      for (int j = i + 1; j < players.size(); j++) {
        final var otherVPlayer = players.get(j);
        if (isPlayerInvalid(otherVPlayer))
          continue;

        final var managerB = otherVPlayer.getManager(VoiceWallManager.class);
        if (managerB == null)
          continue;

        final var cacheKey = generateCacheKey(vPlayer, otherVPlayer);

        // Si aucun des 2 joueurs n'a bougé et que le cache existe, aucun calcul nécessaire !
        if (!managerA.isMoved() && !managerB.isMoved() && this.lineOfSightCache.containsKey(cacheKey))
          continue;

        processPlayerPair(vPlayer, otherVPlayer, cacheKey);
      }
    }
  }

  private void processPlayerPair(final @NotNull VPlayer vPlayer, final @NotNull VPlayer otherVPlayer, final @NotNull String cacheKey) {
    final var pa = vPlayer.getBukkitPlayer();
    final var pb = otherVPlayer.getBukkitPlayer();
    if (pa == null || pb == null)
      return;

    if (!pa.getWorld().equals(pb.getWorld())) {
      blockBoth(vPlayer, otherVPlayer, 0.0, VoiceWallManager.WallBlockReason.DISTANCE);
      this.lineOfSightCache.put(cacheKey, new CachedLineOfSight(false, 100.0, getActualTick()));
      return;
    }

    final var maxVoiceDistance = DreamVoice.getService(CodexService.class).getConfig().getDistance();
    final var distance = pa.getLocation().distance(pb.getLocation());
    if (distance > maxVoiceDistance) {
      blockBoth(vPlayer, otherVPlayer, 0.0, VoiceWallManager.WallBlockReason.DISTANCE);
      this.lineOfSightCache.put(cacheKey, new CachedLineOfSight(false, 100.0, getActualTick()));
      return;
    }

    unblockBoth(vPlayer, otherVPlayer);

    final var los = getLineOfSightCached(vPlayer, otherVPlayer, cacheKey);

    if (los.lineOfSight())
      return;

    blockBoth(vPlayer, otherVPlayer, los.totalAttenuation(), VoiceWallManager.WallBlockReason.WALL);
  }

  private CachedLineOfSight getLineOfSightCached(final @NotNull VPlayer vPlayer, final @NotNull VPlayer otherVPlayer, final @NotNull String cacheKey) {
    final var cached = this.lineOfSightCache.get(cacheKey);
    if (cached != null && !cached.isExpired(getActualTick(), 600)) {
      if (this.enableStats) {
        vPlayer.consumeManager(VoiceWallManager.class, VoiceWallManager::incrementCacheHits);
        otherVPlayer.consumeManager(VoiceWallManager.class, VoiceWallManager::incrementCacheHits);
      }
      return cached;
    }

    final var result = VoiceRayCast.check(vPlayer.getBukkitPlayer(), otherVPlayer.getBukkitPlayer());
    final var computed = new CachedLineOfSight(result.lineOfSight(), result.totalAttenuation(), getActualTick());

    this.lineOfSightCache.put(cacheKey, computed);

    if (this.enableStats) {
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
    return manager == null || !manager.isValidClient() || !vPlayer.isOnline();
  }

  private String generateCacheKey(final @NotNull VPlayer player1, final @NotNull VPlayer player2) {
    final var id1 = player1.getUuid().toString();
    final var id2 = player2.getUuid().toString();

    if (id1.compareTo(id2) < 0)
      return id1 + ":" + id2;
    else
      return id2 + ":" + id1;
  }

  private void invalidateCacheForPlayer(final @NotNull VPlayer player) {
    final var playerId = player.getUuid().toString();
    this.lineOfSightCache.entrySet().removeIf(entry ->
      entry.getKey().contains(playerId));
  }

  private void cleanupCache() {
    this.lineOfSightCache.entrySet().removeIf(entry ->
      entry.getValue().isExpired(getActualTick(), 1200));

    this.lastProcessedPairs.entrySet().removeIf(entry ->
      (System.currentTimeMillis() - entry.getValue()) > 10000);
  }

  // ###############################################################
  // -------------------- VOICE MANAGE METHODS ---------------------
  // ###############################################################

  private void applyVolume(final @NotNull de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent event, final @NotNull VoicechatConnection receiver, final double totalDbLoss) {
    if (Math.abs(totalDbLoss) <= 0.001)
      return;

    try {
      if (this.debug)
        this.plugin.getLogger().info("Attenuation: " + totalDbLoss);

      final var packet = event.getPacket();

      final var opusDta = packet.getOpusEncodedData();
      if (opusDta == null || opusDta.length == 0)
        return;

      final var receiverUUID = receiver.getPlayer().getUuid();

      final var decoder = DreamVoice.getService(VoiceService.class).getDecoder(receiverUUID);
      final var encoder = DreamVoice.getService(VoiceService.class).getEncoder(receiverUUID);

      if (decoder == null || encoder == null)
        return;

      final var pcm = decoder.decode(opusDta);

      if (pcm == null || pcm.length != 960)
        return;

      final var dbReduction = (float) totalDbLoss;
      final var gain = (float) Math.pow(10.0, dbReduction / 20.0);

      for (int i = 0; i < pcm.length; i++)
        pcm[i] = (short) (pcm[i] * gain);

      final var newOpus = encoder.encode(pcm);

      final var newPacket = packet.entitySoundPacketBuilder()
        .channelId(packet.getChannelId())
        .entityUuid(packet.getEntityUuid())
        .distance(packet.getDistance())
        .whispering(packet.isWhispering())
        .opusEncodedData(newOpus)
        .category(packet.getCategory())
        .build();

      this.api.sendEntitySoundPacketTo(receiver, newPacket);
      event.cancel();

    } catch (Exception e) {
      this.plugin.getLogger().warning("Volume attenuation error : " + e.getMessage());
    }
  }


  // ###############################################################
  // ---------------------- LISTENER METHODS -----------------------
  // ###############################################################

  @EventHandler
  private void onEntitySound(final @NotNull EntitySoundPacketEvent event) {
    if (!this.enable)
      return;

    event.getVReceiver().consumeManager(VoiceWallManager.class, manager ->
      applyVolume(event.getSvcEvent(), event.getReceiver(), manager.getTotalAttenuationDb(event.getVSender()))
    );
  }

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    final var vPlayer = this.playerService.getPlayer(event.getPlayer());
    if (vPlayer != null)
      invalidateCacheForPlayer(vPlayer);
  }


  // ###############################################################
  // ---------------------------- CLASS ----------------------------
  // ###############################################################

  private record CachedLineOfSight(boolean lineOfSight, double totalAttenuation, long timestamp, int tickCreated) {

    public CachedLineOfSight(final boolean hasLineOfSight, final double totalAttenuation , final int tickCreated) {
      this(hasLineOfSight, totalAttenuation, System.currentTimeMillis(), tickCreated);
    }

    public boolean isExpired(final int currentTick, final int maxAge) {
      return (currentTick - this.tickCreated) > maxAge;
    }

  }

}

