package fr.dreamin.dreamvoice.api.radio.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamvoice.api.radio.model.RadioChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Service managing radio channels, frequency tuning, Roger Beep feedback, and radio transmission routing.
 */
public interface VoiceRadioService {

  /**
   * Initializes the radio service with the Simple Voice Chat server API.
   *
   * @param api the VoicechatServerApi instance
   */
  void init(final @NotNull VoicechatServerApi api);

  /**
   * Returns all active radio frequency channels.
   *
   * @return collection of {@link RadioChannel}s
   */
  @NotNull Collection<RadioChannel> getChannels();

  /**
   * Retrieves an existing radio channel by name, or creates a new one if it does not exist.
   *
   * @param name the channel name
   * @return the {@link RadioChannel}
   */
  @NotNull RadioChannel getOrCreateChannel(final @NotNull String name);

  /**
   * Retrieves a radio channel by name.
   *
   * @param name the channel name
   * @return the {@link RadioChannel}, or {@code null} if not found
   */
  @Nullable RadioChannel getChannel(final @NotNull String name);

  /**
   * Retrieves the active radio channel a player is currently tuned into.
   *
   * @param playerUuid the player UUID
   * @return the {@link RadioChannel}, or {@code null} if not on any radio
   */
  @Nullable RadioChannel getChannelOfPlayer(final @NotNull UUID playerUuid);

  /**
   * Tunes a player into a radio frequency channel.
   *
   * @param playerUuid  the player UUID
   * @param channelName the channel name
   */
  void joinChannel(final @NotNull UUID playerUuid, final @NotNull String channelName);

  /**
   * Disconnects a player from their active radio frequency channel.
   *
   * @param playerUuid the player UUID
   */
  void leaveChannel(final @NotNull UUID playerUuid);

  /**
   * Deletes a radio channel by name.
   *
   * @param name the channel name
   */
  void removeChannel(final @NotNull String name);

  /**
   * Saves all radio channels to disk.
   */
  void save();

  /**
   * Reloads saved radio channels from disk.
   */
  void load();

}
