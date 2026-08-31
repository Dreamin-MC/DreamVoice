package fr.dreamin.dreamvoice.core.wall.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamapi.core.time.Tick;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
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
  private @NotNull fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode mode = fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode.REALISTIC;

  private final @NotNull java.util.Set<java.util.UUID> debugPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

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

    if (this.enable) {
      if (getActualTick() % this.cacheCleanupInterval == 0)
        cleanupCache();

      if (getActualTick() % this.checkInterval == 0)
        processVoiceConnections();
    }

    if (!this.debugPlayers.isEmpty() && getActualTick() % 4 == 0) {
      renderVisualDebug();
    }
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
    if (!value) {
      this.mode = fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode.OFF;
    } else if (this.mode == fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode.OFF) {
      this.mode = fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode.REALISTIC;
    }
  }

  @Override
  public @NotNull fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode getMode() {
    return this.mode;
  }

  @Override
  public void setMode(final @NotNull fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode mode) {
    this.mode = mode;
    this.enable = (mode != fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode.OFF);
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
  public boolean toggleDebugPlayer(final @NotNull org.bukkit.entity.Player player) {
    final var uid = player.getUniqueId();
    if (this.debugPlayers.contains(uid)) {
      this.debugPlayers.remove(uid);
      return false;
    } else {
      this.debugPlayers.add(uid);
      return true;
    }
  }

  @Override
  public boolean hasDebugPlayer(final @NotNull java.util.UUID playerUuid) {
    return this.debugPlayers.contains(playerUuid);
  }

  @Override
  public void setDebugPlayer(final @NotNull java.util.UUID playerUuid, final boolean enabled) {
    if (enabled)
      this.debugPlayers.add(playerUuid);
    else
      this.debugPlayers.remove(playerUuid);
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

    final var attenuation = (this.mode == fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode.STRICT_BLOCK) ? 100.0 : los.totalAttenuation();
    blockBoth(vPlayer, otherVPlayer, attenuation, VoiceWallManager.WallBlockReason.WALL);
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

  private boolean airDamping = true;

  @Override
  public boolean isAirDampingEnabled() {
    return this.airDamping;
  }

  @Override
  public void setAirDampingEnabled(final boolean value) {
    this.airDamping = value;
  }

  // ###############################################################
  // -------------------- VOICE MANAGE METHODS ---------------------
  // ###############################################################

  @Override
  public void processEntitySoundPacket(
    final @NotNull de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent event,
    final @NotNull VPlayer vSender,
    final @NotNull VPlayer vReceiver,
    final @NotNull VoicechatConnection receiverConn
  ) {
    final var senderUuid = vSender.getUuid();
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    final var hasFilters = filterService != null && filterService.hasActiveFilters(senderUuid);

    var totalDbLoss = 0.0;
    if (this.enable) {
      final var wallManager = vReceiver.getManager(VoiceWallManager.class);
      if (wallManager != null)
        totalDbLoss = wallManager.getTotalAttenuationDb(vSender);
    }

    final var hasAttenuation = Math.abs(totalDbLoss) > 0.001;

    // Calculate distance for air damping
    var distance = 0.0;
    final var pSender = vSender.getBukkitPlayer();
    final var pReceiver = vReceiver.getBukkitPlayer();
    if (pSender != null && pReceiver != null && pSender.getWorld().equals(pReceiver.getWorld()))
      distance = pSender.getLocation().distance(pReceiver.getLocation());

    final var hasAirDamping = this.airDamping && distance > 5.0;

    if (!hasAttenuation && !hasFilters && !hasAirDamping)
      return;

    try {
      if (this.debug)
        this.plugin.getLogger().info("Audio processing for " + senderUuid + " (attenuation=" + totalDbLoss + "dB, filters=" + hasFilters + ", dist=" + distance + "m)");

      final var packet = event.getPacket();
      final var opusDta = packet.getOpusEncodedData();
      if (opusDta == null || opusDta.length == 0)
        return;

      final var receiverUUID = receiverConn.getPlayer().getUuid();
      final var decoder = DreamVoice.getService(VoiceService.class).getDecoder(receiverUUID);
      final var encoder = DreamVoice.getService(VoiceService.class).getEncoder(receiverUUID);

      if (decoder == null || encoder == null)
        return;

      var pcm = decoder.decode(opusDta);
      if (pcm == null || pcm.length == 0)
        return;

      if (hasFilters && filterService != null)
        pcm = filterService.applyFilters(senderUuid, pcm);

      if (hasAttenuation) {
        final var dbReduction = (float) totalDbLoss;
        final var gain = (float) Math.pow(10.0, dbReduction / 20.0);
        for (int i = 0; i < pcm.length; i++)
          pcm[i] = (short) (pcm[i] * gain);
      }

      // Air absorption high-cut filter for distant audio
      if (hasAirDamping) {
        final var alpha = Math.max(0.10f, 1.0f - (float) (distance - 5.0) * 0.038f);
        var smooth = (float) pcm[0];
        for (int i = 0; i < pcm.length; i++) {
          smooth = smooth + alpha * (pcm[i] - smooth);
          pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(smooth)));
        }
      }

      final var newOpus = encoder.encode(pcm);

      final var newPacket = packet.entitySoundPacketBuilder()
        .channelId(packet.getChannelId())
        .entityUuid(packet.getEntityUuid())
        .distance(packet.getDistance())
        .whispering(packet.isWhispering())
        .opusEncodedData(newOpus)
        .category(packet.getCategory())
        .build();

      event.cancel();
      this.api.sendEntitySoundPacketTo(receiverConn, newPacket);

    } catch (Exception e) {
      this.plugin.getLogger().warning("Audio processing error : " + e.getMessage());
    }
  }


  // ###############################################################
  // ---------------------- LISTENER METHODS -----------------------
  // ###############################################################

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    this.debugPlayers.remove(event.getPlayer().getUniqueId());
    final var vPlayer = this.playerService.getPlayer(event.getPlayer());
    if (vPlayer != null)
      invalidateCacheForPlayer(vPlayer);
  }

  private void renderVisualDebug() {
    for (final var viewerUuid : this.debugPlayers) {
      final var viewer = Bukkit.getPlayer(viewerUuid);
      if (viewer == null || !viewer.isOnline()) {
        this.debugPlayers.remove(viewerUuid);
        continue;
      }

      // Find closest other player
      var closestPlayer = (org.bukkit.entity.Player) null;
      var closestDist = Double.MAX_VALUE;

      for (final var target : Bukkit.getOnlinePlayers()) {
        if (target.getUniqueId().equals(viewerUuid) || !target.getWorld().equals(viewer.getWorld()))
          continue;

        final var d = viewer.getLocation().distance(target.getLocation());
        if (d < closestDist && d <= 32.0) {
          closestDist = d;
          closestPlayer = target;
        }
      }

      if (closestPlayer == null)
        continue;

      final var result = VoiceRayCast.check(viewer, closestPlayer);

      final org.bukkit.Color color;
      final net.kyori.adventure.text.Component statusText;

      switch (result.type()) {
        case DIRECT -> {
          color = org.bukkit.Color.fromRGB(40, 255, 40);
          statusText = net.kyori.adventure.text.Component.text("DIRECT (Air Libre)", net.kyori.adventure.text.format.NamedTextColor.GREEN);
        }
        case DIFFRACTED -> {
          color = org.bukkit.Color.fromRGB(255, 180, 0);
          statusText = net.kyori.adventure.text.Component.text("CONTOURNÉ (Porte/Angle)", net.kyori.adventure.text.format.NamedTextColor.GOLD);
        }
        case WALL_ATTENUATED -> {
          color = org.bukkit.Color.fromRGB(255, 60, 60);
          statusText = net.kyori.adventure.text.Component.text("ATTÉNUÉ (Mur)", net.kyori.adventure.text.format.NamedTextColor.RED);
        }
        case WALL_BLOCKED -> {
          color = org.bukkit.Color.fromRGB(180, 0, 0);
          statusText = net.kyori.adventure.text.Component.text("BLOQUÉ (Coupé 100%)", net.kyori.adventure.text.format.NamedTextColor.DARK_RED);
        }
        default -> {
          color = org.bukkit.Color.fromRGB(200, 200, 200);
          statusText = net.kyori.adventure.text.Component.text("INCONNU", net.kyori.adventure.text.format.NamedTextColor.GRAY);
        }
      }

      // Draw particle trail along path waypoints
      spawnParticleTrail(viewer, result.waypoints(), color);

      // Send Action Bar feedback
      viewer.sendActionBar(
        net.kyori.adventure.text.Component.text("[VoiceWall Debug] ", net.kyori.adventure.text.format.NamedTextColor.GRAY)
          .append(net.kyori.adventure.text.Component.text("Cible: ", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY))
          .append(net.kyori.adventure.text.Component.text(closestPlayer.getName(), net.kyori.adventure.text.format.NamedTextColor.AQUA))
          .append(net.kyori.adventure.text.Component.text(" | ", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY))
          .append(statusText)
          .append(net.kyori.adventure.text.Component.text(String.format(" (-%.1f dB, %.1fm)", result.totalAttenuation(), closestDist), net.kyori.adventure.text.format.NamedTextColor.YELLOW))
      );
    }
  }

  private void spawnParticleTrail(
    final @NotNull org.bukkit.entity.Player viewer,
    final @NotNull java.util.List<org.bukkit.Location> waypoints,
    final @NotNull org.bukkit.Color color
  ) {
    if (waypoints.size() < 2)
      return;

    final var dust = new org.bukkit.Particle.DustOptions(color, 1.2f);

    for (int i = 0; i < waypoints.size() - 1; i++) {
      final var start = waypoints.get(i);
      final var end = waypoints.get(i + 1);

      final var diff = end.toVector().subtract(start.toVector());
      final var length = diff.length();
      if (length < 0.1)
        continue;

      final var step = 0.5;
      final var steps = (int) Math.ceil(length / step);
      final var inc = diff.clone().multiply(1.0 / steps);

      var current = start.clone();
      for (int s = 0; s <= steps; s++) {
        viewer.spawnParticle(org.bukkit.Particle.DUST, current.getX(), current.getY(), current.getZ(), 1, 0.0, 0.0, 0.0, 0.0, dust);
        current.add(inc);
      }
    }
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


