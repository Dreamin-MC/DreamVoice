package fr.dreamin.dreamvoice.fabric.network.handler;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamServerReceiver;
import fr.dreamin.dreamvoice.fabric.network.model.config.ClientBoundVisualizerSyncPacket;
import fr.dreamin.dreamvoice.fabric.network.model.config.ServerBoundRequestConfigSyncPacket;
import fr.dreamin.dreamvoice.fabric.network.model.config.ServerBoundVisualizerTogglePacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ConfigServerNetworkHandler {

  @DreamServerReceiver
  public static void handleVisualizerToggle(ServerBoundVisualizerTogglePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      ServerPlayNetworking.send(player, new ClientBoundVisualizerSyncPacket(packet.enabled()));
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[ConfigNetwork] Failed to toggle visualizer: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleRequestConfigSync(ServerBoundRequestConfigSyncPacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var module = packet.targetModule().toUpperCase();
      switch (module) {
        case "SPEAKERS" -> SpeakerServerNetworkHandler.syncSpeakersToAll(server);
        case "WIRETAPS" -> WiretapServerNetworkHandler.syncWiretapsToAll(server);
        case "ROOMS" -> RoomServerNetworkHandler.syncRoomsToAll(server);
        case "RADIOS" -> RadioServerNetworkHandler.syncRadioToAll(server);
        case "TRANSMITTERS" -> TransmitterServerNetworkHandler.syncTransmittersToAll(server);
        case "RECORDINGS" -> RecordingServerNetworkHandler.syncRecordingsToAll(server);
        case "FILTERS" -> FilterServerNetworkHandler.syncPlayerFilters(server, player.getUUID());
        default -> {
          // Sync ALL
          SpeakerServerNetworkHandler.syncSpeakersToAll(server);
          WiretapServerNetworkHandler.syncWiretapsToAll(server);
          RoomServerNetworkHandler.syncRoomsToAll(server);
          RadioServerNetworkHandler.syncRadioToAll(server);
          TransmitterServerNetworkHandler.syncTransmittersToAll(server);
          RecordingServerNetworkHandler.syncRecordingsToAll(server);
          FilterServerNetworkHandler.syncPlayerFilters(server, player.getUUID());
        }
      }
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[ConfigNetwork] Failed to sync config: {}", e.getMessage(), e);
    }
  }
}
