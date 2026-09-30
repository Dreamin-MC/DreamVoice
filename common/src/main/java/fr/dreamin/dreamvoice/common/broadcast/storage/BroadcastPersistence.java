package fr.dreamin.dreamvoice.common.broadcast.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.dreamin.dreamvoice.api.broadcast.model.BroadcastPoint;
import fr.dreamin.dreamvoice.api.broadcast.service.VoiceBroadcastService;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.storage.model.LocationData;
import fr.dreamin.dreamvoice.common.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BroadcastPersistence {

  public record BroadcastPointData(
    UUID uuid,
    String name,
    LocationData location,
    double radius,
    boolean allSpeakers,
    Set<String> targetSpeakers,
    String filterId,
    boolean enabled,
    UUID targetEntityUuid
  ) {}

  private BroadcastPersistence() {}

  public static void save(final @NotNull VoiceBroadcastService service, final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, "data.json");
    final var dataList = new ArrayList<BroadcastPointData>();

    for (final var point : service.getBroadcastPoints()) {
      dataList.add(new BroadcastPointData(
        point.getUuid(),
        point.getName(),
        LocationData.fromVoiceLocation(point.getLocation()),
        point.getRadius(),
        point.isAllSpeakers(),
        point.getTargetSpeakers(),
        point.getFilterId(),
        point.isEnabled(),
        point.getTargetEntityUuid()
      ));
    }

    try {
      JsonUtils.save(file, dataList);
    } catch (Exception e) {
      platform.logError("Failed to save broadcast points", e);
    }
  }

  public static void load(final @NotNull VoiceBroadcastService service, final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    final var file = new File(targetDir, "data.json");
    if (!file.exists())
      return;

    try {
      final List<BroadcastPointData> list = JsonUtils.load(file, new TypeReference<>() {});
      if (list == null)
        return;

      for (final var data : list) {
        if (data.location() == null)
          continue;

        final var loc = data.location().toVoiceLocation();
        if (loc == null)
          continue;

        final var point = new BroadcastPoint(
          data.uuid(),
          data.name(),
          loc,
          data.radius(),
          data.allSpeakers(),
          data.filterId(),
          data.enabled()
        );

        if (data.targetSpeakers() != null)
          for (final var spk : data.targetSpeakers())
            point.linkSpeaker(spk);

        point.setTargetEntityUuid(data.targetEntityUuid());

        service.register(point);
      }
    } catch (Exception e) {
      platform.logError("Failed to load broadcast points", e);
    }
  }

}
