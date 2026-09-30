package fr.dreamin.dreamvoice.common.room.data;

import fr.dreamin.dreamvoice.api.room.model.RoomReverbConfig;

import java.util.List;

/**
 * Serialized acoustic room entry for data.json.
 */
public record AcousticRoomData(
  String id,
  String name,
  String presetId,
  Integer isolationPctOverride,
  RoomReverbConfig reverbOverride,
  List<String> additionalFilters,
  List<CuboidData> cuboids
) {}
