package fr.dreamin.dreamvoice.api.speech.event;

import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.speech.model.SpeechTranscriptionResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a voice recording transcription has completed successfully.
 */
@Getter
@RequiredArgsConstructor
public final class TranscriptionCompleteEvent extends VoiceEvent {

  private final @NotNull UUID playerUuid;
  private final @NotNull SpeechTranscriptionResult result;

}
