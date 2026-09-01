package fr.dreamin.dreamvoice.core.wall.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import fr.dreamin.dreamapi.core.time.Tick;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.player.manager.VoiceWallManager;
import fr.dreamin.dreamvoice.core.utils.raycast.VoiceRayCast;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link VoiceWallService} managing real-time acoustic soundproofing,
 * diffraction through apertures, air absorption damping, and visual particle diagnostics.
 */
public final class VoiceWallServiceImpl extends Tick implements VoiceWallService, Listener {

  // ###############################################################
  // ----------------------- STATIC FIELDS -------------------------
  // ###############################################################

  private static final double DEFAULT_MOVEMENT_THRESHOLD = 0.5;
  private static final int DEFAULT_CACHE_CLEANUP_INTERVAL = 200; // ticks
  private static final int DEFAULT_CHECK_INTERVAL = 2; // ticks (10 times/sec)
  private static final int DEBUG_RENDER_INTERVAL = 4; // ticks
  private static final double MAX_DEBUG_DISTANCE = 32.0;

  private static final Color COLOR_DIRECT = Color.fromRGB(40, 255, 40);
  private static final Color COLOR_DIFFRACTED = Color.fromRGB(255, 180, 0);
  private static final Color COLOR_ATTENUATED = Color.fromRGB(255, 60, 60);
  private static final Color COLOR_BLOCKED = Color.fromRGB(180, 0, 0);
  private static final Color COLOR_UNKNOWN = Color.fromRGB(200, 200, 200);

  // ###############################################################
  // --------------------- INSTANCE FIELDS -------------------------
  // ###############################################################

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;
  private final @NotNull PlayerService playerService;

  private boolean enable = false;
  private boolean enableStats = false;
  private boolean debug = false;
  private boolean airDamping = true;
  private @NotNull VoiceWallMode mode = VoiceWallMode.REALISTIC;

  private final @NotNull Set<UUID> debugPlayers = ConcurrentHashMap.newKeySet();
  private final @NotNull Map<String, CachedLineOfSight> lineOfSightCache = new ConcurrentHashMap<>();

  private boolean codexServiceMissingLogged = false;
  private boolean voiceServiceMissingLogged = false;

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
  // ------------------- LIFECYCLE & TICK METHODS ------------------
  // ###############################################################

  @Override
  public void tick() {
    super.tick();

    if (this.enable) {
      if (getActualTick() % DEFAULT_CACHE_CLEANUP_INTERVAL == 0)
        cleanupCache();

      if (getActualTick() % DEFAULT_CHECK_INTERVAL == 0)
        processVoiceConnections();
    }

    if (!this.debugPlayers.isEmpty() && getActualTick() % DEBUG_RENDER_INTERVAL == 0)
      renderVisualDebug();
  }

