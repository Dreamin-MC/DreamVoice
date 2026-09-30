package fr.dreamin.dreamvoice.common.room.data;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.dreamin.dreamvoice.api.room.model.AcousticRoom;
import fr.dreamin.dreamvoice.api.room.model.VoiceCuboid;
import fr.dreamin.dreamvoice.api.room.service.VoiceRoomService;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence handler for acoustic rooms (modules/room/data.json).
 */
public final class VoiceRoomPersistence {

  private VoiceRoomPersistence() {}

  public static void save(final @NotNull VoiceRoomService service, final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, "data.json");
    final var dataList = new ArrayList<AcousticRoomData>();

    for (final var room : service.getRooms()) {
      final var cuboidDataList = new ArrayList<CuboidData>();
      for (final var cuboid : room.getCuboids()) {
        final var cData = CuboidData.fromVoiceCuboid(cuboid);
        if (cData != null)
          cuboidDataList.add(cData);
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
      JsonUtils.save(file, dataList);
    } catch (final Exception e) {
      platform.logError("[DreamVoice] Error saving acoustic rooms", e);
    }
  }

  public static void load(final @NotNull VoiceRoomService service, final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    final var file = new File(targetDir, "data.json");
    if (!file.exists())
      return;

    try {
      final List<AcousticRoomData> dataList = JsonUtils.load(file, new TypeReference<>() {});
      if (dataList == null)
        return;

      for (final var data : dataList) {
        final var cuboids = new ArrayList<VoiceCuboid>();
        if (data.cuboids() != null)
          for (final var cData : data.cuboids()) {
            final var cuboid = cData.toVoiceCuboid();
            if (cuboid != null)
              cuboids.add(cuboid);
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
      platform.logError("[DreamVoice] Error loading acoustic rooms", e);
    }
  }

}
