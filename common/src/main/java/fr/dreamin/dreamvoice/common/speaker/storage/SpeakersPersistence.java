package fr.dreamin.dreamvoice.common.speaker.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import fr.dreamin.dreamvoice.api.speaker.model.SpeakerMode;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.storage.model.LocationData;
import fr.dreamin.dreamvoice.common.utils.JsonUtils;
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

  private SpeakersPersistence() {}

  public static void save(final @NotNull VoiceSpeakerService service, final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, "data.json");
    final var dataList = new ArrayList<SpeakerData>();

    for (final var speaker : service.getSpeakers()) {
      final var loc = LocationData.fromVoiceLocation(speaker.getLocation());

      dataList.add(new SpeakerData(
        speaker.getUuid(),
        speaker.getName(),
        loc,
        speaker.getDistance(),
        speaker.getMode(),
        speaker.getAllowedSpeakers(),
        speaker.getTargetEntityUuid()
      ));
    }

    try {
      JsonUtils.save(file, dataList);
    } catch (Exception e) {
      platform.logError("[DreamVoice] Error saving speakers", e);
    }
  }

  public static void load(final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    var file = new File(targetDir, "data.json");
    if (!file.exists()) {
      file = new File(targetDir, "speakers.json");
      if (!file.exists())
        return;
    }

    try {
      final List<SpeakerData> dataList = JsonUtils.load(file, new TypeReference<>() {});
      if (dataList == null)
        return;

      for (final var data : dataList) {
        if (data.location() == null)
          continue;

        final var loc = data.location().toVoiceLocation();
        if (loc == null)
          continue;

        final var builder = Speaker.builder()
          .uuid(data.uuid() != null ? data.uuid() : UUID.randomUUID())
          .name(data.name())
          .location(loc)
          .targetEntity(data.targetEntityUuid())
          .mode(data.mode() != null ? data.mode() : SpeakerMode.GLOBAL);

        if (data.distance() != null)
          builder.distance(data.distance());

        if (data.allowedSpeakers() != null)
          for (final var allowed : data.allowedSpeakers())
            builder.allowSpeaker(allowed);

        builder.build();
      }
    } catch (Exception e) {
      platform.logError("[DreamVoice] Error loading speakers", e);
    }
  }

}
