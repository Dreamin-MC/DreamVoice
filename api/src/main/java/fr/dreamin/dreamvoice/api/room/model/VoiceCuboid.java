package fr.dreamin.dreamvoice.api.room.model;

import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Platform-neutral 3D axis-aligned bounding box.
 */
public record VoiceCuboid(
  @NotNull String world,
  double minX,
  double minY,
  double minZ,
  double maxX,
  double maxY,
  double maxZ
) {

  public static @NotNull VoiceCuboid of(
    final @NotNull String world,
    final double x1, final double y1, final double z1,
    final double x2, final double y2, final double z2
  ) {
    return new VoiceCuboid(
      world,
      Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
      Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
    );
  }

  public boolean contains(final @NotNull VoiceLocation location) {
    if (!this.world.equalsIgnoreCase(location.world()))
      return false;
    return location.x() >= this.minX && location.x() <= this.maxX
      && location.y() >= this.minY && location.y() <= this.maxY
      && location.z() >= this.minZ && location.z() <= this.maxZ;
  }

  public boolean contains(final double x, final double y, final double z) {
    return x >= this.minX && x <= this.maxX
      && y >= this.minY && y <= this.maxY
      && z >= this.minZ && z <= this.maxZ;
  }

}
