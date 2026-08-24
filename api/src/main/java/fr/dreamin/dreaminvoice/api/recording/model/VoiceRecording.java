package fr.dreamin.dreaminvoice.api.recording.model;

import fr.dreamin.dreaminvoice.api.utils.ByteArrayUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public final class VoiceRecording {

  private final @NotNull UUID uuid = UUID.randomUUID();
  private final @NotNull UUID speakerUUID;
  private @Nullable Instant startTime;
  private @Nullable Duration duration;

  private final @NotNull List<Byte[]> audio = new ArrayList<>();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceRecording(final @NotNull UUID speakerUUID) {
    this.speakerUUID = speakerUUID;
  }

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  public void start() {
    this.startTime = Instant.now();
    this.duration = null;
  }

  public void stop() {
    if (this.startTime == null) return;
    this.duration = Duration.between(this.startTime, Instant.now());
  }

  public boolean isRecording() {
    return this.startTime != null && this.duration == null;
  }

  public boolean isFinished() {
    return this.startTime != null && this.duration != null;
  }

  public void addAudio(final byte[] opusData) {
    final var boxed = new Byte[opusData.length];
    for (var i = 0; i < opusData.length; i++) {
      boxed[i] = opusData[i];
    }
    this.audio.add(boxed);
  }

  public byte[] getAllOpusData() {
    return audio.stream()
      .map(this::unboxBytes)
      .reduce(new byte[0], ByteArrayUtils::concat);  // Flat concat
  }

  private byte[] unboxBytes(final @NotNull Byte[] boxed) {
    final var primitive = new byte[boxed.length];
    for (var i = 0; i < boxed.length; i++) {
      primitive[i] = boxed[i];
    }
    return primitive;
  }

  public float getDurationSeconds() {
    return this.duration != null ? (float) this.duration.toMillis() / 1000F : 0F;
  }

}
