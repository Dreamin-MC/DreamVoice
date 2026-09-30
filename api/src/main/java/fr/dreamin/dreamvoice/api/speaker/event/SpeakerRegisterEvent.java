package fr.dreamin.dreamvoice.api.speaker.event;

import fr.dreamin.dreamvoice.api.event.VoiceCancelEvent;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired before a new {@link Speaker} is registered into the system. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class SpeakerRegisterEvent extends VoiceCancelEvent {

  private final @NotNull Speaker speaker;

}
