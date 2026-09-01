package fr.dreamin.dreamvoice.core.transmitter.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.dreamin.dreamapi.api.config.Configurations;
import fr.dreamin.dreamvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TransmittersPersistence {

  public record TransmitterData(
    UUID transmitterUuid,
    List<ReceiverData> receivers
  ) {
    public record ReceiverData(
      UUID receiverUuid,
      Double maxDistance
    ) {}
  }

  public static void save(final @NotNull VoiceTransmitterService service, final @NotNull File targetDir) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, "transmitters.json");
    final var dataList = new ArrayList<TransmitterData>();

    for (final var player : Bukkit.getOnlinePlayers()) {
      final var uuid = player.getUniqueId();
      if (!service.isTransmitter(uuid))
        continue;

      final var receivers = service.getReceivers(uuid);
      final var receiverDataList = new ArrayList<TransmitterData.ReceiverData>();

      for (final var rc : receivers)
        receiverDataList.add(new TransmitterData.ReceiverData(rc.getUuid(), rc.getMaxDistance()));

      dataList.add(new TransmitterData(uuid, receiverDataList));
    }

    try {
      Configurations.saveJson(file, dataList);
    } catch (Exception e) {
      DreamVoice.getInstance().getLogger().severe("[DreamVoice] Error saving transmitters: " + e.getMessage());
    }
  }

  public static void load(final @NotNull VoiceTransmitterService service, final @NotNull File targetDir) {
    final var file = new File(targetDir, "transmitters.json");
    if (!file.exists())
      return;

    try {
      final List<TransmitterData> dataList = Configurations.loadJson(file, new TypeReference<>() {});
      if (dataList == null)
        return;

      for (final var data : dataList) {
        if (data.transmitterUuid() == null)
          continue;

        service.createTransmitter(data.transmitterUuid());

        if (data.receivers() != null)
          for (final var r : data.receivers())
            if (r.receiverUuid() != null) {
              if (r.maxDistance() != null)
                service.addReceiver(data.transmitterUuid(), r.receiverUuid(), r.maxDistance());
              else
                service.addReceiver(data.transmitterUuid(), r.receiverUuid());
            }
      }
    } catch (Exception e) {
      DreamVoice.getInstance().getLogger().severe("[DreamVoice] Error loading transmitters: " + e.getMessage());
    }
  }

}
