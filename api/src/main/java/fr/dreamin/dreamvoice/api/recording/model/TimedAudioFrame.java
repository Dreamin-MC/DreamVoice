package fr.dreamin.dreamvoice.api.recording.model;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Record representing a timestamped Opus audio frame.
 *
 * @param timestampMs millisecond offset from the start of the recording
 * @param data        raw Opus audio frame bytes
 * @param speakerUuid optional UUID of the speaker who produced this audio frame
 */
public record TimedAudioFrame(long timestampMs, byte[] data, @Nullable UUID speakerUuid) {

  public TimedAudioFrame(long timestampMs, byte[] data) {
    this(timestampMs, data, null);
  }

}

