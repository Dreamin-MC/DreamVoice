package fr.dreamin.dreamvoice.core.utils;

import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

public final class LocationUtils {

  private LocationUtils() {}

  public static @NotNull VoiceLocation toVoiceLocation(final @NotNull Location loc) {
    final var world = loc.getWorld();
    final var worldName = world != null ? world.getName() : "world";
    return new VoiceLocation(worldName, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
  }

  public static @NotNull Location toLocation(final @NotNull VoiceLocation loc) {
    final var world = Bukkit.getWorld(loc.world());
    return new Location(world, loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
  }
}
