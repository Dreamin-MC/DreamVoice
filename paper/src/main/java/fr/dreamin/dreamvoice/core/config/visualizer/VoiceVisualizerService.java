package fr.dreamin.dreamvoice.core.config.visualizer;

import fr.dreamin.dreamvoice.api.broadcast.service.VoiceBroadcastService;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.api.room.model.VoiceCuboid;
import fr.dreamin.dreamvoice.api.room.service.VoiceRoomService;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.config.tool.VoiceConfigTool;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing real-time particle visualization of audio ranges (spheres, boxes, anchors).
 */
public final class VoiceVisualizerService {

  private static final int CIRCLE_POINTS = 24;
  private static final double TWO_PI = Math.PI * 2;
  private static final double MAX_RENDER_DISTANCE = 48.0;

  private static final Color COLOR_SPEAKER = Color.fromRGB(0, 220, 255);
  private static final Color COLOR_SPEAKER_LINKED = Color.fromRGB(50, 255, 120);
  private static final Color COLOR_BROADCAST = Color.fromRGB(56, 239, 125);
  private static final Color COLOR_WIRETAP = Color.fromRGB(200, 80, 255);
  private static final Color COLOR_WIRETAP_RECORDING = Color.fromRGB(255, 50, 50);
  private static final Color COLOR_ROOM = Color.fromRGB(65, 130, 255);
  private static final Color COLOR_PROJECTION = Color.fromRGB(255, 105, 180);
  private static final Color COLOR_FOCUS = Color.fromRGB(255, 200, 0);

  private final @NotNull DreamVoice plugin;
  private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Object> focusedObjects = new ConcurrentHashMap<>();
  private @Nullable BukkitTask task;

  public VoiceVisualizerService(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;
  }

