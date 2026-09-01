package fr.dreamin.dreamvoice.core.utils.raycast;

import fr.dreamin.dreamvoice.api.codex.model.Codex;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode;
import fr.dreamin.dreamvoice.core.DreamVoice;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * Acoustic raycasting engine computing sound propagation, wall attenuation, and diffraction bypasses.
 */
public final class VoiceRayCast {

  // ###############################################################
  // ----------------------- STATIC FIELDS -------------------------
  // ###############################################################

  public static final double[] TARGET_HEIGHTS = { 1.62, 1.00, 0.20 };
  private static boolean codexServiceMissingLoggedForCheck;
  private static boolean codexServiceMissingLoggedForPolicy;

  public enum PropagationType {
    DIRECT,
    DIFFRACTED,
    WALL_ATTENUATED,
    WALL_BLOCKED
  }

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  public static RaycastResult check(final @NotNull Player speaker, final @NotNull Player listener) {
    if (!speaker.getWorld().equals(listener.getWorld()))
      return RaycastResult.CLEAR;

    return checkLocations(speaker.getEyeLocation(), listener.getEyeLocation());
  }

  public static RaycastResult check(final @NotNull Location from, final @NotNull Player listener) {
    if (from.getWorld() == null || !from.getWorld().equals(listener.getWorld()))
      return RaycastResult.CLEAR;

    return checkLocations(from, listener.getEyeLocation());
  }

