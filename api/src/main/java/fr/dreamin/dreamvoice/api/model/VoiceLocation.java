package fr.dreamin.dreamvoice.api.model;

import org.jetbrains.annotations.NotNull;

/**
 * Platform-neutral 3D location with yaw and pitch.
 */
public record VoiceLocation(
  @NotNull String world,
  double x,
  double y,
  double z,
  float yaw,
  float pitch
) {

  public VoiceLocation(final @NotNull String world, final double x, final double y, final double z) {
    this(world, x, y, z, 0.0f, 0.0f);
  }

  public double distanceSquared(final @NotNull VoiceLocation other) {
    if (!this.world.equalsIgnoreCase(other.world()))
      return Double.MAX_VALUE;
    final var dx = this.x - other.x();
    final var dy = this.y - other.y();
    final var dz = this.z - other.z();
    return dx * dx + dy * dy + dz * dz;
  }

  public double distance(final @NotNull VoiceLocation other) {
    return Math.sqrt(distanceSquared(other));
  }

  public int getBlockX() {
    return (int) Math.floor(this.x);
  }

  public int getBlockY() {
    return (int) Math.floor(this.y);
  }

  public int getBlockZ() {
    return (int) Math.floor(this.z);
  }

  public @NotNull VoiceLocation add(final double dx, final double dy, final double dz) {
    return new VoiceLocation(this.world, this.x + dx, this.y + dy, this.z + dz, this.yaw, this.pitch);
  }

}
