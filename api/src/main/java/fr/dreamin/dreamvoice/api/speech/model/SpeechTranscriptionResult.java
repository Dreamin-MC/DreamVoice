package fr.dreamin.dreamvoice.api.speech.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Result of a speech transcription process containing formatted segments and full text.
 *
 * @param recordingUuid  UUID of the source recording, if any
 * @param speakerUuid    UUID of the primary speaker, if known
 * @param durationSeconds total audio duration transcribed
 * @param fullText       full concatenated transcript
 * @param segments       list of timestamped dialogue segments
 */
public record SpeechTranscriptionResult(
  @Nullable UUID recordingUuid,
  @Nullable UUID speakerUuid,
  float durationSeconds,
  @NotNull String fullText,
  @NotNull List<TranscriptionSegment> segments
) {

  /**
   * Represents an individual speech segment with offset and text.
   *
   * @param offsetSeconds start offset in seconds
   * @param speakerName   resolved display name of the speaker
   * @param text          transcribed text
   */
  public record TranscriptionSegment(
    float offsetSeconds,
    @NotNull String speakerName,
    @NotNull String text
  ) {
  }

}
