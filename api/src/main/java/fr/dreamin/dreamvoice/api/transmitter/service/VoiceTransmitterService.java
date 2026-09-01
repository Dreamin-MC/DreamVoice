package fr.dreamin.dreamvoice.api.transmitter.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamvoice.api.transmitter.model.ReceiverConfig;
import org.bukkit.entity.Player;
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
   * Initializes the transmitter service with the Simple Voice Chat server API.
   *
   * @param api the VoicechatServerApi instance
   */
  void init(@NotNull VoicechatServerApi api);

  /**
   * Checks whether a player is in transmitter mode.
   *
   * @param player the player
   * @return {@code true} if transmitter enabled
   */
  boolean isTransmitter(@NotNull Player player);

  /**
   * Checks whether a player UUID is in transmitter mode.
   *
   * @param uuid the player UUID
   * @return {@code true} if transmitter enabled
   */
  boolean isTransmitter(@NotNull UUID uuid);

  /**
   * Enables transmitter mode for a player.
   *
   * @param player the player
   */
  void createTransmitter(@NotNull Player player);

  /**
   * Enables transmitter mode for a player UUID.
   *
   * @param uuid the player UUID
   */
  void createTransmitter(@NotNull UUID uuid);

  /**
   * Disables transmitter mode for a player.
   *
   * @param player the player
   */
  void removeTransmitter(@NotNull Player player);

  /**
   * Disables transmitter mode for a player UUID.
   *
   * @param uuid the player UUID
   */
  void removeTransmitter(@NotNull UUID uuid);

  /**
   * Retrieves all configured receivers for a transmitter player.
   *
   * @param player the transmitter player
   * @return collection of {@link ReceiverConfig}s
   */
  @NotNull Collection<ReceiverConfig> getReceivers(@NotNull Player player);

  /**
   * Retrieves all configured receivers for a transmitter UUID.
   *
   * @param uuid the transmitter UUID
   * @return collection of {@link ReceiverConfig}s
   */
  @NotNull Collection<ReceiverConfig> getReceivers(@NotNull UUID uuid);

  /**
   * Adds an infinite-range receiver to a transmitter.
   *
   * @param transmitter the transmitter player
   * @param receiver    the receiver player
   */
  void addReceiver(@NotNull Player transmitter, @NotNull Player receiver);

  /**
   * Adds an infinite-range receiver to a transmitter by UUID.
   *
   * @param transmitter the transmitter UUID
   * @param receiver    the receiver UUID
   */
  void addReceiver(@NotNull UUID transmitter, @NotNull UUID receiver);

  /**
   * Adds a distance-limited receiver to a transmitter.
   *
   * @param transmitter the transmitter player
   * @param receiver    the receiver player
   * @param maxDistance maximum hearing range in blocks
   */
  void addReceiver(@NotNull Player transmitter, @NotNull Player receiver, double maxDistance);

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
   * Removes a receiver from a transmitter.
   *
   * @param transmitter the transmitter player
   * @param receiver    the receiver player
   */
  void removeReceiver(@NotNull Player transmitter, @NotNull Player receiver);

  /**
   * Removes a receiver from a transmitter by UUID.
   *
   * @param transmitter the transmitter UUID
   * @param receiver    the receiver UUID
   */
  void removeReceiver(@NotNull UUID transmitter, @NotNull UUID receiver);

  /**
   * Clears all receivers from a transmitter.
   *
   * @param transmitter the transmitter player
   */
  void clearReceivers(@NotNull Player transmitter);

  /**
   * Clears all receivers from a transmitter by UUID.
   *
   * @param transmitter the transmitter UUID
   */
  void clearReceivers(@NotNull UUID transmitter);

  /**
   * Adds a receiver with infinite range to all active transmitters.
   *
   * @param receiver the receiver player
   */
  void addReceiverToAll(@NotNull Player receiver);

  /**
   * Adds a receiver with infinite range to all active transmitters by UUID.
   *
   * @param receiver the receiver UUID
   */
  void addReceiverToAll(@NotNull UUID receiver);

  /**
   * Adds a distance-limited receiver to all active transmitters.
   *
   * @param receiver    the receiver player
   * @param maxDistance maximum hearing range
   */
  void addReceiverToAll(@NotNull Player receiver, double maxDistance);

  /**
   * Adds a distance-limited receiver to all active transmitters by UUID.
   *
   * @param receiver    the receiver UUID
   * @param maxDistance maximum hearing range
   */
  void addReceiverToAll(@NotNull UUID receiver, double maxDistance);

  /**
   * Removes a receiver from all active transmitters.
   *
   * @param receiver the receiver player
   */
  void removeReceiverFromAll(@NotNull Player receiver);

  /**
   * Removes a receiver from all active transmitters by UUID.
   *
   * @param receiver the receiver UUID
   */
  void removeReceiverFromAll(@NotNull UUID receiver);

  /**
   * Saves all active transmitters to disk.
   */
  void save();

  /**
   * Reloads saved transmitters from disk.
   */
  void load();

}
