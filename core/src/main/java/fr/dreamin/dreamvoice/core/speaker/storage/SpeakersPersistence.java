package fr.dreamin.dreamvoice.core.speaker.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.dreamin.dreamapi.api.config.Configurations;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import fr.dreamin.dreamvoice.api.speaker.model.SpeakerMode;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.storage.model.LocationData;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SpeakersPersistence {

  public record SpeakerData(
    UUID uuid,
    String name,
    LocationData location,
    Float distance,
    SpeakerMode mode,
    Set<UUID> allowedSpeakers,
    UUID targetEntityUuid
  ) {}

  public static void save(final @NotNull VoiceSpeakerService service, final @NotNull File targetDir) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, "speakers.json");
    final var dataList = new ArrayList<SpeakerData>();

    for (final var speaker : service.getSpeakers()) {
      final var loc = LocationData.fromLocation(speaker.getLocation());
      if (loc == null)
        continue;

      final var targetEntity = speaker.getTargetEntity();
      final var entityUuid = (targetEntity != null && targetEntity.isValid()) ? targetEntity.getUniqueId() : null;

      dataList.add(new SpeakerData(
        speaker.getUuid(),
        speaker.getName(),
        loc,
        speaker.getDistance(),
        speaker.getMode(),
        speaker.getAllowedSpeakers(),
        entityUuid
      ));
    }

    try {
      Configurations.saveJson(file, dataList);
    } catch (Exception e) {
      DreamVoice.getInstance().getLogger().severe("[DreamVoice] Error saving speakers: " + e.getMessage());
    }
  }

  public static void load(final @NotNull File targetDir) {
    final var file = new File(targetDir, "speakers.json");
    if (!file.exists())
      return;

    try {
      final List<SpeakerData> dataList = Configurations.loadJson(file, new TypeReference<>() {});
      if (dataList == null)
        return;

      for (final var data : dataList) {
        if (data.location() == null)
          continue;

        final var loc = data.location().toLocation();
        if (loc == null)
          continue;

        final var builder = Speaker.builder()
          .uuid(data.uuid() != null ? data.uuid() : UUID.randomUUID())
          .name(data.name())
          .location(loc)
          .mode(data.mode() != null ? data.mode() : SpeakerMode.GLOBAL);

        if (data.distance() != null)
          builder.distance(data.distance());

        if (data.allowedSpeakers() != null)
          for (final var allowed : data.allowedSpeakers())
            builder.allowSpeaker(allowed);

        final var speaker = builder.build();

        if (data.targetEntityUuid() != null) {
          final var entity = Bukkit.getEntity(data.targetEntityUuid());
          if (entity != null && entity.isValid())
            speaker.setTargetEntity(entity);
        }
      }
    } catch (Exception e) {
      DreamVoice.getInstance().getLogger().severe("[DreamVoice] Error loading speakers: " + e.getMessage());
    }
  }

}
