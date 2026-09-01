package fr.dreamin.dreamvoice.core.radio.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.dreamin.dreamapi.api.config.Configurations;
import fr.dreamin.dreamvoice.api.radio.model.RadioChannel;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RadiosPersistence {

  public record RadioData(
    String name,
    Set<UUID> members,
    boolean rogerBeep,
    String filterId
  ) {}

  public static void save(final @NotNull VoiceRadioService service, final @NotNull File targetDir) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, "radios.json");
    final var dataList = new ArrayList<RadioData>();

    for (final var channel : service.getChannels()) {
      dataList.add(new RadioData(
        channel.getName(),
        channel.getMembers(),
        channel.isRogerBeep(),
        channel.getFilterId()
      ));
    }

    try {
      Configurations.saveJson(file, dataList);
    } catch (Exception e) {
      Bukkit.getLogger().severe("[DreamVoice] Error saving radios: " + e.getMessage());
    }
  }

  public static void load(final @NotNull VoiceRadioService service, final @NotNull File targetDir) {
    final var file = new File(targetDir, "radios.json");
    if (!file.exists())
      return;

    try {
      final List<RadioData> dataList = Configurations.loadJson(file, new TypeReference<>() {});
      if (dataList == null)
        return;

      for (final var data : dataList) {
        if (data.name() == null)
          continue;

        final var channel = service.getOrCreateChannel(data.name());
        channel.setRogerBeep(data.rogerBeep());
        channel.setFilterId(data.filterId());

        if (data.members() != null)
          for (final var member : data.members())
            channel.addMember(member);
      }
    } catch (Exception e) {
      Bukkit.getLogger().severe("[DreamVoice] Error loading radios: " + e.getMessage());
    }
  }

}
