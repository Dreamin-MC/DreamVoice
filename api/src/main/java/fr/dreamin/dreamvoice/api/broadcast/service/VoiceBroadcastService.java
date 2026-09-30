package fr.dreamin.dreamvoice.api.broadcast.service;

import fr.dreamin.dreamvoice.api.broadcast.model.BroadcastPoint;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Service managing physical microphone and intercom broadcast points routed to speakers.
 */
public interface VoiceBroadcastService {

  /**
   * Retrieves all registered broadcast points.
   *
   * @return collection of broadcast points
   */
  @NotNull Collection<BroadcastPoint> getBroadcastPoints();

  /**
   * Retrieves a broadcast point by its unique name.
   *
   * @param name the broadcast point name
   * @return the broadcast point, or null if not found
   */
  @Nullable BroadcastPoint getBroadcastPoint(final @NotNull String name);

  /**
   * Retrieves a broadcast point by its UUID.
   *
   * @param uuid the broadcast point UUID
   * @return the broadcast point, or null if not found
   */
  @Nullable BroadcastPoint getBroadcastPoint(final @NotNull UUID uuid);

  /**
   * Registers a broadcast point.
   *
   * @param broadcastPoint the broadcast point to register
   */
  void register(final @NotNull BroadcastPoint broadcastPoint);

  /**
   * Unregisters a broadcast point by its UUID.
   *
   * @param uuid the broadcast point UUID
   */
  void unregister(final @NotNull UUID uuid);

  /**
   * Unregisters a broadcast point by its unique name.
   *
   * @param name the broadcast point name
   */
  void unregister(final @NotNull String name);

  /**
   * Clears all registered broadcast points.
   */
  void clearBroadcastPoints();

  /**
   * Retrieves all active broadcast points covering a specific location.
   *
   * @param location the location
   * @return collection of matching broadcast points
   */
  @NotNull Collection<BroadcastPoint> getBroadcastersInRange(final @NotNull VoiceLocation location);

  /**
   * Saves all broadcast points to disk.
   */
  void save();

  /**
   * Reloads all broadcast points from disk.
   */
  void load();

}
