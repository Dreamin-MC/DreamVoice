package fr.dreamin.dreamvoice.api.speech.service;

import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.speech.model.KeywordDefinition;
import fr.dreamin.dreamvoice.api.speech.model.SpeechModelInfo;
import fr.dreamin.dreamvoice.api.speech.model.SpeechTranscriptionResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Service managing real-time speech recognition, keyword spotting, model management,
 * and transcription stations in DreamVoice.
 */
public interface VoiceSpeechService {

  /**
   * Transcribes an existing {@link VoiceRecording} session asynchronously.
   *
   * @param recording the recording session
   * @return future containing the structured transcription result
   */
  @NotNull CompletableFuture<SpeechTranscriptionResult> transcribeRecording(final @NotNull VoiceRecording recording);

  /**
   * Transcribes raw 48kHz mono 16-bit PCM audio samples.
   *
   * @param pcm         16-bit 48kHz PCM samples
   * @param speakerUuid optional UUID of the speaker
   * @return future containing the structured transcription result
   */
  @NotNull CompletableFuture<SpeechTranscriptionResult> transcribePcm(final short @NotNull [] pcm, final @Nullable UUID speakerUuid);

  /**
   * Downloads and extracts an official Vosk model by language code (e.g. "fr", "en", "de").
   *
   * @param langCode         language code
   * @param progressConsumer progress consumer accepting values from 0.0 to 1.0
   * @return future completed with the target extracted directory
   */
  @NotNull CompletableFuture<File> downloadModel(final @NotNull String langCode, final @Nullable Consumer<Double> progressConsumer);

  /**
   * Retrieves the list of currently installed Vosk models in the models directory.
   *
   * @return list of model directory names
   */
  @NotNull List<String> getInstalledModels();

  /**
   * Retrieves the catalogue of downloadable cloud models.
   *
   * @return list of supported models
   */
  @NotNull List<SpeechModelInfo> getCloudModels();

  /**
   * Gets the active model identifier currently loaded or configured.
   *
   * @return active model id
   */
  @NotNull String getActiveModel();

  /**
   * Sets and reloads the active model.
   *
   * @param modelId model identifier / folder name
   * @return {@code true} if successfully loaded
   */
  boolean setActiveModel(final @NotNull String modelId);

  /**
   * Checks whether a block identifier is configured as a transcription station.
   *
   * @param blockId target block identifier / material
   * @return {@code true} if allowed
   */
  boolean isStationBlock(final @NotNull String blockId);

  /**
   * Starts the transcription process at a physical station block.
   *
   * @param playerUuid      interacting player UUID
   * @param stationLocation the station location
   * @param recording       the recording to transcribe
   */
  void startStationProcess(final @NotNull UUID playerUuid, final @NotNull VoiceLocation stationLocation, final @NotNull VoiceRecording recording);

  /**
   * Retrieves the active list of registered keywords.
   *
   * @return unmodifiable list of keywords
   */
  @NotNull List<KeywordDefinition> getRegisteredKeywords();

  /**
   * Reloads configuration and keywords from disk.
   */
  void reload();

  /**
   * Checks if keyword detection debug notifications are enabled for the specified player.
   *
   * @param playerUuid player UUID
   * @return {@code true} if debug notifications are enabled
   */
  boolean isDebugEnabled(final @NotNull UUID playerUuid);

  /**
   * Sets keyword detection debug notifications for the specified player.
   *
   * @param playerUuid player UUID
   * @param enabled    whether debug notifications are enabled
   */
  void setDebugEnabled(final @NotNull UUID playerUuid, final boolean enabled);

  /**
   * Toggles keyword detection debug notifications for the specified player.
   *
   * @param playerUuid player UUID
   * @return new debug state
   */
  boolean toggleDebug(final @NotNull UUID playerUuid);

  /**
   * Checks if global console debug logging is enabled for keyword detection.
   *
   * @return {@code true} if global debug is enabled
   */
  boolean isGlobalDebugEnabled();

  /**
   * Sets global console debug logging for keyword detection.
   *
   * @param enabled whether global debug is enabled
   */
  void setGlobalDebugEnabled(final boolean enabled);

  /**
   * Toggles global console debug logging for keyword detection.
   *
   * @return new global debug state
   */
  boolean toggleGlobalDebug();

}
