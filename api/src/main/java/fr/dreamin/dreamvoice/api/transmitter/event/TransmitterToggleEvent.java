package fr.dreamin.dreamvoice.api.transmitter.event;

import fr.dreamin.dreamapi.api.event.ToolsCancelEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when transmitter mode is toggled for a player. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class TransmitterToggleEvent extends ToolsCancelEvent {

  private final @NotNull UUID playerUuid;
  private final boolean enabled;

}
