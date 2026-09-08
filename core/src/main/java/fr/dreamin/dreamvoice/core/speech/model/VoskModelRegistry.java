package fr.dreamin.dreamvoice.core.speech.model;

import fr.dreamin.dreamvoice.api.speech.model.SpeechModelInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Catalogue and resolver for officially supported lightweight Vosk speech models.
 */
public final class VoskModelRegistry {

  private static final List<SpeechModelInfo> CLOUD_MODELS = List.of(
    // Small models (recommended for real-time keyword spotting and low RAM)
    new SpeechModelInfo("vosk-model-small-fr-0.22", "fr", "French Small (41MB)", "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip", 41),
    new SpeechModelInfo("vosk-model-small-en-us-0.15", "en", "English US Small (40MB)", "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", 40),
    new SpeechModelInfo("vosk-model-small-de-0.15", "de", "German Small (45MB)", "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip", 45),
    new SpeechModelInfo("vosk-model-small-es-0.42", "es", "Spanish Small (39MB)", "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip", 39),
    new SpeechModelInfo("vosk-model-small-it-0.22", "it", "Italian Small (48MB)", "https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip", 48),
    new SpeechModelInfo("vosk-model-small-ja-0.22", "ja", "Japanese Small (48MB)", "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip", 48),
    new SpeechModelInfo("vosk-model-small-ru-0.22", "ru", "Russian Small (45MB)", "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip", 45),
    new SpeechModelInfo("vosk-model-small-pt-0.3", "pt", "Portuguese Small (31MB)", "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip", 31),
    new SpeechModelInfo("vosk-model-small-nl-0.22", "nl", "Dutch Small (39MB)", "https://alphacephei.com/vosk/models/vosk-model-small-nl-0.22.zip", 39),
    new SpeechModelInfo("vosk-model-small-cn-0.22", "zh", "Chinese Small (42MB)", "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip", 42),

    // Big server models (high accuracy transcription, high RAM)
    new SpeechModelInfo("vosk-model-fr-0.22", "fr-big", "French Big Server (1.4GB)", "https://alphacephei.com/vosk/models/vosk-model-fr-0.22.zip", 1400),
    new SpeechModelInfo("vosk-model-en-us-0.22", "en-big", "English US Big Server (1.8GB)", "https://alphacephei.com/vosk/models/vosk-model-en-us-0.22.zip", 1800),
    new SpeechModelInfo("vosk-model-de-0.21", "de-big", "German Big Server (1.9GB)", "https://alphacephei.com/vosk/models/vosk-model-de-0.21.zip", 1900),
    new SpeechModelInfo("vosk-model-es-0.42", "es-big", "Spanish Big Server (1.4GB)", "https://alphacephei.com/vosk/models/vosk-model-es-0.42.zip", 1400),
    new SpeechModelInfo("vosk-model-it-0.22", "it-big", "Italian Big Server (1.2GB)", "https://alphacephei.com/vosk/models/vosk-model-it-0.22.zip", 1200),
    new SpeechModelInfo("vosk-model-ja-0.22", "ja-big", "Japanese Big Server (1.0GB)", "https://alphacephei.com/vosk/models/vosk-model-ja-0.22.zip", 1000),
    new SpeechModelInfo("vosk-model-ru-0.42", "ru-big", "Russian Big Server (1.8GB)", "https://alphacephei.com/vosk/models/vosk-model-ru-0.42.zip", 1800),
    new SpeechModelInfo("vosk-model-cn-0.22", "zh-big", "Chinese Big Server (1.3GB)", "https://alphacephei.com/vosk/models/vosk-model-cn-0.22.zip", 1300),
    new SpeechModelInfo("vosk-model-hi-0.22", "hi-big", "Hindi Big Server (1.5GB)", "https://alphacephei.com/vosk/models/vosk-model-hi-0.22.zip", 1500),
    new SpeechModelInfo("vosk-model-uk-v3", "uk-big", "Ukrainian Big Server (343MB)", "https://alphacephei.com/vosk/models/vosk-model-uk-v3.zip", 343)
  );

  public static @NotNull List<SpeechModelInfo> getAllModels() {
    return CLOUD_MODELS;
  }

  public static @Nullable SpeechModelInfo findByLangOrId(final @NotNull String query) {
    final var lower = query.toLowerCase();
    for (final var model : CLOUD_MODELS) {
      if (model.language().equalsIgnoreCase(lower) || model.id().equalsIgnoreCase(query))
        return model;
    }
    // Fallback: match prefix (e.g. "fr" matches first "fr" small model)
    for (final var model : CLOUD_MODELS) {
      if (model.language().startsWith(lower) || model.id().toLowerCase().contains(lower))
        return model;
    }
    return null;
  }

}