  public static RaycastResult check(final @NotNull Location from, final @NotNull Location to) {
    if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld()))
      return RaycastResult.CLEAR;

    return checkLocations(from, to);
  }

  public static boolean hasLineOfSight(final @NotNull Location from, final @NotNull Player listener) {
    return check(from, listener).lineOfSight();
  }

  public static boolean hitsPlayerAtAnyHeight(final @NotNull Player speaker, final @NotNull Player listener, final double distance) {
    return hitsTargetAtAnyHeight(speaker.getLocation(), listener.getLocation(), speaker.getWorld(), distance);
  }

  public static boolean hitsTargetAtAnyHeight(final @NotNull Location startLoc, final @NotNull Location targetLoc, final @NotNull World world, final double distance) {
    for (final var h : TARGET_HEIGHTS) {
      final var start = startLoc.clone().add(0, h, 0);
      final var target = targetLoc.clone().add(0, h, 0);
      final var dir = target.toVector().subtract(start.toVector());
      final var len = dir.length();
      if (len < 0.05)
        return true;

      final var direction = dir.clone().normalize();
      final var rayDistance = Math.min(distance, len);

      final var fullBlockHit = world.rayTraceBlocks(start, direction, rayDistance, FluidCollisionMode.NEVER, true);
      if (fullBlockHit == null || fullBlockHit.getHitBlock() == null)
        return true;
    }

    return false;
  }

  // ###############################################################
  // ----------------------- PRIVATE METHODS -----------------------
  // ###############################################################

  private static @NotNull RaycastResult checkLocations(final @NotNull Location from, final @NotNull Location to) {
    final var world = from.getWorld();
    if (world == null)
      return RaycastResult.CLEAR;

    final var directDist = from.distance(to);

    // 1. Direct Line of Sight check
    if (hitsTargetAtAnyHeight(from, to, world, directDist))
      return new RaycastResult(true, 0.0, PropagationType.DIRECT, List.of(from, to));

    final var codexService = DreamVoice.getService(CodexService.class);
    final Codex codex;
    if (codexService == null) {
      if (!codexServiceMissingLoggedForCheck) {
        codexServiceMissingLoggedForCheck = true;
        logMissingCodexService("raycast check");
      }
      codex = null;
    } else
      codex = codexService.getConfig();

    final var voiceWall = codex != null ? codex.getVoiceWall() : null;
    final var diffraction = voiceWall != null ? voiceWall.getDiffractionConfig() : Codex.DiffractionConfig.defaults();
    final var mode = voiceWall != null ? voiceWall.getEffectiveMode() : VoiceWallMode.REALISTIC;

    // 2. Diffraction / Acoustic Bypass (Tier 1: Multi-Ray Lateral/Vertical)
    if (diffraction.enabled()) {
      final var tier1 = checkLocalDiffraction(from, to, world, directDist, diffraction);
      if (tier1 != null)
        return tier1;

      // Tier 2: Bounded Air Pathfinding (Open Doors, Windows, Corridor Apertures)
      final var path = AcousticPathFinder.findAirPath(from, to, diffraction.maxPathDistance());
      if (path.found()) {
        final var extraDist = Math.max(0.0, path.pathLength() - directDist);
        final var pathAttenuation = Math.min(95.0, diffraction.diffractionLossDb() + (extraDist * diffraction.lossPerMeter()));
        return new RaycastResult(false, pathAttenuation, PropagationType.DIFFRACTED, path.waypoints());
      }
    }

    // 3. Solid Obstacle / Wall Attenuation
    if (mode == VoiceWallMode.STRICT_BLOCK)
      return new RaycastResult(false, 100.0, PropagationType.WALL_BLOCKED, List.of(from, to));

    final var wallAttenuation = computeTotalAttenuation(from, to);
    final var resultType = wallAttenuation >= 99.0 ? PropagationType.WALL_BLOCKED : PropagationType.WALL_ATTENUATED;
    return new RaycastResult(false, wallAttenuation, resultType, List.of(from, to));
  }

  private static @Nullable RaycastResult checkLocalDiffraction(
    final @NotNull Location from,
    final @NotNull Location to,
    final @NotNull World world,
    final double directDist,
    final @NotNull Codex.DiffractionConfig config
  ) {
    final var dir = to.toVector().subtract(from.toVector()).normalize();
    var up = new Vector(0, 1, 0);
    if (Math.abs(dir.getY()) > 0.95)
      up = new Vector(1, 0, 0);

    final var right = dir.clone().crossProduct(up).normalize();

    final var mid = from.clone().add(dir.clone().multiply(directDist * 0.5));
    final var offsets = new Vector[] {
      right.clone().multiply(config.maxBypassWidth()),
      right.clone().multiply(-config.maxBypassWidth()),
      up.clone().multiply(config.maxBypassHeight()),
      right.clone().multiply(config.maxBypassWidth() * 0.5).add(up.clone().multiply(config.maxBypassHeight() * 0.7)),
      right.clone().multiply(-config.maxBypassWidth() * 0.5).add(up.clone().multiply(config.maxBypassHeight() * 0.7))
    };

    for (final var offset : offsets) {
      final var testPoint = mid.clone().add(offset);
      final var block = testPoint.getBlock();
      if (!AcousticPathFinder.isAcousticallyPassable(world, block.getX(), block.getY(), block.getZ()))
        continue;

      final var d1 = from.distance(testPoint);
      final var d2 = testPoint.distance(to);

      if (hitsTargetAtAnyHeight(from, testPoint, world, d1) && hitsTargetAtAnyHeight(testPoint, to, world, d2)) {
        final var totalDist = d1 + d2;
        final var extraDist = Math.max(0.0, totalDist - directDist);
        final var loss = Math.min(95.0, config.diffractionLossDb() + (extraDist * config.lossPerMeter()));
        return new RaycastResult(false, loss, PropagationType.DIFFRACTED, List.of(from, testPoint, to));
      }
    }

    return null;
  }

  private static double computeTotalAttenuation(final @NotNull Location from, final @NotNull Location to) {
    final var world = from.getWorld();
    if (world == null)
      return 0.0;

    final var delta = to.toVector().subtract(from.toVector());
    final var totalDistance = delta.length();
    if (totalDistance < 0.05)
      return 0.0;

    final var dir = delta.clone().normalize();
    final var policy = getSoundPolicy();

    var totalDbLoss = 0.0;

    try {
      final var maxDistance = (int) Math.ceil(totalDistance);
      final var iterator = new BlockIterator(world, from.toVector(), dir, 0.0, maxDistance);

      while (iterator.hasNext()) {
        final var block = iterator.next();
        final var type = block.getType();

        if (!type.isAir()) {
          totalDbLoss += policy.getAttenuationDb(type);
          if (totalDbLoss >= 100.0)
            return 100.0;
        }
      }
    } catch (Exception exception) {
      DreamVoice.getInstance().getLogger().warning("Failed to compute voice wall attenuation from " + from + " to " + to + ": " + exception.getMessage());
    }

    return Math.min(100.0, totalDbLoss);
  }

  private static SoundMaterialPolicy getSoundPolicy() {
    final var codexService = DreamVoice.getService(CodexService.class);
    if (codexService == null) {
      if (!codexServiceMissingLoggedForPolicy) {
        codexServiceMissingLoggedForPolicy = true;
        logMissingCodexService("sound material policy");
      }
      return SoundMaterialPolicy.defaults();
    }

    final Codex codex = codexService.getConfig();
    if (codex.getVoiceWall() == null)
      return SoundMaterialPolicy.defaults();

    return new SoundMaterialPolicy(codex.getVoiceWall());
  }

  private static void logMissingCodexService(final @NotNull String context) {
    final var plugin = DreamVoice.getInstance();
    if (plugin != null)
      plugin.getLogger().warning("CodexService is unavailable. Falling back to defaults for " + context + ".");
  }

  // ###############################################################
  // ------------------- SOUND MATERIAL POLICY ---------------------
  // ###############################################################

  private record SoundMaterialPolicy(Codex.VoiceWall config) {

    static SoundMaterialPolicy defaults() {
      return new SoundMaterialPolicy(null);
    }

    public double getAttenuationDb(final @NotNull Material material) {
      if (this.config == null)
        return 15.0;
      return this.config.getAttenuationDb(material.name());
    }

  }

  // ###############################################################
  // ----------------------- RAYCAST RESULT ------------------------
  // ###############################################################

  public record RaycastResult(
    boolean lineOfSight,
    double totalAttenuation,
    @NotNull PropagationType type,
    @NotNull List<Location> waypoints
  ) {

    public static final RaycastResult CLEAR = new RaycastResult(true, 0.0, PropagationType.DIRECT, Collections.emptyList());

    public RaycastResult(boolean lineOfSight, double totalAttenuation) {
      this(lineOfSight, totalAttenuation, lineOfSight ? PropagationType.DIRECT : PropagationType.WALL_ATTENUATED, Collections.emptyList());
    }

    public boolean isBlocked() {
      return this.type == PropagationType.WALL_BLOCKED || this.totalAttenuation >= 99.0;
    }

    public boolean isDiffracted() {
      return this.type == PropagationType.DIFFRACTED;
    }

    @Override
    public @NonNull String toString() {
      return String.format("RaycastResult{%s, attenuation=%.1fdB, points=%d}", this.type, this.totalAttenuation, this.waypoints.size());
    }

  }

}
