package fr.dreamin.dreamvoice.core.config.model;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record ConfigTarget(
  @NotNull Location location,
  @Nullable Entity entity,
  @Nullable Block block
) {

  public static @NotNull ConfigTarget resolve(final @NotNull Player player) {
    final var eye = player.getEyeLocation();
    final var world = player.getWorld();

    final var entityHit = world.rayTraceEntities(eye, eye.getDirection(), 6.0, 0.8, e -> !e.equals(player));
    if (entityHit != null && entityHit.getHitEntity() != null)
      return new ConfigTarget(entityHit.getHitEntity().getLocation(), entityHit.getHitEntity(), null);

    final var blockHit = world.rayTraceBlocks(eye, eye.getDirection(), 6.0);
    if (blockHit != null && blockHit.getHitBlock() != null)
      return new ConfigTarget(blockHit.getHitPosition().toLocation(world), null, blockHit.getHitBlock());

    return new ConfigTarget(player.getLocation(), null, null);
  }

  public String describe() {
    if (entity != null) {
      entity.getName();
      return entity.getType().name() + " (" + entity.getName() + ")";
    }
    if (block != null)
      return block.getType().name() + " [" + block.getX() + ", " + block.getY() + ", " + block.getZ() + "]";
    return String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ());
  }

}
