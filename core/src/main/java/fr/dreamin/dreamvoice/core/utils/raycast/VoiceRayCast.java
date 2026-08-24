package fr.dreamin.dreamvoice.core.utils.raycast;

import fr.dreamin.dreaminvoice.api.codex.model.Codex;
import fr.dreamin.dreaminvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.HashSet;

public final class VoiceRayCast {

  public static boolean DEBUG_MODE = false;

  public static final double[] TARGET_HEIGHTS = { 1.62, 1.00, 0.20 };
  public static final double ATTENUATION_STEP = 0.15;
  public static final double ATTENUATION_TRANSPARENT_THRESHOLD = 5.0;

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  public static RaycastResult check (final @NotNull Player speaker, final @NotNull Player listener) {
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
      if (len < 0.05) return true;

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

  private static boolean hasBlockingMaterialBefore(final @NotNull World world, final @NotNull Location start, final @NotNull Vector dir, final double dist, final @NotNull SoundMaterialPolicy policy) {
    final var blockHit = world.rayTraceBlocks(start ,dir, dist, FluidCollisionMode.NEVER, true);
    if (blockHit == null || blockHit.getHitBlock() == null) return false;

    return policy.getAttenuationDb(blockHit.getHitBlock().getType()) >= ATTENUATION_TRANSPARENT_THRESHOLD;
  }

  private static double computeTotalAttenuation(final @NotNull Location from, final @NotNull Location to) {
    final var world = from.getWorld();
    if (world == null) return 0.0;

    final var delta = to.toVector().subtract(from.toVector());
    final var totalDistance = delta.length();
    if (totalDistance < 0.05) return 0.0;

    final var dir = delta.clone().normalize();
    final var policy = getSoundPolicy();

    var totalDbLoss  = 0.0;

    final var visited = new HashSet<>();

    for (var d = 0.0; d <= totalDistance; d += ATTENUATION_STEP) {
      final var pos = from.toVector().add(dir.clone().multiply(d));
      final var block = world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
      final var type = block.getType();

      if (!type.isAir() && visited.add(block)) {
        totalDbLoss += policy.getAttenuationDb(type);
      }

    }

    return Math.min(100.0, totalDbLoss);
  }

  private static SoundMaterialPolicy getSoundPolicy() {
    final Codex codex = DreamVoice.getService(CodexService.class).getConfig();
    if (codex.getVoiceWall().soundMaterials() == null)
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
      return config.getAttenuationDb(material.name());
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
