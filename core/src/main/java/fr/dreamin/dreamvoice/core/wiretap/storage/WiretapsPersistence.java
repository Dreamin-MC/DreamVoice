package fr.dreamin.dreamvoice.core.wiretap.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.dreamin.dreamapi.api.config.Configurations;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.storage.model.LocationData;
import org.bukkit.Bukkit;
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

  public static void save(final @NotNull VoiceWiretapService service, final @NotNull File targetDir) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, "wiretaps.json");
    final var dataList = new ArrayList<WiretapData>();

    for (final var wiretap : service.getWiretaps()) {
      final var loc = LocationData.fromLocation(wiretap.getLocation());
      if (loc == null)
        continue;

      final var targetEntity = wiretap.getTargetEntity();
      final var entityUuid = (targetEntity != null && targetEntity.isValid()) ? targetEntity.getUniqueId() : null;

      dataList.add(new WiretapData(
        wiretap.getUuid(),
        wiretap.getName(),
        loc,
        wiretap.getDistance(),
        wiretap.isApplyVoiceWall(),
        wiretap.getFilterId(),
        wiretap.getListeners(),
        entityUuid
      ));
    }

    try {
      Configurations.saveJson(file, dataList);
    } catch (Exception e) {
      DreamVoice.getInstance().getLogger().severe("[DreamVoice] Error saving wiretaps: " + e.getMessage());
    }
  }

  public static void load(final @NotNull VoiceWiretapService service, final @NotNull File targetDir) {
    final var file = new File(targetDir, "wiretaps.json");
    if (!file.exists())
      return;

    try {
      final List<WiretapData> dataList = Configurations.loadJson(file, new TypeReference<>() {});
      if (dataList == null)
        return;

      for (final var data : dataList) {
        if (data.location() == null)
          continue;

        final var loc = data.location().toLocation();
        if (loc == null)
          continue;

        final var wiretap = new VoiceWiretap(
          data.uuid() != null ? data.uuid() : UUID.randomUUID(),
          data.name(),
          loc
        );

        wiretap.setDistance(data.distance() > 0 ? data.distance() : 12.0);
        wiretap.setApplyVoiceWall(data.applyVoiceWall());
        wiretap.setFilterId(data.filterId());

        if (data.listeners() != null)
          for (final var listener : data.listeners())
            wiretap.addListener(listener);

        if (data.targetEntityUuid() != null) {
          final var entity = Bukkit.getEntity(data.targetEntityUuid());
          if (entity != null && entity.isValid())
            wiretap.setTargetEntity(entity);
        }

        service.register(wiretap);
      }
    } catch (Exception e) {
      DreamVoice.getInstance().getLogger().severe("[DreamVoice] Error loading wiretaps: " + e.getMessage());
    }
  }

}
