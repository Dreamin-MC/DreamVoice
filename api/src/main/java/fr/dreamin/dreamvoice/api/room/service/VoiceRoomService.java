package fr.dreamin.dreamvoice.api.room.service;

import fr.dreamin.dreamapi.api.cuboid.Cuboid;
import fr.dreamin.dreamvoice.api.room.model.AcousticRoom;
import fr.dreamin.dreamvoice.api.room.model.RoomPreset;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing acoustic rooms, soundproofing boundaries, and spatial reverberation zones.
 */
public interface VoiceRoomService {

  /**
   * Registers a new acoustic room.
   *
   * @param room the room instance
   */
  void registerRoom(final @NotNull AcousticRoom room);

  /**
   * Deletes an acoustic room by ID.
   *
   * @param roomId room identifier
   */
  void unregisterRoom(final @NotNull String roomId);

  /**
   * Retrieves an acoustic room by its identifier.
   *
   * @param roomId room identifier
   * @return optional room
   */
  @NotNull Optional<AcousticRoom> getRoom(final @NotNull String roomId);

  /**
   * Retrieves all registered acoustic rooms.
   *
   * @return collection of rooms
   */
  @NotNull Collection<AcousticRoom> getRooms();

  /**
   * Finds the acoustic room encompassing a specific world location.
   *
   * @param location world location
   * @return optional room
   */
  @NotNull Optional<AcousticRoom> getRoomAt(final @NotNull Location location);

  /**
   * Finds the acoustic room a player is currently located in.
   *
   * @param playerUuid player UUID
   * @return optional room
   */
  @NotNull Optional<AcousticRoom> getPlayerRoom(final @NotNull UUID playerUuid);

  /**
   * Registers or updates an acoustic preset.
   *
   * @param preset preset definition
   */
  void registerPreset(final @NotNull RoomPreset preset);

  /**
   * Retrieves an acoustic preset by ID.
   *
   * @param presetId preset ID
   * @return optional preset
   */
  @NotNull Optional<RoomPreset> getPreset(final @NotNull String presetId);

  /**
   * Retrieves all available room presets.
   *
   * @return collection of presets
   */
  @NotNull Collection<RoomPreset> getPresets();

  /**
   * Returns effective sound isolation percentage for a given room (0 to 100).
   *
   * @param room acoustic room
   * @return isolation percentage
   */
  int getEffectiveIsolation(final @NotNull AcousticRoom room);

  /**
   * Checks whether two players can hear each other taking acoustic room boundaries into account.
   *
   * @param senderUuid   sender player UUID
   * @param receiverUuid receiver player UUID
   * @return attenuation loss in decibels (e.g. 0.0 for direct, 100.0 for completely blocked)
   */
  double calculateRoomAttenuationDb(final @NotNull UUID senderUuid, final @NotNull UUID receiverUuid);

  /**
   * Reloads rooms and presets configuration.
   */
  void reload();

  /**
   * Saves rooms data to storage.
   */
  void save();

}
