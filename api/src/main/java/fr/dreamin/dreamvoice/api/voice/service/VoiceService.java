package fr.dreamin.dreamvoice.api.voice.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.api.voice.model.VoiceSoundBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Core service managing Simple Voice Chat API integration, sound playback channels,
 * and per-player Opus codec instances.
 */
public interface VoiceService {

  /**
   * Checks whether global debug logging is enabled.
   *
   * @return {@code true} if debug is active
   */
  boolean isDebug();

  /**
   * Sets global debug logging flag.
   *
   * @param value debug flag
   */
  void setDebug(final boolean value);

  // ###############################################################
  // --------------------------- SOUNDS ---------------------------
  // ###############################################################

  /**
   * Gets the underlying VoicechatServerApi instance.
   *
   * @return the active API instance
   */
  VoicechatServerApi getAPI();

  /**
   * Plays a positional or global sound configured via {@link VoiceSoundBuilder}.
   *
   * @param builder the sound configuration builder
   */
  void playSound(final @NotNull VoiceSoundBuilder builder);

  /**
   * Returns the count of currently active playing sound channels.
   *
   * @return active sound count
   */
  int getActiveSoundCount();

  /**
   * Returns the set of UUIDs for all active playing sound channels.
   *
   * @return set of sound UUIDs
   */
  Set<UUID> getActiveSoundIds();

  /**
   * Stops an active sound channel by its UUID.
   *
   * @param soundId the sound channel UUID
   * @return {@code true} if sound was found and stopped
   */
  boolean stopSound(final @NotNull UUID soundId);

  /**
   * Stops and clears all active sound channels.
   */
  void clearAllSounds();

  // ###############################################################
  // -------------------------- PLAYERS ----------------------------
  // ###############################################################

  /**
   * Checks whether a player is connected to the voice chat server.
   *
   * @param uuid the player UUID
   * @return {@code true} if connected
   */
  boolean isPlayerConnected(final @NotNull UUID uuid);

  /**
   * Retrieves or creates a cached OpusDecoder for a player.
   *
   * @param uuid the player UUID
   * @return the {@link OpusDecoder}
   */
  OpusDecoder getDecoder(final @NotNull UUID uuid);

  /**
   * Retrieves or creates a cached OpusEncoder for a player.
   *
   * @param uuid the player UUID
   * @return the {@link OpusEncoder}
   */
  OpusEncoder getEncoder(final @NotNull UUID uuid);

}
