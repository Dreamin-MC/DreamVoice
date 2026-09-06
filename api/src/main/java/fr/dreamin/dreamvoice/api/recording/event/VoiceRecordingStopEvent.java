package fr.dreamin.dreamvoice.api.recording.event;

import fr.dreamin.dreamapi.api.event.ToolsEvent;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a voice recording session ends and the recording is generated.
 */
@Getter
@RequiredArgsConstructor
public final class VoiceRecordingStopEvent extends ToolsEvent {

  private final @NotNull VoiceRecording recording;

}
