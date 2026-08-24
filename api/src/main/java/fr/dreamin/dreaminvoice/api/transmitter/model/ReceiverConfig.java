package fr.dreamin.dreaminvoice.api.transmitter.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Getter @Setter
public final class ReceiverConfig {

  private final @NotNull UUID uuid;
  private double maxDistance;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public ReceiverConfig(@NotNull UUID uuid, double maxDistance) {
    this.uuid = uuid;
    this.maxDistance = maxDistance;
  }

}
