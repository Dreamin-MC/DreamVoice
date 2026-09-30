package fr.dreamin.dreamvoice.api.speaker.event;

import fr.dreamin.dreamvoice.api.event.VoiceCancelEvent;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired before audio playback (recording, sound file, or URL) begins on a {@link Speaker}. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class SpeakerPlaySoundEvent extends VoiceCancelEvent {

  private final @NotNull Speaker speaker;
  private final @NotNull String source;
  private final boolean loop;

}
