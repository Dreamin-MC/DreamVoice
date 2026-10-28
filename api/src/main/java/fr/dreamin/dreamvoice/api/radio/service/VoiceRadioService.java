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
   * Gets the underlying VoicechatServerApi instance.
   */
  VoicechatServerApi getAPI();

  /**
   * Registers a radio channel.
   */
  void register(final @NotNull RadioChannel radioChannel);

  /**
   * Unregisters a radio channel by name.
   */
  void unregister(final @NotNull String name);

  /**
   * Checks whether a player is connected to any radio channel.
   */
  boolean isInChannel(final @NotNull UUID playerUuid);

  /**
   * Unregisters all radio channels.
   */
  void unregisterAll();

  /**
   * Tunes a player into a radio frequency channel.
   *
   * @param playerUuid  the player UUID
   * @param channelName the channel name
   * @return true if joined successfully
   */
  boolean joinChannel(final @NotNull UUID playerUuid, final @NotNull String channelName);

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
  default void removeChannel(final @NotNull String name) {
    unregister(name);
  }

  /**
   * Saves all radio channels to disk.
   */
  void save();

  /**
   * Reloads saved radio channels from disk.
   */
  void load();

  /**
   * Registers a listener called whenever a player transmits on a radio channel.
   *
   * @param listener (channelName, senderUuid)
   */
  default void addTransmissionListener(final @NotNull java.util.function.BiConsumer<String, UUID> listener) {}

  @FunctionalInterface
  interface RadioAudioTransmissionListener {
    void onAudioTransmit(final @NotNull String channelName, final @NotNull UUID senderUuid, final byte[] opusData);
  }

  interface RadioPacketFilter {
    boolean shouldDeliver(final @NotNull RadioChannel channel, final @NotNull UUID senderUuid, final @NotNull UUID recipientUuid);

    default boolean shouldTransmit(final @NotNull RadioChannel channel, final @NotNull UUID senderUuid) {
      return true;
    }
  }

  /**
   * Sets a packet filter to control radio voice transmission between participants (e.g. range, packet loss).
   *
   * @param filter the packet filter
   */
  default void setPacketFilter(final @Nullable RadioPacketFilter filter) {}

  /**
   * Retrieves the current radio packet filter.
   */
  default @Nullable RadioPacketFilter getPacketFilter() {
    return null;
  }

  /**
   * Registers a listener called whenever radio voice audio is transmitted, providing the processed Opus audio.
   *
   * @param listener (channelName, senderUuid, opusData)
   */
  default void addAudioTransmissionListener(final @NotNull RadioAudioTransmissionListener listener) {}

  /**
   * Checks whether a radio channel is currently receiving active voice transmission.
   *
   * @param channelName the channel name
   * @return true if currently transmitting
   */
  default boolean isChannelActive(final @NotNull String channelName) {
    return false;
  }

  /**
   * Starts a voice recording session capturing all transmissions on a radio channel.
   *
   * @param channelName the channel name
   * @return the started {@link fr.dreamin.dreamvoice.api.recording.model.VoiceRecording}
   */
  @Nullable fr.dreamin.dreamvoice.api.recording.model.VoiceRecording startRecording(final @NotNull String channelName);

  /**
   * Stops the active voice recording session on a radio channel.
   *
   * @param channelName the channel name
   * @return the finished {@link fr.dreamin.dreamvoice.api.recording.model.VoiceRecording}, or {@code null} if not recording
   */
  @Nullable fr.dreamin.dreamvoice.api.recording.model.VoiceRecording stopRecording(final @NotNull String channelName);

  /**
   * Checks whether a radio channel is currently being recorded.
   *
   * @param channelName the channel name
   * @return true if actively recording
   */
  boolean isRecording(final @NotNull String channelName);

  /**
   * Gets the active recording session for a radio channel, if any.
   *
   * @param channelName the channel name
   * @return active {@link fr.dreamin.dreamvoice.api.recording.model.VoiceRecording}, or {@code null}
   */
  @Nullable fr.dreamin.dreamvoice.api.recording.model.VoiceRecording getActiveRecording(final @NotNull String channelName);

  /**
   * Broadcasts a voice recording across a radio channel to all tuned listeners and external systems.
   *
   * @param channelName the channel name
   * @param recording   the recording to play
   */
  void playRecordingToRadio(final @NotNull String channelName, final @NotNull fr.dreamin.dreamvoice.api.recording.model.VoiceRecording recording);

  /**
   * Stops any ongoing recording playback on a radio channel.
   *
   * @param channelName the channel name
   */
  void stopRadioPlayback(final @NotNull String channelName);

  /**
   * Checks whether a radio channel is currently playing back a recording.
   *
   * @param channelName the channel name
   * @return true if actively playing
   */
  boolean isRadioPlaying(final @NotNull String channelName);
}

