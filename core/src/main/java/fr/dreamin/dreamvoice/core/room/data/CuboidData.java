package fr.dreamin.dreamvoice.core.room.data;

import fr.dreamin.dreamvoice.core.storage.model.LocationData;

/**
 * Storage representation of a Cuboid boundary.
 */
public record CuboidData(
  LocationData locA,
  LocationData locB
) {}