  public void start() {
    if (this.task != null)
      return;

    // Run every 10 ticks (0.5 second) for smooth and lightweight particle rendering
    this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 10L, 10L);
  }

  public void stop() {
    if (this.task != null) {
      this.task.cancel();
      this.task = null;
    }
    this.enabledPlayers.clear();
    this.focusedObjects.clear();
  }

  public boolean toggle(final @NotNull UUID playerUuid) {
    if (this.enabledPlayers.contains(playerUuid)) {
      this.enabledPlayers.remove(playerUuid);
      return false;
    } else {
      this.enabledPlayers.add(playerUuid);
      return true;
    }
  }

  public boolean isEnabled(final @NotNull UUID playerUuid) {
    return this.enabledPlayers.contains(playerUuid);
  }

  public void setEnabled(final @NotNull UUID playerUuid, final boolean enabled) {
    if (enabled)
      this.enabledPlayers.add(playerUuid);
    else
      this.enabledPlayers.remove(playerUuid);
  }

  public void setFocus(final @NotNull UUID playerUuid, final @Nullable Object focus) {
    if (focus != null)
      this.focusedObjects.put(playerUuid, focus);
    else
      this.focusedObjects.remove(playerUuid);
  }

  public void clearFocus(final @NotNull UUID playerUuid) {
    this.focusedObjects.remove(playerUuid);
  }

  public void cleanup(final @NotNull UUID playerUuid) {
    this.enabledPlayers.remove(playerUuid);
    this.focusedObjects.remove(playerUuid);
  }

  private void tick() {
    for (final var player : Bukkit.getOnlinePlayers()) {
      final var uuid = player.getUniqueId();
      final boolean isHoldingItem = VoiceConfigTool.isHoldingConfigTool(player);
      final boolean isActivated = this.enabledPlayers.contains(uuid);

      // Sent ONLY to players who hold the item in hand or who have explicitly activated the visualizer
      if (!isHoldingItem && !isActivated)
        continue;

      renderForPlayer(player, this.focusedObjects.get(uuid));
    }
  }

  private void renderForPlayer(final @NotNull Player player, final @Nullable Object focus) {
    final var pLoc = player.getLocation();
    final var world = pLoc.getWorld();
    if (world == null)
      return;

    final var worldName = world.getName();

    renderSpeakers(player, pLoc, worldName, focus);
    renderBroadcasts(player, pLoc, worldName, focus);
    renderWiretaps(player, pLoc, worldName, focus);
    renderRooms(player, pLoc, worldName, focus);
    renderProjections(player, pLoc, worldName, focus);
  }

  private void renderSpeakers(
    final @NotNull Player player,
    final @NotNull Location pLoc,
    final @NotNull String worldName,
    final @Nullable Object focus
  ) {
    final var speakerService = DreamVoice.getService(VoiceSpeakerService.class);
    if (speakerService == null)
      return;

    for (final var spk : speakerService.getSpeakers()) {
      final var loc = spk.getLocation();
      if (!worldName.equalsIgnoreCase(loc.world()))
        continue;

      final double distance = spk.getDistance() != null ? spk.getDistance() : 16.0;
      final double distSq = pLoc.distanceSquared(new Location(player.getWorld(), loc.x(), loc.y(), loc.z()));
      final double maxDist = distance + MAX_RENDER_DISTANCE;

      if (distSq > maxDist * maxDist)
        continue;

      final boolean isFocused = spk.equals(focus);
      final boolean isLinked = spk.getTargetEntityUuid() != null || !spk.getAllowedSpeakers().isEmpty();
      final Color color = isFocused
        ? COLOR_FOCUS
        : (isLinked ? COLOR_SPEAKER_LINKED : COLOR_SPEAKER);

      drawWireframeSphere(player, loc.x(), loc.y(), loc.z(), distance, color, isFocused);
    }
  }

  private void renderBroadcasts(
    final @NotNull Player player,
    final @NotNull Location pLoc,
    final @NotNull String worldName,
    final @Nullable Object focus
  ) {
    final var broadcastService = DreamVoice.getService(VoiceBroadcastService.class);
    if (broadcastService == null)
      return;

    for (final var bp : broadcastService.getBroadcastPoints()) {
      final var loc = bp.getLocation();
      if (!worldName.equalsIgnoreCase(loc.world()))
        continue;

      final double radius = bp.getRadius();
      final double distSq = pLoc.distanceSquared(new Location(player.getWorld(), loc.x(), loc.y(), loc.z()));
      final double maxDist = radius + MAX_RENDER_DISTANCE;

      if (distSq > maxDist * maxDist)
        continue;

      final boolean isFocused = bp.equals(focus);
      final Color color = isFocused ? COLOR_FOCUS : COLOR_BROADCAST;

      drawWireframeSphere(player, loc.x(), loc.y(), loc.z(), radius, color, isFocused);
    }
  }

  private void renderWiretaps(
    final @NotNull Player player,
    final @NotNull Location pLoc,
    final @NotNull String worldName,
    final @Nullable Object focus
  ) {
    final var wiretapService = DreamVoice.getService(VoiceWiretapService.class);
    if (wiretapService == null)
      return;

    for (final var wt : wiretapService.getWiretaps()) {
      double cx;
      double cy;
      double cz;
      String wtWorld;

      if (wt.getTargetEntityUuid() != null) {
        final var entity = Bukkit.getEntity(wt.getTargetEntityUuid());
        if (entity != null) {
          cx = entity.getLocation().getX();
          cy = entity.getLocation().getY() + 1.0;
          cz = entity.getLocation().getZ();
          wtWorld = entity.getWorld().getName();
        } else {
          final var loc = wt.getLocation();
          cx = loc.x();
          cy = loc.y();
          cz = loc.z();
          wtWorld = loc.world();
        }
      } else {
        final var loc = wt.getLocation();
        cx = loc.x();
        cy = loc.y();
        cz = loc.z();
        wtWorld = loc.world();
      }

      if (!worldName.equalsIgnoreCase(wtWorld))
        continue;

      final double distance = wt.getDistance();
      final double distSq = pLoc.distanceSquared(new Location(player.getWorld(), cx, cy, cz));
      final double maxDist = distance + MAX_RENDER_DISTANCE;

      if (distSq > maxDist * maxDist)
        continue;

      final boolean isFocused = wt.equals(focus);
      final Color color = isFocused
        ? COLOR_FOCUS
        : (wt.isRecording() ? COLOR_WIRETAP_RECORDING : COLOR_WIRETAP);

      drawWireframeSphere(player, cx, cy, cz, distance, color, isFocused);
    }
  }

  private void renderRooms(
    final @NotNull Player player,
    final @NotNull Location pLoc,
    final @NotNull String worldName,
    final @Nullable Object focus
  ) {
    final var roomService = DreamVoice.getService(VoiceRoomService.class);
    if (roomService == null)
      return;

    for (final var room : roomService.getRooms()) {
      final boolean isFocused = room.equals(focus);
      final Color color = isFocused ? COLOR_FOCUS : COLOR_ROOM;

      for (final var cuboid : room.getCuboids()) {
        if (!worldName.equalsIgnoreCase(cuboid.world()))
          continue;

        final double centerX = (cuboid.minX() + cuboid.maxX()) / 2.0;
        final double centerY = (cuboid.minY() + cuboid.maxY()) / 2.0;
        final double centerZ = (cuboid.minZ() + cuboid.maxZ()) / 2.0;

        if (pLoc.distanceSquared(new Location(player.getWorld(), centerX, centerY, centerZ)) > 80 * 80)
          continue;

        drawCuboid(player, cuboid, color, isFocused);
      }
    }
  }

  private void renderProjections(
    final @NotNull Player player,
    final @NotNull Location pLoc,
    final @NotNull String worldName,
    final @Nullable Object focus
  ) {
    final var projectionService = DreamVoice.getService(VoiceProjectionService.class);
    if (projectionService == null)
      return;

    for (final var prj : projectionService.getProjections()) {
      final var anchor = prj.getAnchorLocation();
      if (!worldName.equalsIgnoreCase(anchor.world()))
        continue;

      final double distance = prj.getDistance();
      final double distSq = pLoc.distanceSquared(new Location(player.getWorld(), anchor.x(), anchor.y(), anchor.z()));
      final double maxDist = distance + MAX_RENDER_DISTANCE;

      if (distSq > maxDist * maxDist)
        continue;

      final boolean isFocused = prj.equals(focus);
      final Color color = isFocused ? COLOR_FOCUS : COLOR_PROJECTION;

      drawWireframeSphere(player, anchor.x(), anchor.y(), anchor.z(), distance, color, isFocused);

      // Line between target player and anchor
      final var owner = Bukkit.getPlayer(prj.getPlayerUuid());
      if (owner != null && owner.isOnline() && owner.getWorld().getName().equalsIgnoreCase(worldName))
        drawLine(player, owner.getLocation(), new Location(player.getWorld(), anchor.x(), anchor.y(), anchor.z()), color);
    }
  }

  private void drawWireframeSphere(
    final @NotNull Player player,
    final double cx,
    final double cy,
    final double cz,
    final double radius,
    final @NotNull Color color,
    final boolean highlight
  ) {
    final var dust = new Particle.DustOptions(color, highlight ? 1.4f : 0.85f);

    // 1. Horizontal circle (XZ plane)
    for (int i = 0; i < CIRCLE_POINTS; i++) {
      final double angle = i * (TWO_PI / CIRCLE_POINTS);
      final double x = cx + radius * Math.cos(angle);
      final double z = cz + radius * Math.sin(angle);
      player.spawnParticle(Particle.DUST, x, cy, z, 1, 0, 0, 0, 0, dust);
    }

    // 2. Vertical circle 1 (XY plane)
    for (int i = 0; i < CIRCLE_POINTS; i++) {
      final double angle = i * (TWO_PI / CIRCLE_POINTS);
      final double x = cx + radius * Math.cos(angle);
      final double y = cy + radius * Math.sin(angle);
      player.spawnParticle(Particle.DUST, x, y, cz, 1, 0, 0, 0, 0, dust);
    }

    // 3. Vertical circle 2 (YZ plane)
    for (int i = 0; i < CIRCLE_POINTS; i++) {
      final double angle = i * (TWO_PI / CIRCLE_POINTS);
      final double y = cy + radius * Math.cos(angle);
      final double z = cz + radius * Math.sin(angle);
      player.spawnParticle(Particle.DUST, cx, y, z, 1, 0, 0, 0, 0, dust);
    }

    // Center indicator
    if (highlight)
      player.spawnParticle(Particle.END_ROD, cx, cy, cz, 1, 0, 0, 0, 0.01);
    else
      player.spawnParticle(Particle.DUST, cx, cy, cz, 1, 0, 0, 0, 0, dust);
  }

  private void drawCuboid(
    final @NotNull Player player,
    final @NotNull VoiceCuboid cuboid,
    final @NotNull Color color,
    final boolean highlight
  ) {
    final var dust = new Particle.DustOptions(color, highlight ? 1.3f : 0.8f);

    final double minX = cuboid.minX();
    final double minY = cuboid.minY();
    final double minZ = cuboid.minZ();
    final double maxX = cuboid.maxX();
    final double maxY = cuboid.maxY();
    final double maxZ = cuboid.maxZ();

    // 4 edges along X
    drawEdgeX(player, minX, maxX, minY, minZ, dust);
    drawEdgeX(player, minX, maxX, maxY, minZ, dust);
    drawEdgeX(player, minX, maxX, minY, maxZ, dust);
    drawEdgeX(player, minX, maxX, maxY, maxZ, dust);

    // 4 edges along Y
    drawEdgeY(player, minX, minY, maxY, minZ, dust);
    drawEdgeY(player, maxX, minY, maxY, minZ, dust);
    drawEdgeY(player, minX, minY, maxY, maxZ, dust);
    drawEdgeY(player, maxX, minY, maxY, maxZ, dust);

    // 4 edges along Z
    drawEdgeZ(player, minX, minY, minZ, maxZ, dust);
    drawEdgeZ(player, maxX, minY, minZ, maxZ, dust);
    drawEdgeZ(player, minX, maxY, minZ, maxZ, dust);
    drawEdgeZ(player, maxX, maxY, minZ, maxZ, dust);
  }

  private void drawEdgeX(final @NotNull Player player, final double minX, final double maxX, final double y, final double z, final @NotNull Particle.DustOptions dust) {
    for (double x = minX; x <= maxX; x += 1.0)
      player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
  }

  private void drawEdgeY(final @NotNull Player player, final double x, final double minY, final double maxY, final double z, final @NotNull Particle.DustOptions dust) {
    for (double y = minY; y <= maxY; y += 1.0)
      player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
  }

  private void drawEdgeZ(final @NotNull Player player, final double x, final double y, final double minZ, final double maxZ, final @NotNull Particle.DustOptions dust) {
    for (double z = minZ; z <= maxZ; z += 1.0)
      player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, dust);
  }

  private void drawLine(final @NotNull Player player, final @NotNull Location from, final @NotNull Location to, final @NotNull Color color) {
    final var dust = new Particle.DustOptions(color, 0.7f);
    final var dir = to.toVector().subtract(from.toVector());
    final double length = dir.length();
    if (length < 0.2)
      return;

    final double step = 0.8;
    final int steps = (int) (length / step);
    final var stepVec = dir.clone().normalize().multiply(step);
    final var current = from.clone();

    for (int i = 0; i <= steps; i++) {
      player.spawnParticle(Particle.DUST, current.getX(), current.getY(), current.getZ(), 1, 0, 0, 0, 0, dust);
      current.add(stepVec);
    }
  }
}
