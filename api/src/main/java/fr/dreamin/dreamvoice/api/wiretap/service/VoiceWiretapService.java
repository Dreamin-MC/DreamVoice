package fr.dreamin.dreamvoice.api.wiretap.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Service managing covert spy microphones, spatial eavesdropping subscriptions,
 * mobile entity bug tracking, and direct cassette recording.
 */
public interface VoiceWiretapService {

  /**
   * Initializes the wiretap service with the Simple Voice Chat server API.
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
   * Creates a static wiretap listening point at a specific location.
   *
   * @param name     the wiretap identifier
   * @param location the location in world
   * @return the created {@link VoiceWiretap}
   */
  @NotNull VoiceWiretap createWiretap(final @NotNull String name, final @NotNull Location location);

  /**
   * Creates a mobile wiretap bug attached to an entity.
   *
   * @param name   the wiretap identifier
   * @param entity the entity to attach to
   * @return the created {@link VoiceWiretap}
   */
  @NotNull VoiceWiretap createWiretap(final @NotNull String name, final @NotNull Entity entity);

  /**
   * Attaches an existing wiretap to a target entity.
   *
   * @param name   the wiretap name
   * @param entity the entity to attach to
   */
  void attachToEntity(final @NotNull String name, final @NotNull Entity entity);

  /**
   * Detaches a wiretap from its entity, freezing its current coordinates.
   *
   * @param name the wiretap name
   */
  void detachFromEntity(final @NotNull String name);

  /**
   * Registers a wiretap into the service registry.
   *
   * @param wiretap the wiretap instance
   */
  void register(final @NotNull VoiceWiretap wiretap);

  /**
   * Removes a wiretap by its name.
   *
   * @param name the wiretap name
   */
  void removeWiretap(final @NotNull String name);

  /**
   * Removes a wiretap by its UUID.
   *
   * @param uuid the wiretap UUID
   */
  void removeWiretap(final @NotNull UUID uuid);

  /**
   * Retrieves a wiretap by name.
   *
   * @param name the wiretap name
   * @return the {@link VoiceWiretap}, or {@code null} if not found
   */
  @Nullable VoiceWiretap getWiretap(final @NotNull String name);

  /**
   * Retrieves a wiretap by UUID.
   *
   * @param uuid the wiretap UUID
   * @return the {@link VoiceWiretap}, or {@code null} if not found
   */
  @Nullable VoiceWiretap getWiretap(final @NotNull UUID uuid);

  /**
   * Returns all active wiretaps.
   *
   * @return collection of {@link VoiceWiretap}s
   */
  @NotNull Collection<VoiceWiretap> getWiretaps();

  /**
   * Subscribes a player to live eavesdropping on a wiretap.
   *
   * @param name       the wiretap name
   * @param playerUuid the player UUID
   */
  void addListener(final @NotNull String name, final @NotNull UUID playerUuid);

  default void addListener(final @NotNull String name, final @NotNull Player player) {
    addListener(name, player.getUniqueId());
  }

  /**
   * Unsubscribes a player from live eavesdropping.
   *
   * @param name       the wiretap name
   * @param playerUuid the player UUID
   */
  void removeListener(final @NotNull String name, final @NotNull UUID playerUuid);

  default void removeListener(final @NotNull String name, final @NotNull Player player) {
    removeListener(name, player.getUniqueId());
  }

  /**
   * Unsubscribes a player from all active wiretaps.
   *
   * @param playerUuid the player UUID
   */
  void removeListenerFromAll(final @NotNull UUID playerUuid);

  /**
   * Starts covert audio recording on a wiretap.
   *
   * @param name the wiretap name
   * @return the started {@link VoiceRecording}, or {@code null} if wiretap not found
   */
  @Nullable VoiceRecording startRecording(final @NotNull String name);

  /**
   * Stops active covert audio recording on a wiretap.
   *
   * @param name the wiretap name
   * @return the finished {@link VoiceRecording}, or {@code null} if not recording
   */
  @Nullable VoiceRecording stopRecording(final @NotNull String name);

  /**
   * Saves all active wiretaps to disk.
   */
  void save();

  /**
   * Reloads saved wiretaps from disk.
   */
  void load();

}
