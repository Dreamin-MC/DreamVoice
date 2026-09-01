package fr.dreamin.dreamvoice.api.projection.model;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
public final class VoiceProjection {

  private final @NotNull UUID uuid;
  private final @NotNull UUID playerUuid;
  @Setter
  private @NotNull Location anchorLocation;
  @Setter
  private @Nullable Entity anchorEntity;

  @Setter
  private double distance = 16.0;

  @Setter
  private boolean emitVoiceAtAnchor = true;

  @Setter
  private boolean emitVoiceAtPlayer = false;

  @Setter
  private boolean hearAnchorEnvironment = true;

  @Setter
  private boolean hearPlayerEnvironment = true;

  @Setter
  private boolean applyVoiceWall = true;

  @Setter
  private @Nullable String filterId = null;

  public VoiceProjection(final @NotNull UUID playerUuid, final @NotNull Location anchorLocation) {
    this(UUID.randomUUID(), playerUuid, anchorLocation);
  }

  public VoiceProjection(final @NotNull UUID playerUuid, final @NotNull Entity anchorEntity) {
    this(UUID.randomUUID(), playerUuid, anchorEntity.getLocation());
    this.anchorEntity = anchorEntity;
  }

  public VoiceProjection(final @NotNull UUID uuid, final @NotNull UUID playerUuid, final @NotNull Location anchorLocation) {
    this.uuid = uuid;
    this.playerUuid = playerUuid;
    this.anchorLocation = anchorLocation;
  }

  public @NotNull Location getAnchorLocation() {
    if (this.anchorEntity != null && this.anchorEntity.isValid())
      return this.anchorEntity.getLocation();
    return this.anchorLocation;
  }

}
