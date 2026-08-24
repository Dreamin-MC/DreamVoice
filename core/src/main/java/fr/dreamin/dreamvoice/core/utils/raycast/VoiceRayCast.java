package fr.dreamin.dreamvoice.core.utils.raycast;

import fr.dreamin.dreamvoice.api.codex.model.Codex;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

public final class VoiceRayCast {

  public static final double[] TARGET_HEIGHTS = { 1.62, 1.00, 0.20 };
  public static final double ATTENUATION_TRANSPARENT_THRESHOLD = 5.0;

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  public static RaycastResult check(final @NotNull Player speaker, final @NotNull Player listener) {
    if (!speaker.getWorld().equals(listener.getWorld()))
      return RaycastResult.CLEAR;

    final var from = speaker.getEyeLocation();
    final var maxDistance = from.distance(listener.getEyeLocation());

    if (hitsPlayerAtAnyHeight(speaker, listener, maxDistance))
      return RaycastResult.CLEAR;

    final var totalAttenuation = computeTotalAttenuation(from, listener.getEyeLocation());
    return new RaycastResult(false, totalAttenuation);
  }

  public static boolean hasLineOfSight(final @NotNull Player speaker, final @NotNull Player listener) {
    return check(speaker, listener).lineOfSight();
  }

  public static boolean hitsPlayerAtAnyHeight(final @NotNull Player speaker, final @NotNull Player listener, final double distance) {
    final var world = speaker.getWorld();
    final var policy = getSoundPolicy();

    for (final var h : TARGET_HEIGHTS) {
      final var start = speaker.getLocation().clone().add(0, h, 0);
      final var targetLoc = listener.getLocation().clone().add(0, h, 0);
      final var dir = targetLoc.toVector().subtract(start.toVector());
      final var len = dir.length();
      if (len < 0.05)
        return true;

      final var direction = dir.clone().normalize();
      final var rayDistance = Math.min(distance, len);

      final var fullBlockHit = world.rayTraceBlocks(start, direction, rayDistance, FluidCollisionMode.NEVER, true);
      if (fullBlockHit == null || fullBlockHit.getHitBlock() == null)
        return true;

      final var blockingMat = policy.getAttenuationDb(fullBlockHit.getHitBlock().getType());
      if (blockingMat < ATTENUATION_TRANSPARENT_THRESHOLD)
        continue;
    }

    return false;
  }

  // ###############################################################
  // ----------------------- PRIVATE METHODS -----------------------
  // ###############################################################

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
    } catch (Exception ignored) {
    }

    return Math.min(100.0, totalDbLoss);
  }


  private static SoundMaterialPolicy getSoundPolicy() {
    final Codex codex = DreamVoice.getService(CodexService.class).getConfig();
    if (codex.getVoiceWall() == null || codex.getVoiceWall().soundMaterials() == null)
      return SoundMaterialPolicy.defaults();

    return new SoundMaterialPolicy(codex.getVoiceWall().soundMaterials());
  }

  // ###############################################################
  // ------------------- SOUND MATERIAL POLICY ---------------------
  // ###############################################################

  private record SoundMaterialPolicy(Codex.SoundMaterials config) {

    static SoundMaterialPolicy defaults() {
      return new SoundMaterialPolicy(new Codex.SoundMaterials(null, 0.0));
    }

    public double getAttenuationDb(final @NotNull Material material) {
      return this.config.getAttenuationDb(material.name());
    }

  }

  // ###############################################################
  // ----------------------- RAYCAST RESULT ------------------------
  // ###############################################################

  public record RaycastResult(boolean lineOfSight, double totalAttenuation) {

    public static final RaycastResult CLEAR = new RaycastResult(true, 0.0);

    // ###############################################################
    // ----------------------- PUBLIC METHODS ------------------------
    // ###############################################################

    public boolean isBlocked() {
      return !this.lineOfSight;
    }

    // ###############################################################
    // -------------------------- METHODS ----------------------------
    // ###############################################################

    @Override
    public @NonNull String toString() {
      return this.lineOfSight
        ? "RaycastResult{CLEAR, attenuation=0.0%}"
        : String.format("RaycastResult{BLOCKED, total_attenuation=%.1f%%}", this.totalAttenuation);
    }

  }

}