  // ###############################################################
  // ------------------- PUBLIC SERVICE METHODS --------------------
  // ###############################################################

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
  public boolean toggleDebugPlayer(final @NotNull Player player) {
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
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    final var hasFilters = filterService != null && filterService.hasActiveFilters(senderUuid);

    var totalDbLoss = 0.0;
    if (this.enable) {
      final var wallManager = vReceiver.getManager(VoiceWallManager.class);
      if (wallManager != null)
        totalDbLoss = wallManager.getTotalAttenuationDb(vSender);
    }

    final var hasAttenuation = Math.abs(totalDbLoss) > 0.001;
    final var distance = calculateDistance(vSender, vReceiver);
    final var hasAirDamping = this.airDamping && distance > 5.0;

    if (!hasAttenuation && !hasFilters && !hasAirDamping)
      return;

    try {
      if (this.debug)
        this.plugin.getLogger().info("Audio processing for " + senderUuid + " (attenuation=" + totalDbLoss + "dB, filters=" + hasFilters + ", dist=" + distance + "m)");

      final var packet = event.getPacket();
      final var opusData = packet.getOpusEncodedData();
      if (opusData == null || opusData.length == 0)
        return;

      final var voiceService = DreamVoice.getService(VoiceService.class);
      if (voiceService == null) {
        logVoiceServiceMissingOnce();
        return;
      }

      final var receiverUUID = receiverConn.getPlayer().getUuid();
      final var decoder = voiceService.getDecoder(receiverUUID);
      final var encoder = voiceService.getEncoder(receiverUUID);
      if (decoder == null || encoder == null)
        return;

      var pcm = decoder.decode(opusData);
      if (pcm == null || pcm.length == 0)
        return;

      pcm = applyDspGainAndFilters(pcm, senderUuid, filterService, hasFilters, hasAttenuation, totalDbLoss, hasAirDamping, distance);

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
  // ------------------- PRIVATE HELPER METHODS --------------------
  // ###############################################################

  private double calculateDistance(final @NotNull VPlayer vSender, final @NotNull VPlayer vReceiver) {
    final var pSender = vSender.getBukkitPlayer();
    final var pReceiver = vReceiver.getBukkitPlayer();
    if (pSender != null && pReceiver != null && pSender.getWorld().equals(pReceiver.getWorld()))
      return pSender.getLocation().distance(pReceiver.getLocation());
    return 0.0;
  }

  private short[] applyDspGainAndFilters(
    short[] pcm,
    final @NotNull UUID senderUuid,
    final @Nullable VoiceFilterService filterService,
    final boolean hasFilters,
    final boolean hasAttenuation,
    final double totalDbLoss,
    final boolean hasAirDamping,
    final double distance
  ) {
    if (hasFilters && filterService != null)
      pcm = filterService.applyFilters(senderUuid, pcm);

    if (hasAttenuation) {
      final var gain = (float) Math.pow(10.0, totalDbLoss / 20.0);
      for (int i = 0; i < pcm.length; i++)
        pcm[i] = (short) (pcm[i] * gain);
    }

    if (hasAirDamping)
      applyAirDamping(pcm, distance);

    return pcm;
  }

  private void applyAirDamping(final short[] pcm, final double distance) {
    final var alpha = Math.max(0.10f, 1.0f - (float) (distance - 5.0) * 0.038f);
    var smooth = (float) pcm[0];
    for (int i = 0; i < pcm.length; i++) {
      smooth = smooth + alpha * (pcm[i] - smooth);
      pcm[i] = (short) Math.clamp(Math.round(smooth), Short.MIN_VALUE, Short.MAX_VALUE);
    }
  }

  private void logVoiceServiceMissingOnce() {
    if (!this.voiceServiceMissingLogged) {
      this.voiceServiceMissingLogged = true;
      this.plugin.getLogger().warning("VoiceService is unavailable. VoiceWall packet processing is skipped.");
    }
  }

  private void processVoiceConnections() {
    final var players = new ArrayList<>(this.playerService.getPlayers());

    for (final var vPlayer : players) {
      if (isPlayerInvalid(vPlayer))
        continue;

      final var manager = vPlayer.getManager(VoiceWallManager.class);
      if (manager == null)
        continue;

      if (manager.hasMovedSignificantly(DEFAULT_MOVEMENT_THRESHOLD)) {
        manager.updatePosition();
        manager.setMoved(true);
        invalidateCacheForPlayer(vPlayer);
      } else
        manager.setMoved(false);
    }

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

    final var codexService = DreamVoice.getService(CodexService.class);
    if (codexService == null) {
      if (!this.codexServiceMissingLogged) {
        this.codexServiceMissingLogged = true;
        this.plugin.getLogger().warning("CodexService is unavailable. VoiceWall pair processing is skipped.");
      }
      return;
    }

    final var maxVoiceDistance = codexService.getConfig().getDistance();
    final var distance = pa.getLocation().distance(pb.getLocation());
    if (distance > maxVoiceDistance) {
      blockBoth(vPlayer, otherVPlayer, 0.0, VoiceWallManager.WallBlockReason.DISTANCE);
      this.lineOfSightCache.put(cacheKey, new CachedLineOfSight(false, 100.0, getActualTick()));
      return;
    }

    unblockBoth(vPlayer, otherVPlayer);

    final var los = getLineOfSightCached(vPlayer, otherVPlayer, cacheKey);
    if (los == null || los.lineOfSight())
      return;

    final var attenuation = (this.mode == VoiceWallMode.STRICT_BLOCK) ? 100.0 : los.totalAttenuation();
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

    if (vPlayer.getBukkitPlayer() == null || otherVPlayer.getBukkitPlayer() == null)
      return null;

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
    this.lineOfSightCache.entrySet().removeIf(entry -> entry.getValue().isExpired(getActualTick(), 1200));
  }

  private void renderVisualDebug() {
    for (final var viewerUuid : this.debugPlayers) {
      final var viewer = Bukkit.getPlayer(viewerUuid);
      if (viewer == null || !viewer.isOnline()) {
        this.debugPlayers.remove(viewerUuid);
        continue;
      }

      final var closestPlayer = findClosestPlayer(viewer, viewerUuid);
      if (closestPlayer == null)
        continue;

      final var result = VoiceRayCast.check(viewer, closestPlayer);
      final var feedback = resolveDebugFeedback(result);

      spawnParticleTrail(viewer, result.waypoints(), feedback.color());
      dispatchActionBarDebug(viewer, closestPlayer.getName(), feedback.statusText(), result.totalAttenuation(), viewer.getLocation().distance(closestPlayer.getLocation()));
    }
  }

  private @Nullable Player findClosestPlayer(final @NotNull Player viewer, final @NotNull UUID viewerUuid) {
    var closestPlayer = (Player) null;
    var closestDist = Double.MAX_VALUE;

    for (final var target : Bukkit.getOnlinePlayers()) {
      if (target.getUniqueId().equals(viewerUuid) || !target.getWorld().equals(viewer.getWorld()))
        continue;

      final var d = viewer.getLocation().distance(target.getLocation());
      if (d < closestDist && d <= MAX_DEBUG_DISTANCE) {
        closestDist = d;
        closestPlayer = target;
      }
    }
    return closestPlayer;
  }

  private DebugFeedback resolveDebugFeedback(final @NotNull VoiceRayCast.RaycastResult result) {
    return switch (result.type()) {
      case DIRECT -> new DebugFeedback(COLOR_DIRECT, Component.text("DIRECT (Open Air)", NamedTextColor.GREEN));
      case DIFFRACTED -> new DebugFeedback(COLOR_DIFFRACTED, Component.text("DIFFRACTED (Aperture / Corner)", NamedTextColor.GOLD));
      case WALL_ATTENUATED -> new DebugFeedback(COLOR_ATTENUATED, Component.text("ATTENUATED (Wall)", NamedTextColor.RED));
      case WALL_BLOCKED -> new DebugFeedback(COLOR_BLOCKED, Component.text("BLOCKED (100% Soundproof)", NamedTextColor.DARK_RED));
    };
  }

  private void dispatchActionBarDebug(
    final @NotNull Player viewer,
    final @NotNull String targetName,
    final @NotNull Component statusText,
    final double attenuationDb,
    final double distance
  ) {
    viewer.sendActionBar(
      Component.text("[VoiceWall Debug] ", NamedTextColor.GRAY)
        .append(Component.text("Target: ", NamedTextColor.DARK_GRAY))
        .append(Component.text(targetName, NamedTextColor.AQUA))
        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
        .append(statusText)
        .append(Component.text(String.format(" (-%.1f dB, %.1fm)", attenuationDb, distance), NamedTextColor.YELLOW))
    );
  }

  private void spawnParticleTrail(
    final @NotNull Player viewer,
    final @NotNull List<Location> waypoints,
    final @NotNull Color color
  ) {
    if (waypoints.size() < 2)
      return;

    final var dust = new Particle.DustOptions(color, 1.2f);

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

      final var current = start.clone();
      for (int s = 0; s <= steps; s++) {
        viewer.spawnParticle(Particle.DUST, current.getX(), current.getY(), current.getZ(), 1, 0.0, 0.0, 0.0, 0.0, dust);
        current.add(inc);
      }
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

  // ###############################################################
  // ------------------------ INNER RECORDS ------------------------
  // ###############################################################

  private record CachedLineOfSight(boolean lineOfSight, double totalAttenuation, long timestamp, int tickCreated) {
    public CachedLineOfSight(final boolean hasLineOfSight, final double totalAttenuation, final int tickCreated) {
      this(hasLineOfSight, totalAttenuation, System.currentTimeMillis(), tickCreated);
    }

    public boolean isExpired(final int currentTick, final int maxAge) {
      return (currentTick - this.tickCreated) > maxAge;
    }
  }

  private record DebugFeedback(Color color, Component statusText) {
  }

}
