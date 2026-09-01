package fr.dreamin.dreamvoice.api.transmitter.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamvoice.api.transmitter.model.ReceiverConfig;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.Collection;

public interface VoiceTransmitterService {

  // ------------------------------------------------
  // Core
  // ------------------------------------------------

  @NotNull VoicechatServerApi getAPI();

  void init(@NotNull VoicechatServerApi api);

  // ------------------------------------------------
  // Transmitter
  // ------------------------------------------------

  boolean isTransmitter(@NotNull Player player);
  boolean isTransmitter(@NotNull UUID uuid);

  void createTransmitter(@NotNull Player player);
  void createTransmitter(@NotNull UUID uuid);

  void removeTransmitter(@NotNull Player player);
  void removeTransmitter(@NotNull UUID uuid);

  // ------------------------------------------------
  // Receivers
  // ------------------------------------------------

  @NotNull Collection<ReceiverConfig> getReceivers(@NotNull Player player);
  @NotNull Collection<ReceiverConfig> getReceivers(@NotNull UUID uuid);

  void addReceiver(@NotNull Player transmitter, @NotNull Player receiver);
  void addReceiver(@NotNull UUID transmitter, @NotNull UUID receiver);

  void addReceiver(@NotNull Player transmitter, @NotNull Player receiver, double maxDistance);
  void addReceiver(@NotNull UUID transmitter, @NotNull UUID receiver, double maxDistance);

  void addReceiver(@NotNull UUID transmitter, @NotNull ReceiverConfig receiverConfig);

  void removeReceiver(@NotNull Player transmitter, @NotNull Player receiver);
  void removeReceiver(@NotNull UUID transmitter, @NotNull UUID receiver);

  void clearReceivers(@NotNull Player transmitter);
  void clearReceivers(@NotNull UUID transmitter);

  // ------------------------------------------------
  // Global Operations
  // ------------------------------------------------

  void addReceiverToAll(@NotNull Player receiver);
  void addReceiverToAll(@NotNull UUID receiver);

  void addReceiverToAll(@NotNull Player receiver, double maxDistance);
  void addReceiverToAll(@NotNull UUID receiver, double maxDistance);

  void removeReceiverFromAll(@NotNull Player receiver);
  void removeReceiverFromAll(@NotNull UUID receiver);

  void save();

  void load();
}

