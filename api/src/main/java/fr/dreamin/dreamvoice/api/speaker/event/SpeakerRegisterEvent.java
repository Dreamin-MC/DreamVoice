package fr.dreamin.dreamvoice.api.speaker.event;

import fr.dreamin.dreamapi.api.event.ToolsCancelEvent;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired before a {@link Speaker} is registered into the service registry. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class SpeakerRegisterEvent extends ToolsCancelEvent {

  private final @NotNull Speaker speaker;

}
