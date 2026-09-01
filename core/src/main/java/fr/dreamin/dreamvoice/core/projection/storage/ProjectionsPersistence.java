package fr.dreamin.dreamvoice.core.projection.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.dreamin.dreamapi.api.config.Configurations;
import fr.dreamin.dreamvoice.api.projection.model.VoiceProjection;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.storage.model.LocationData;
import org.bukkit.Bukkit;
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

  public static void save(final @NotNull VoiceProjectionService service, final @NotNull File targetDir) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, "projections.json");
    final var dataList = new ArrayList<ProjectionData>();

    for (final var projection : service.getProjections()) {
      final var loc = LocationData.fromLocation(projection.getAnchorLocation());
      if (loc == null)
        continue;

      final var anchorEntity = projection.getAnchorEntity();
      final var entityUuid = (anchorEntity != null && anchorEntity.isValid()) ? anchorEntity.getUniqueId() : null;

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
        entityUuid
      ));
    }

    try {
      Configurations.saveJson(file, dataList);
    } catch (Exception e) {
      DreamVoice.getInstance().getLogger().severe("[DreamVoice] Error saving projections: " + e.getMessage());
    }
  }

  public static void load(final @NotNull VoiceProjectionService service, final @NotNull File targetDir) {
    final var file = new File(targetDir, "projections.json");
    if (!file.exists())
      return;

    try {
      final List<ProjectionData> dataList = Configurations.loadJson(file, new TypeReference<>() {});
      if (dataList == null)
        return;

      for (final var data : dataList) {
        if (data.anchorLocation() == null || data.playerUuid() == null)
          continue;

        final var loc = data.anchorLocation().toLocation();
        if (loc == null)
          continue;

        final var projection = new VoiceProjection(
          data.uuid() != null ? data.uuid() : UUID.randomUUID(),
          data.playerUuid(),
          loc
        );

        projection.setDistance(data.distance() > 0 ? data.distance() : 16.0);
        projection.setEmitVoiceAtAnchor(data.emitVoiceAtAnchor());
        projection.setEmitVoiceAtPlayer(data.emitVoiceAtPlayer());
        projection.setHearAnchorEnvironment(data.hearAnchorEnvironment());
        projection.setHearPlayerEnvironment(data.hearPlayerEnvironment());
        projection.setApplyVoiceWall(data.applyVoiceWall());
        projection.setFilterId(data.filterId());

        if (data.anchorEntityUuid() != null) {
          final var entity = Bukkit.getEntity(data.anchorEntityUuid());
          if (entity != null && entity.isValid())
            projection.setAnchorEntity(entity);
        }

        service.register(projection);
      }
    } catch (Exception e) {
      DreamVoice.getInstance().getLogger().severe("[DreamVoice] Error loading projections: " + e.getMessage());
    }
  }

}
