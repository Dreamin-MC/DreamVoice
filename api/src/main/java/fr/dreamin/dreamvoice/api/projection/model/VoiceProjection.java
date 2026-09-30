package fr.dreamin.dreamvoice.api.projection.model;

import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Model representing a voice projection / body anchor for a player.
 * Allows remote voice emission and remote environmental hearing (e.g. security cameras, drones, intercoms).
 */
@Getter
public final class VoiceProjection {

  private final @NotNull UUID uuid;
  private final @NotNull UUID playerUuid;
  @Setter
  private @NotNull VoiceLocation anchorLocation;
  @Setter
  private @Nullable UUID anchorEntityUuid;

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

  public VoiceProjection(final @NotNull UUID playerUuid, final @NotNull VoiceLocation anchorLocation) {
    this(UUID.randomUUID(), playerUuid, anchorLocation);
  }

  public VoiceProjection(final @NotNull UUID playerUuid, final @NotNull VoiceLocation anchorLocation, final @Nullable UUID anchorEntityUuid) {
    this(UUID.randomUUID(), playerUuid, anchorLocation);
    this.anchorEntityUuid = anchorEntityUuid;
  }

  public VoiceProjection(final @NotNull UUID uuid, final @NotNull UUID playerUuid, final @NotNull VoiceLocation anchorLocation) {
    this.uuid = uuid;
    this.playerUuid = playerUuid;
    this.anchorLocation = anchorLocation;
  }

}
