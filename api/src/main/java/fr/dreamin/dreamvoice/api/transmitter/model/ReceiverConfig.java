package fr.dreamin.dreamvoice.api.transmitter.model;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Configuration for a point-to-point transmitter receiver.
 * Stores target player UUID and optional maximum broadcast distance.
 */
@Getter
@Setter
public final class ReceiverConfig {

  private final @NotNull UUID uuid;
  private @Nullable Double maxDistance;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public ReceiverConfig(final @NotNull UUID uuid) {
    this(uuid, null);
  }

  public ReceiverConfig(final @NotNull UUID uuid, final @Nullable Double maxDistance) {
    this.uuid = uuid;
    this.maxDistance = (maxDistance != null && maxDistance > 0) ? maxDistance : null;
  }

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  /**
   * Checks whether this receiver has a finite maximum distance limit.
   *
   * @return {@code true} if distance is limited
   */
  public boolean hasMaxDistance() {
    return this.maxDistance != null && this.maxDistance > 0;
  }

}
