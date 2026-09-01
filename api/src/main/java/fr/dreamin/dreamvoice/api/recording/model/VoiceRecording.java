package fr.dreamin.dreamvoice.api.recording.model;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Model representing a voice recording session.
 * Stores timestamped Opus frames and supports slicing and duration calculations.
 */
@Getter
public final class VoiceRecording {

  private final @NotNull UUID uuid = UUID.randomUUID();
  private final @NotNull UUID speakerUUID;
  private @Nullable Instant startTime;
  private @Nullable Duration duration;

  private final @NotNull List<TimedAudioFrame> audioFrames = new ArrayList<>();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceRecording(final @NotNull UUID speakerUUID) {
    this.speakerUUID = speakerUUID;
  }

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  /**
   * Starts or restarts the recording session.
   */
  public void start() {
    this.startTime = Instant.now();
    this.duration = null;
    this.audioFrames.clear();
  }

  /**
   * Stops the active recording session and computes the total duration.
   */
  public void stop() {
    if (this.startTime == null)
      return;
    this.duration = Duration.between(this.startTime, Instant.now());
  }

  /**
   * Checks whether this recording is currently capturing audio.
   *
   * @return {@code true} if actively recording
   */
  public boolean isRecording() {
    return this.startTime != null && this.duration == null;
  }

  /**
   * Checks whether this recording session has finished.
   *
   * @return {@code true} if completed
   */
  public boolean isFinished() {
    return this.startTime != null && this.duration != null;
  }

  /**
   * Appends an Opus audio frame to this recording.
   *
   * @param opusData the raw Opus frame bytes
   */
  public void addAudio(final byte[] opusData) {
    if (this.startTime == null)
      return;

    final var offsetMs = Math.max(0, System.currentTimeMillis() - this.startTime.toEpochMilli());
    this.audioFrames.add(new TimedAudioFrame(offsetMs, opusData.clone()));
  }

  /**
   * Calculates the total duration of the recording in seconds.
   *
   * @return duration in seconds
   */
  public float getDurationSeconds() {
    if (this.duration != null)
      return (float) this.duration.toMillis() / 1000F;
    if (this.startTime != null)
      return (float) (System.currentTimeMillis() - this.startTime.toEpochMilli()) / 1000F;
    return 0F;
  }

  /**
   * Slices an audio segment starting from a specific Instant.
   *
   * @param timestamp the start timestamp
   * @param duration  the length of the slice
   * @return a new sliced {@link VoiceRecording}
   */
  public VoiceRecording slice(final @NotNull Instant timestamp, final @NotNull Duration duration) {
    if (this.startTime == null)
      return slice(0L, duration.toMillis());

    final var diffMs = timestamp.toEpochMilli() - this.startTime.toEpochMilli();
    final var startOffsetMs = Math.max(0L, diffMs);
    final var durationMs = duration.toMillis();
    return slice(startOffsetMs, durationMs);
  }

  /**
   * Slices an audio segment from a start millisecond offset.
   *
   * @param startOffsetMs offset in milliseconds from recording start
   * @param durationMs    slice duration in milliseconds
   * @return a new sliced {@link VoiceRecording}
   */
  public VoiceRecording slice(final long startOffsetMs, final long durationMs) {
    final var sliced = new VoiceRecording(this.speakerUUID);
    sliced.startTime = Instant.now().minusMillis(durationMs);
    sliced.duration = Duration.ofMillis(durationMs);

    final var endOffsetMs = startOffsetMs + durationMs;
    for (final var frame : this.audioFrames) {
      if (frame.timestampMs() >= startOffsetMs && frame.timestampMs() <= endOffsetMs) {
        final var newTimestamp = frame.timestampMs() - startOffsetMs;
        sliced.audioFrames.add(new TimedAudioFrame(newTimestamp, frame.data().clone()));
      }
    }

    return sliced;
  }

  /**
   * Extracts the last duration segment of the recording.
   *
   * @param duration the duration to extract
   * @return a new sliced {@link VoiceRecording}
   */
  public VoiceRecording sliceLast(final @NotNull Duration duration) {
    return sliceLast(duration.toMillis());
  }

  /**
   * Extracts the last duration in milliseconds of the recording.
   *
   * @param durationMs milliseconds to extract
   * @return a new sliced {@link VoiceRecording}
   */
  public VoiceRecording sliceLast(final long durationMs) {
    final var totalMs = (long) (getDurationSeconds() * 1000L);
    final var startMs = Math.max(0L, totalMs - durationMs);
    return slice(startMs, durationMs);
  }

}
