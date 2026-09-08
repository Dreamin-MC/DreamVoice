package fr.dreamin.dreamvoice.core.room.data;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.dreamin.dreamapi.api.config.Configurations;
import fr.dreamin.dreamapi.api.cuboid.Cuboid;
import fr.dreamin.dreamvoice.api.room.model.AcousticRoom;
import fr.dreamin.dreamvoice.api.room.service.VoiceRoomService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.storage.model.LocationData;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence handler for acoustic rooms (modules/room/data.json).
 */
public final class VoiceRoomPersistence {

  public static void save(final @NotNull VoiceRoomService service, final @NotNull File targetDir) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, "data.json");
    final var dataList = new ArrayList<AcousticRoomData>();

    for (final var room : service.getRooms()) {
      final var cuboidDataList = new ArrayList<CuboidData>();
      for (final var cuboid : room.getCuboids()) {
        final var locA = LocationData.fromLocation(cuboid.getLocA());
        final var locB = LocationData.fromLocation(cuboid.getLocB());
        if (locA != null && locB != null)
          cuboidDataList.add(new CuboidData(locA, locB));
      }

      dataList.add(new AcousticRoomData(
        room.getId(),
        room.getName(),
        room.getPresetId(),
        room.getIsolationPctOverride(),
        room.getReverbOverride(),
        room.getAdditionalFilters(),
        cuboidDataList
      ));
    }

    try {
      Configurations.saveJson(file, dataList);
    } catch (final Exception e) {
      DreamVoice.getInstance().getLogger().severe("[DreamVoice] Error saving acoustic rooms: " + e.getMessage());
    }
  }

  public static void load(final @NotNull VoiceRoomService service, final @NotNull File targetDir) {
    final var file = new File(targetDir, "data.json");
    if (!file.exists())
      return;

    try {
      final List<AcousticRoomData> dataList = Configurations.loadJson(file, new TypeReference<>() {});
      if (dataList == null)
        return;

    for (final var data : dataList) {
      final var cuboids = new ArrayList<Cuboid>();
      if (data.cuboids() != null) {
        for (final var cData : data.cuboids()) {
          final var locA = cData.locA() != null ? cData.locA().toLocation() : null;
          final var locB = cData.locB() != null ? cData.locB().toLocation() : null;
          if (locA != null && locB != null)
            cuboids.add(new Cuboid(locA, locB));
        }
      }

      final var room = AcousticRoom.builder()
        .id(data.id())
        .name(data.name())
        .presetId(data.presetId() != null ? data.presetId() : "default")
        .isolationPctOverride(data.isolationPctOverride())
        .reverbOverride(data.reverbOverride())
        .additionalFilters(data.additionalFilters() != null ? data.additionalFilters() : new ArrayList<>())
        .cuboids(cuboids)
        .build();

      service.registerRoom(room);
    }
    } catch (final Exception e) {
      DreamVoice.getInstance().getLogger().severe("[DreamVoice] Error loading acoustic rooms: " + e.getMessage());
    }
  }

}
