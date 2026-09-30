package fr.dreamin.dreamvoice.api.speaker.event;

import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired after a {@link Speaker} is unregistered and dismantled.
 */
@Getter
@RequiredArgsConstructor
public final class SpeakerUnregisterEvent extends VoiceEvent {

  private final @NotNull Speaker speaker;

}
