package fr.dreamin.dreamvoice.common.room.data;

import fr.dreamin.dreamvoice.api.room.model.VoiceCuboid;
import fr.dreamin.dreamvoice.common.storage.model.LocationData;
import org.jetbrains.annotations.Nullable;

/**
 * Storage representation of a Cuboid boundary.
 */
public record CuboidData(
  LocationData locA,
  LocationData locB
) {

  public @Nullable VoiceCuboid toVoiceCuboid() {
    if (this.locA == null || this.locB == null || this.locA.world() == null)
      return null;
    return VoiceCuboid.of(
      this.locA.world(),
      this.locA.x(), this.locA.y(), this.locA.z(),
      this.locB.x(), this.locB.y(), this.locB.z()
    );
  }

  public static @Nullable CuboidData fromVoiceCuboid(final @Nullable VoiceCuboid cuboid) {
    if (cuboid == null)
      return null;
    return new CuboidData(
      new LocationData(cuboid.world(), cuboid.minX(), cuboid.minY(), cuboid.minZ(), 0f, 0f),
      new LocationData(cuboid.world(), cuboid.maxX(), cuboid.maxY(), cuboid.maxZ(), 0f, 0f)
    );
  }

}
