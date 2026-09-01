package fr.dreamin.dreamvoice.api.recording.model;

/**
 * Record representing a timestamped Opus audio frame.
 *
 * @param timestampMs millisecond offset from the start of the recording
 * @param data        raw Opus audio frame bytes
 */
public record TimedAudioFrame(long timestampMs, byte[] data) {
}
