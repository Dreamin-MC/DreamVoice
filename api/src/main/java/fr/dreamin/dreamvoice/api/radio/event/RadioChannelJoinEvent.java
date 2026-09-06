package fr.dreamin.dreamvoice.api.radio.event;

import fr.dreamin.dreamapi.api.event.ToolsCancelEvent;
import fr.dreamin.dreamvoice.api.radio.model.RadioChannel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a player tunes into a {@link RadioChannel}. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class RadioChannelJoinEvent extends ToolsCancelEvent {

  private final @NotNull RadioChannel channel;
  private final @NotNull UUID playerUuid;

}
