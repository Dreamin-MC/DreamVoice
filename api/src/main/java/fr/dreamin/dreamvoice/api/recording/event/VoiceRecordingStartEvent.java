package fr.dreamin.dreamvoice.api.recording.event;

import fr.dreamin.dreamvoice.api.event.VoiceCancelEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired before a live voice recording session starts. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class VoiceRecordingStartEvent extends VoiceCancelEvent {

  private final @NotNull UUID speakerUuid;

}
