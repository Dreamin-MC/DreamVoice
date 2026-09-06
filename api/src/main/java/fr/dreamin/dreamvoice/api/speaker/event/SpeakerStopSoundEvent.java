package fr.dreamin.dreamvoice.api.speaker.event;

import fr.dreamin.dreamapi.api.event.ToolsEvent;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when audio playback is stopped on a {@link Speaker}.
 */
@Getter
@RequiredArgsConstructor
public final class SpeakerStopSoundEvent extends ToolsEvent {

  private final @NotNull Speaker speaker;

}
