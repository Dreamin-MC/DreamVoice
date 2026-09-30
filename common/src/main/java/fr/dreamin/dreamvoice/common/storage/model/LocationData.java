package fr.dreamin.dreamvoice.common.storage.model;

import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import org.jetbrains.annotations.Nullable;

public record LocationData(
  String world,
  double x,
  double y,
  double z,
  float yaw,
  float pitch
) {

  public static @Nullable LocationData fromVoiceLocation(final @Nullable VoiceLocation loc) {
    if (loc == null)
      return null;
    return new LocationData(loc.world(), loc.x(), loc.y(), loc.z(), loc.yaw(), loc.pitch());
  }

  public @Nullable VoiceLocation toVoiceLocation() {
    if (this.world == null)
      return null;
    return new VoiceLocation(this.world, this.x, this.y, this.z, this.yaw, this.pitch);
  }

}
