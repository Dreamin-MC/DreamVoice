package fr.dreamin.dreamvoice.api.transmitter.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import fr.dreamin.dreamvoice.api.transmitter.model.ReceiverConfig;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;

/**
 * Service managing point-to-point voice broadcast from transmitter players to specific receivers.
 */
public interface VoiceTransmitterService {

  /**
   * Gets the underlying VoicechatServerApi instance.
   *
   * @return the active API instance
   */
  @NotNull VoicechatServerApi getAPI();

  /**
   * Retrieves the VolumeCategory used for transmitter audio channels.
   */
  VolumeCategory getVolumeCategory();

  /**
   * Checks whether a receiver is registered for a transmitter.
   */
  boolean isReceiver(@NotNull UUID transmitterUuid, @NotNull UUID receiverUuid);

  /**
   * Initializes the transmitter service with the Simple Voice Chat server API.
   *
   * @param api the VoicechatServerApi instance
   */
  void init(@NotNull VoicechatServerApi api);

  /**
   * Checks whether a player UUID is in transmitter mode.
   *
   * @param uuid the player UUID
   * @return {@code true} if transmitter enabled
   */
  boolean isTransmitter(@NotNull UUID uuid);

  /**
   * Enables transmitter mode for a player UUID.
   *
   * @param uuid the player UUID
   */
  void createTransmitter(@NotNull UUID uuid);

  /**
   * Disables transmitter mode for a player UUID.
   *
   * @param uuid the player UUID
   */
  void removeTransmitter(@NotNull UUID uuid);

  /**
   * Retrieves all configured receivers for a transmitter UUID.
   *
   * @param uuid the transmitter UUID
   * @return collection of {@link ReceiverConfig}s
   */
  @NotNull Collection<ReceiverConfig> getReceivers(@NotNull UUID uuid);

  /**
   * Adds an infinite-range receiver to a transmitter by UUID.
   *
   * @param transmitter the transmitter UUID
   * @param receiver    the receiver UUID
   */
  void addReceiver(@NotNull UUID transmitter, @NotNull UUID receiver);

  /**
   * Adds a distance-limited receiver to a transmitter by UUID.
   *
   * @param transmitter the transmitter UUID
   * @param receiver    the receiver UUID
   * @param maxDistance maximum hearing range in blocks
   */
  void addReceiver(@NotNull UUID transmitter, @NotNull UUID receiver, double maxDistance);

  /**
   * Adds a configured receiver to a transmitter.
   *
   * @param transmitter    the transmitter UUID
   * @param receiverConfig the receiver configuration
   */
  void addReceiver(@NotNull UUID transmitter, @NotNull ReceiverConfig receiverConfig);

  /**
   * Removes a receiver from a transmitter by UUID.
   *
   * @param transmitter the transmitter UUID
   * @param receiver    the receiver UUID
   */
  void removeReceiver(@NotNull UUID transmitter, @NotNull UUID receiver);

  /**
   * Clears all receivers from a transmitter by UUID.
   *
   * @param transmitter the transmitter UUID
   */
  void clearReceivers(@NotNull UUID transmitter);

  /**
   * Adds a receiver with infinite range to all active transmitters by UUID.
   *
   * @param receiver the receiver UUID
   */
  void addReceiverToAll(@NotNull UUID receiver);

  /**
   * Adds a distance-limited receiver to all active transmitters by UUID.
   *
   * @param receiver    the receiver UUID
   * @param maxDistance maximum hearing range
   */
  void addReceiverToAll(@NotNull UUID receiver, double maxDistance);

  /**
   * Removes a receiver from all active transmitters by UUID.
   *
   * @param receiver the receiver UUID
   */
  void removeReceiverFromAll(@NotNull UUID receiver);

  /**
   * Retrieves the raw receiver mapping for a transmitter.
   */
  @NotNull java.util.Map<UUID, ReceiverConfig> getReceiverMap(@NotNull UUID transmitterUuid);

  /**
   * Retrieves all registered transmitters and their receivers.
   */
  @NotNull java.util.Map<UUID, java.util.Map<UUID, ReceiverConfig>> getTransmitters();

  /**
   * Clears all active transmitters and receivers.
   */
  void clearTransmitters();

  /**
   * Toggles transmitter mode on or off for a player.
   */
  boolean toggleTransmitter(@NotNull UUID playerUuid);

  /**
   * Explicitly sets transmitter mode on or off for a player.
   */
  void setTransmitter(@NotNull UUID playerUuid, boolean active);

  /**
   * Saves all active transmitters to disk.
   */
  void save();

  /**
   * Reloads saved transmitters from disk.
   */
  void load();

}
