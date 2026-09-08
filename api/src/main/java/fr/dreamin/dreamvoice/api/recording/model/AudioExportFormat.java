package fr.dreamin.dreamvoice.api.recording.model;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Supported audio formats for exporting voice recordings.
 */
@Getter
public enum AudioExportFormat {

  MP3("mp3", "audio/mpeg"),
  OGG("ogg", "audio/ogg"),
  WAV("wav", "audio/wav");

  private final @NotNull String extension;
  private final @NotNull String mimeType;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  AudioExportFormat(final @NotNull String extension, final @NotNull String mimeType) {
    this.extension = extension;
    this.mimeType = mimeType;
  }

  public static @NotNull AudioExportFormat fromString(final @NotNull String format) {
    for (final var f : values()) {
      if (f.name().equalsIgnoreCase(format) || f.extension.equalsIgnoreCase(format))
        return f;
    }
    throw new IllegalArgumentException("Unknown export format: " + format + " (supported: mp3, ogg, wav)");
  }

}
