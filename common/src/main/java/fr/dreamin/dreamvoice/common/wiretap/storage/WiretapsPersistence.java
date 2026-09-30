package fr.dreamin.dreamvoice.common.wiretap.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.storage.model.LocationData;
import fr.dreamin.dreamvoice.common.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WiretapsPersistence {

  public record WiretapData(
    UUID uuid,
    String name,
    LocationData location,
    double distance,
    boolean applyVoiceWall,
    String filterId,
    Set<UUID> listeners,
    UUID targetEntityUuid
  ) {}

  private WiretapsPersistence() {}

  public static void save(final @NotNull VoiceWiretapService service, final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, "data.json");
    final var dataList = new ArrayList<WiretapData>();

    for (final var wiretap : service.getWiretaps()) {
      final var loc = LocationData.fromVoiceLocation(wiretap.getLocation());

      dataList.add(new WiretapData(
        wiretap.getUuid(),
        wiretap.getName(),
        loc,
        wiretap.getDistance(),
        wiretap.isApplyVoiceWall(),
        wiretap.getFilterId(),
        wiretap.getListeners(),
        wiretap.getTargetEntityUuid()
      ));
    }

    try {
      JsonUtils.save(file, dataList);
    } catch (Exception e) {
      platform.logError("[DreamVoice] Error saving wiretaps", e);
    }
  }

  public static void load(final @NotNull VoiceWiretapService service, final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    var file = new File(targetDir, "data.json");
    if (!file.exists()) {
      file = new File(targetDir, "wiretaps.json");
      if (!file.exists())
        return;
    }

    try {
      final List<WiretapData> dataList = JsonUtils.load(file, new TypeReference<>() {});
      if (dataList == null)
        return;

      for (final var data : dataList) {
        if (data.location() == null)
          continue;

        final var loc = data.location().toVoiceLocation();
        if (loc == null)
          continue;

        final var wiretap = new VoiceWiretap(
          data.uuid() != null ? data.uuid() : UUID.randomUUID(),
          data.name(),
          loc
        );

        wiretap.setDistance(data.distance());
        wiretap.setApplyVoiceWall(data.applyVoiceWall());
        wiretap.setFilterId(data.filterId());
        wiretap.setTargetEntityUuid(data.targetEntityUuid());

        if (data.listeners() != null)
          for (final var listener : data.listeners())
            wiretap.addListener(listener);

        service.register(wiretap);
      }
    } catch (Exception e) {
      platform.logError("[DreamVoice] Error loading wiretaps", e);
    }
  }

}
