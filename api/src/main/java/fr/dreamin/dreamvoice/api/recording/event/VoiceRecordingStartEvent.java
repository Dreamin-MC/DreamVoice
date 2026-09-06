package fr.dreamin.dreamvoice.api.recording.event;

import fr.dreamin.dreamapi.api.event.ToolsCancelEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired before a live voice recording session starts. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class VoiceRecordingStartEvent extends ToolsCancelEvent {

  private final @NotNull UUID speakerUuid;

}
