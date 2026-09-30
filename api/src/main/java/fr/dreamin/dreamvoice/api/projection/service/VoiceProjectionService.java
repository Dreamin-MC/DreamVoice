package fr.dreamin.dreamvoice.api.projection.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.projection.model.VoiceProjection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Service managing player voice projections, body anchors, and remote acoustic routing.
 */
public interface VoiceProjectionService {

  /**
   * Initializes the projection service with the Simple Voice Chat server API.
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
   * Retrieves the VolumeCategory used for projection channels.
   */
  VolumeCategory getVolumeCategory();

  /**
   * Creates a static voice projection point for a player.
   *
   * @param playerUuid     the player UUID
   * @param anchorLocation the static location of the anchor
   * @return the created {@link VoiceProjection}
   */
  @NotNull VoiceProjection createProjection(final @NotNull UUID playerUuid, final @NotNull VoiceLocation anchorLocation);

  /**
   * Creates a dynamic voice projection bound to a moving entity.
   *
   * @param playerUuid       the player UUID
   * @param anchorLocation   the initial location
   * @param anchorEntityUuid the entity UUID to attach the projection to
   * @return the created {@link VoiceProjection}
   */
  @NotNull VoiceProjection createProjection(final @NotNull UUID playerUuid, final @NotNull VoiceLocation anchorLocation, final @Nullable UUID anchorEntityUuid);

  /**
   * Registers an existing projection into the service.
   *
   * @param projection the projection instance
   */
  void register(final @NotNull VoiceProjection projection);

  default void registerProjection(final @NotNull VoiceProjection projection) {
    register(projection);
  }

  /**
   * Removes a player's active voice projection.
   *
   * @param playerUuid the player UUID
   */
  void removeProjection(final @NotNull UUID playerUuid);

  default void removeProjection(final @NotNull VoiceProjection projection) {
    removeProjection(projection.getPlayerUuid());
  }

  /**
   * Retrieves the active voice projection for a player.
   *
   * @param playerUuid the player UUID
   * @return the {@link VoiceProjection}, or {@code null} if none active
   */
  @Nullable VoiceProjection getProjection(final @NotNull UUID playerUuid);

  /**
   * Retrieves a projection by its unique ID.
   *
   * @param projectionId the projection UUID
   * @return the {@link VoiceProjection}, or {@code null} if not found
   */
  @Nullable VoiceProjection getProjectionById(final @NotNull UUID projectionId);

  /**
   * Checks whether a player has an active voice projection.
   *
   * @param playerUuid the player UUID
   * @return {@code true} if an active projection exists
   */
  boolean hasProjection(final @NotNull UUID playerUuid);

  /**
   * Returns all active voice projections.
   *
   * @return collection of active {@link VoiceProjection}s
   */
  @NotNull Collection<VoiceProjection> getProjections();

  /**
   * Clears and removes all active voice projections.
   */
  void clearProjections();

  /**
   * Updates the static coordinates of a player's projection.
   *
   * @param playerUuid  the player UUID
   * @param newLocation the updated location
   */
  void updateLocation(final @NotNull UUID playerUuid, final @NotNull VoiceLocation newLocation);

  /**
   * Saves all active projections to disk.
   */
  void save();

  /**
   * Reloads saved projections from disk.
   */
  void load();

}
