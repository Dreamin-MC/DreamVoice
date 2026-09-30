package fr.dreamin.dreamvoice.api.transmitter.event;

import fr.dreamin.dreamvoice.api.event.VoiceCancelEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when transmitter mode is toggled for a player. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class TransmitterToggleEvent extends VoiceCancelEvent {

  private final @NotNull UUID playerUuid;
  private final boolean enabled;

}
