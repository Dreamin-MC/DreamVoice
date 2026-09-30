package fr.dreamin.dreamvoice.common.transmitter.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.dreamin.dreamvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.utils.JsonUtils;
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

  private TransmittersPersistence() {}

  public static void save(final @NotNull VoiceTransmitterService service, final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, "data.json");
    final var dataList = new ArrayList<TransmitterData>();

    for (final var uuid : platform.getOnlinePlayers()) {
      if (!service.isTransmitter(uuid))
        continue;

      final var receivers = service.getReceivers(uuid);
      final var receiverDataList = new ArrayList<TransmitterData.ReceiverData>();

      for (final var rc : receivers)
        receiverDataList.add(new TransmitterData.ReceiverData(rc.getUuid(), rc.getMaxDistance()));

      dataList.add(new TransmitterData(uuid, receiverDataList));
    }

    try {
      JsonUtils.save(file, dataList);
    } catch (Exception e) {
      platform.logError("[DreamVoice] Error saving transmitters", e);
    }
  }

  public static void load(final @NotNull VoiceTransmitterService service, final @NotNull File targetDir, final @NotNull VoicePlatform platform) {
    var file = new File(targetDir, "data.json");
    if (!file.exists()) {
      file = new File(targetDir, "transmitters.json");
      if (!file.exists())
        return;
    }

    try {
      final List<TransmitterData> dataList = JsonUtils.load(file, new TypeReference<>() {});
      if (dataList == null)
        return;

      for (final var data : dataList) {
        final var uuid = data.transmitterUuid();
        service.createTransmitter(uuid);

        if (data.receivers() != null)
          for (final var r : data.receivers())
            if (r.maxDistance() != null)
              service.addReceiver(uuid, r.receiverUuid(), r.maxDistance());
            else
              service.addReceiver(uuid, r.receiverUuid());
      }
    } catch (Exception e) {
      platform.logError("[DreamVoice] Error loading transmitters", e);
    }
  }

}
