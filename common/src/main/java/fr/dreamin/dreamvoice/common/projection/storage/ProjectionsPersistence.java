package fr.dreamin.dreamvoice.common.projection.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.dreamin.dreamvoice.api.projection.model.VoiceProjection;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.storage.model.LocationData;
import fr.dreamin.dreamvoice.common.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ProjectionsPersistence {

  public record ProjectionData(
    UUID uuid,
    UUID playerUuid,
    LocationData anchorLocation,
    double distance,
    boolean emitVoiceAtAnchor,
    boolean emitVoiceAtPlayer,
    boolean hearAnchorEnvironment,
    boolean hearPlayerEnvironment,
    boolean applyVoiceWall,
    String filterId,
    UUID anchorEntityUuid
  ) {}

  private ProjectionsPersistence() {}

  public static void save(final @NotNull VoiceProjectionService service, final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, "data.json");
    final var dataList = new ArrayList<ProjectionData>();

    for (final var projection : service.getProjections()) {
      final var loc = LocationData.fromVoiceLocation(projection.getAnchorLocation());

      dataList.add(new ProjectionData(
        projection.getUuid(),
        projection.getPlayerUuid(),
        loc,
        projection.getDistance(),
        projection.isEmitVoiceAtAnchor(),
        projection.isEmitVoiceAtPlayer(),
        projection.isHearAnchorEnvironment(),
        projection.isHearPlayerEnvironment(),
        projection.isApplyVoiceWall(),
        projection.getFilterId(),
        projection.getAnchorEntityUuid()
      ));
    }

    try {
      JsonUtils.save(file, dataList);
    } catch (Exception e) {
      platform.logError("[DreamVoice] Error saving projections", e);
    }
  }

  public static void load(final @NotNull VoiceProjectionService service, final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    var file = new File(targetDir, "data.json");
    if (!file.exists()) {
      file = new File(targetDir, "projections.json");
      if (!file.exists())
        return;
    }

    try {
      final List<ProjectionData> dataList = JsonUtils.load(file, new TypeReference<>() {});
      if (dataList == null)
        return;

      for (final var data : dataList) {
        if (data.anchorLocation() == null)
          continue;

        final var loc = data.anchorLocation().toVoiceLocation();
        if (loc == null)
          continue;

        final var proj = new VoiceProjection(
          data.uuid() != null ? data.uuid() : UUID.randomUUID(),
          data.playerUuid(),
          loc
        );

        proj.setDistance(data.distance());
        proj.setEmitVoiceAtAnchor(data.emitVoiceAtAnchor());
        proj.setEmitVoiceAtPlayer(data.emitVoiceAtPlayer());
        proj.setHearAnchorEnvironment(data.hearAnchorEnvironment());
        proj.setHearPlayerEnvironment(data.hearPlayerEnvironment());
        proj.setApplyVoiceWall(data.applyVoiceWall());
        proj.setFilterId(data.filterId());
        proj.setAnchorEntityUuid(data.anchorEntityUuid());

        service.register(proj);
      }
    } catch (Exception e) {
      platform.logError("[DreamVoice] Error loading projections", e);
    }
  }

}
