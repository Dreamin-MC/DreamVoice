package fr.dreamin.dreamvoice.api.recording.model;

import fr.dreamin.dreamvoice.api.utils.ByteArrayUtils;
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

  private final @NotNull List<byte[]> audio = new ArrayList<>();

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
    if (this.startTime == null)
      return;
    this.duration = Duration.between(this.startTime, Instant.now());
  }

  public boolean isRecording() {
    return this.startTime != null && this.duration == null;
  }

  public boolean isFinished() {
    return this.startTime != null && this.duration != null;
  }

  public void addAudio(final byte[] opusData) {
    this.audio.add(opusData.clone());
  }

  public byte[] getAllOpusData() {
    return this.audio.stream()
      .reduce(new byte[0], ByteArrayUtils::concat);
  }

  public float getDurationSeconds() {
    return this.duration != null ? (float) this.duration.toMillis() / 1000F : 0F;
  }

}


