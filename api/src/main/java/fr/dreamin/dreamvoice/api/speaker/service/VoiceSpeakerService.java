package fr.dreamin.dreamvoice.api.speaker.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Service managing 3D locational speakers, dual-channel playback (voice + background audio),
 * entity tracking, and access restrictions.
 */
public interface VoiceSpeakerService {

  /**
   * Gets the underlying VoicechatServerApi instance.
   *
   * @return the active API instance
   */
  VoicechatServerApi getAPI();

  /**
   * Initializes the speaker service with the Simple Voice Chat server API.
   *
   * @param api the VoicechatServerApi instance
   */
  void init(final @NotNull VoicechatServerApi api);

  /**
   * Returns all active 3D speakers.
   *
   * @return collection of {@link Speaker} instances
   */
  Collection<Speaker> getSpeakers();

  /**
   * Retrieves a speaker by its UUID.
   *
   * @param uuid the speaker UUID
   * @return the {@link Speaker}, or {@code null} if not found
   */
  @Nullable Speaker getSpeaker(final @NotNull UUID uuid);

  /**
   * Retrieves a speaker by its unique name.
   *
   * @param name the speaker name
   * @return the {@link Speaker}, or {@code null} if not found
   */
  @Nullable Speaker getSpeaker(final @NotNull String name);

  /**
   * Registers a speaker into the service registry.
   *
   * @param speaker the speaker instance
   */
  void register(final @NotNull Speaker speaker);

  /**
   * Unregisters a speaker by its UUID.
   *
   * @param uuid the speaker UUID
   */
  void unregister(final @NotNull UUID uuid);

  /**
   * Unregisters a speaker instance.
   *
   * @param speaker the speaker instance
   */
  void unregister(final @NotNull Speaker speaker);

  /**
   * Clears and unregisters all active speakers.
   */
  void unregisterAll();

  /**
   * Retrieves the VolumeCategory used for speaker audio channels.
   *
   * @return the {@link VolumeCategory}
   */
  VolumeCategory getVolumeCategory();

  /**
   * Plays a recorded voice session through a speaker.
   *
   * @param speaker   the target speaker
   * @param recording the voice recording to play
   */
  void playRecording(final @NotNull Speaker speaker, final @NotNull VoiceRecording recording);

  /**
   * Plays raw PCM audio samples through a speaker.
   *
   * @param speaker the target speaker
   * @param pcm     raw 16-bit 48kHz audio samples
   */
  void playSound(final @NotNull Speaker speaker, final @NotNull short[] pcm);

  /**
   * Plays raw PCM audio samples through a speaker with optional looping.
   *
   * @param speaker the target speaker
   * @param pcm     raw 16-bit 48kHz audio samples
   * @param loop    whether playback should loop
   */
  void playSound(final @NotNull Speaker speaker, final @NotNull short[] pcm, final boolean loop);

  /**
   * Plays an audio file from the `sounds/` directory through a speaker.
   *
   * @param speaker  the target speaker
   * @param fileName file name relative to sounds folder
   * @param loop     whether playback should loop
   */
  void playSoundFile(final @NotNull Speaker speaker, final @NotNull String fileName, final boolean loop);

  /**
   * Streams a web audio URL through a speaker.
   *
   * @param speaker the target speaker
   * @param url     the audio stream URL
   * @param loop    whether playback should loop
   */
  void playSoundUrl(final @NotNull Speaker speaker, final @NotNull String url, final boolean loop);

  /**
   * Stops any active sound playback on a speaker.
   *
   * @param speaker the target speaker
   */
  void stopSound(final @NotNull Speaker speaker);

  /**
   * Saves all active speakers to disk.
   */
  void save();

  /**
   * Reloads saved speakers from disk.
   */
  void load();

  /**
   * Saves a specific speaker to disk.
   *
   * @param uuid the speaker UUID
   */
  void save(final @NotNull UUID uuid);

}
