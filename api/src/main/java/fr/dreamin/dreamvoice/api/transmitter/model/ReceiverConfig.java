package fr.dreamin.dreamvoice.api.transmitter.model;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter @Setter
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

  public boolean hasMaxDistance() {
    return this.maxDistance != null && this.maxDistance > 0;
  }

}

