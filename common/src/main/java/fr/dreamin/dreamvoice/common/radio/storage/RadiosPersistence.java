package fr.dreamin.dreamvoice.common.radio.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.dreamin.dreamvoice.api.radio.model.RadioChannel;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.utils.JsonUtils;
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

  private RadiosPersistence() {}

  public static void save(final @NotNull VoiceRadioService service, final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, "data.json");
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
      JsonUtils.save(file, dataList);
    } catch (Exception e) {
      platform.logError("[DreamVoice] Error saving radios", e);
    }
  }

  public static void load(final @NotNull VoiceRadioService service, final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    var file = new File(targetDir, "data.json");
    if (!file.exists()) {
      file = new File(targetDir, "radios.json");
      if (!file.exists())
        return;
    }

    try {
      final List<RadioData> dataList = JsonUtils.load(file, new TypeReference<>() {});
      if (dataList == null)
        return;

      for (final var data : dataList) {
        final var channel = new RadioChannel(data.name());
        channel.setRogerBeep(data.rogerBeep());
        channel.setFilterId(data.filterId());

        if (data.members() != null)
          channel.getMembers().addAll(data.members());

        service.register(channel);
      }
    } catch (Exception e) {
      platform.logError("[DreamVoice] Error loading radios", e);
    }
  }

}
