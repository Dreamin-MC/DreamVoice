package fr.dreamin.dreamvoice.core.storage.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

public record LocationData(
  String world,
  double x,
  double y,
  double z,
  float yaw,
  float pitch
) {

  public static @Nullable LocationData fromLocation(final @Nullable Location loc) {
    if (loc == null || loc.getWorld() == null)
      return null;
    return new LocationData(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
  }

  public @Nullable Location toLocation() {
    if (this.world == null)
      return null;
    final var w = Bukkit.getWorld(this.world);
    if (w == null)
      return null;
    return new Location(w, this.x, this.y, this.z, this.yaw, this.pitch);
  }

}
