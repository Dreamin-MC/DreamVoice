package fr.dreamin.dreamvoice.api.recording.model;

public record TimedAudioFrame(long timestampMs, byte[] data) {
}
