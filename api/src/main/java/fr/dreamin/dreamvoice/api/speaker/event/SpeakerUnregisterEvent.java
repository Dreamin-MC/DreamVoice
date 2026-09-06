package fr.dreamin.dreamvoice.api.speaker.event;

import fr.dreamin.dreamapi.api.event.ToolsEvent;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a {@link Speaker} is removed from the service registry.
 */
@Getter
@RequiredArgsConstructor
public final class SpeakerUnregisterEvent extends ToolsEvent {

  private final @NotNull Speaker speaker;

}
