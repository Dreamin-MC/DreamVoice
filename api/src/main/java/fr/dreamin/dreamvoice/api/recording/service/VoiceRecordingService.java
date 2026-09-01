package fr.dreamin.dreamvoice.api.recording.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing live voice recording capture, playback to connections, audio file/URL conversion,
 * segment slicing, and interactive physical Cassette item creation.
 */
public interface VoiceRecordingService {

  /**
   * Initializes the recording service with the Simple Voice Chat server API.
   *
   * @param api the VoicechatServerApi instance
   */
  void init(final @NotNull VoicechatServerApi api);

  /**
   * Gets the underlying VoicechatServerApi instance.
   *
   * @return the active API instance
   */
  VoicechatServerApi getAPI();

  /**
   * Returns all recorded voice sessions.
   *
   * @return collection of {@link VoiceRecording}s
   */
  Collection<VoiceRecording> getVoiceRecordings();

  /**
   * Retrieves a recording by its UUID.
   *
   * @param uuid the recording UUID
   * @return the {@link VoiceRecording}, or {@code null} if not found
   */
  @Nullable VoiceRecording getVoiceRecording(final @NotNull UUID uuid);

  /**
   * Registers a voice recording into the service registry.
   *
   * @param voiceRecording the recording instance
   */
  void register(final @NotNull VoiceRecording voiceRecording);

  /**
   * Unregisters a voice recording.
   *
   * @param voiceRecording the recording instance
   */
  void unregister(final @NotNull VoiceRecording voiceRecording);

  /**
   * Unregisters a voice recording by its UUID.
   *
   * @param uuid the recording UUID
   */
  void unregister(final @NotNull UUID uuid);

  /**
   * Clears and unregisters all voice recordings.
   */
  void unregisterAll();

  /**
   * Plays a recording to a single Simple Voice Chat connection.
   *
   * @param connection the target voice connection
   * @param recording  the recording to play
   */
  void playRecordingTo(final @NotNull VoicechatConnection connection, final @NotNull VoiceRecording recording);

  /**
   * Plays a recording to multiple Simple Voice Chat connections simultaneously.
   *
   * @param connections target voice connections
   * @param recording   the recording to play
   */
  default void playRecordingTo(final @NotNull Collection<VoicechatConnection> connections, final @NotNull VoiceRecording recording) {
    connections.forEach(conn -> playRecordingTo(conn, recording));
  }

  /**
   * Starts a new live voice recording session for a speaker.
   *
   * @param uuid the UUID of the speaker
   * @return the started {@link VoiceRecording}
   */
  VoiceRecording startRecording(final @NotNull UUID uuid);

  /**
   * Stops the active voice recording session for a speaker.
   *
   * @param uuid the UUID of the speaker
   */
  void stopRecording(final @NotNull UUID uuid);

  /**
   * Binds an existing ItemStack to a voice recording.
   *
   * @param item      the item stack to modify
   * @param recording the recording instance
   * @return the modified ItemStack
   */
  @NotNull ItemStack linkItem(final @NotNull ItemStack item, final @NotNull VoiceRecording recording);

  /**
   * Binds an existing ItemStack to a voice recording UUID.
   *
   * @param item          the item stack to modify
   * @param recordingUuid the recording UUID
   * @return the modified ItemStack
   */
  @NotNull ItemStack linkItem(final @NotNull ItemStack item, final @NotNull UUID recordingUuid);

  /**
   * Generates a physical playable Cassette item for a recording.
   *
   * @param recording the recording instance
   * @return the created Cassette ItemStack
   */
  @NotNull ItemStack createCassette(final @NotNull VoiceRecording recording);

  /**
   * Converts raw PCM audio samples into an Opus-encoded {@link VoiceRecording}.
   *
   * @param pcm         the 16-bit 48kHz audio samples
   * @param speakerUuid the author speaker UUID
   * @return CompletableFuture containing the resulting {@link VoiceRecording}
   */
  CompletableFuture<VoiceRecording> createRecordingFromPcm(final short @NotNull [] pcm, final @NotNull UUID speakerUuid);

  /**
   * Asynchronously converts an external audio file into a {@link VoiceRecording}.
   *
   * @param file the audio file
   * @param name optional display name
   * @return CompletableFuture containing the resulting {@link VoiceRecording}
   */
  CompletableFuture<VoiceRecording> createRecordingFromFile(final @NotNull File file, final @Nullable String name);

  /**
   * Converts a file from the plugin `sounds/` directory into a {@link VoiceRecording}.
   *
   * @param fileName the filename relative to sounds directory
   * @return CompletableFuture containing the resulting {@link VoiceRecording}
   */
  CompletableFuture<VoiceRecording> createRecordingFromFile(final @NotNull String fileName);

  /**
   * Asynchronously downloads and converts an audio file from a web URL into a {@link VoiceRecording}.
   *
   * @param url  the audio URL
   * @param name optional display name
   * @return CompletableFuture containing the resulting {@link VoiceRecording}
   */
  CompletableFuture<VoiceRecording> createRecordingFromUrl(final @NotNull String url, final @Nullable String name);

  /**
   * Slices an audio segment from a recording starting at a specific timestamp.
   *
   * @param recordingUuid the recording UUID
   * @param timestamp     start timestamp
   * @param duration      duration of the slice
   * @return sliced {@link VoiceRecording}, or {@code null} if not found
   */
  @Nullable VoiceRecording sliceRecording(final @NotNull UUID recordingUuid, final @NotNull Instant timestamp, final @NotNull Duration duration);

  /**
   * Slices an audio segment from a recording by start offset and length in milliseconds.
   *
   * @param recordingUuid the recording UUID
   * @param startOffsetMs start offset in milliseconds
   * @param durationMs    slice duration in milliseconds
   * @return sliced {@link VoiceRecording}, or {@code null} if not found
   */
  @Nullable VoiceRecording sliceRecording(final @NotNull UUID recordingUuid, final long startOffsetMs, final long durationMs);

  /**
   * Extracts the last duration segment of a recording.
   *
   * @param recordingUuid the recording UUID
   * @param duration      duration to extract
   * @return sliced {@link VoiceRecording}, or {@code null} if not found
   */
  @Nullable VoiceRecording sliceLastRecording(final @NotNull UUID recordingUuid, final @NotNull Duration duration);

  /**
   * Extracts the last duration in milliseconds of a recording.
   *
   * @param recordingUuid the recording UUID
   * @param durationMs    milliseconds to extract
   * @return sliced {@link VoiceRecording}, or {@code null} if not found
   */
  @Nullable VoiceRecording sliceLastRecording(final @NotNull UUID recordingUuid, final long durationMs);

}
