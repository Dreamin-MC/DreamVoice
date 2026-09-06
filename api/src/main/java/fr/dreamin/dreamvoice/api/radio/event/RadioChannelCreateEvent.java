package fr.dreamin.dreamvoice.api.radio.event;

import fr.dreamin.dreamapi.api.event.ToolsCancelEvent;
import fr.dreamin.dreamvoice.api.radio.model.RadioChannel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a new {@link RadioChannel} frequency is created. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class RadioChannelCreateEvent extends ToolsCancelEvent {

  private final @NotNull RadioChannel channel;

}
