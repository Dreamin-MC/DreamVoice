package fr.dreamin.dreamvoice.api.speaker.event;

import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired after audio playback is stopped on a {@link Speaker}.
 */
@Getter
@RequiredArgsConstructor
public final class SpeakerStopSoundEvent extends VoiceEvent {

  private final @NotNull Speaker speaker;

}
