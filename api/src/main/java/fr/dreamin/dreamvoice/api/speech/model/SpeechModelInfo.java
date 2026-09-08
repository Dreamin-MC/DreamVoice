package fr.dreamin.dreamvoice.api.speech.model;

import org.jetbrains.annotations.NotNull;

/**
 * Metadata record describing a downloadable or installed speech recognition model.
 *
 * @param id          unique identifier / folder name (e.g. "vosk-model-small-fr-0.22")
 * @param language    language ISO code (e.g. "fr", "en", "de")
 * @param displayName human-readable name of the model
 * @param downloadUrl direct HTTP(S) URL to the model zip archive
 * @param sizeMb      approximate download size in megabytes
 */
public record SpeechModelInfo(
  @NotNull String id,
  @NotNull String language,
  @NotNull String displayName,
  @NotNull String downloadUrl,
  int sizeMb
) {
}
