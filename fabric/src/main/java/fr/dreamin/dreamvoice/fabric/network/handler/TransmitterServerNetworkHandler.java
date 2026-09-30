package fr.dreamin.dreamvoice.fabric.network.handler;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamServerReceiver;
import fr.dreamin.dreamvoice.fabric.network.model.transmitter.ClientBoundTransmitterSyncPacket;
import fr.dreamin.dreamvoice.fabric.network.model.transmitter.ServerBoundTransmitterCreatePacket;
import fr.dreamin.dreamvoice.fabric.network.model.transmitter.ServerBoundTransmitterDeletePacket;
import fr.dreamin.dreamvoice.fabric.network.model.transmitter.ServerBoundTransmitterTogglePacket;
import fr.dreamin.dreamvoice.fabric.network.utils.JsonUtils;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class TransmitterServerNetworkHandler {

  @DreamServerReceiver
  public static void handleTransmitterCreate(ServerBoundTransmitterCreatePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var node = JsonUtils.MAPPER.readTree(packet.json());
      final var api = DreamVoiceAPI.get();
      if (api.getTransmitterService() == null) return;

      final var id = node.has("id") ? UUID.fromString(node.get("id").asText()) : player.getUUID();
      api.getTransmitterService().createTransmitter(id);

      if (node.has("receivers") && node.get("receivers").isArray()) {
        final var dist = node.has("distance") ? node.get("distance").asDouble() : 0.0;
        for (final var r : node.get("receivers")) {
          final var rUuid = UUID.fromString(r.asText());
          if (dist > 0.0)
            api.getTransmitterService().addReceiver(id, rUuid, dist);
          else
            api.getTransmitterService().addReceiver(id, rUuid);
        }
      }

      syncTransmittersToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[TransmitterNetwork] Failed to create transmitter: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleTransmitterToggle(ServerBoundTransmitterTogglePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getTransmitterService() == null) return;
      final var id = packet.transmitterId().isBlank() ? player.getUUID() : UUID.fromString(packet.transmitterId());
      api.getTransmitterService().setTransmitter(id, packet.active());
      syncTransmittersToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[TransmitterNetwork] Failed to toggle transmitter: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleTransmitterDelete(ServerBoundTransmitterDeletePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getTransmitterService() == null) return;
      final var id = packet.transmitterId().isBlank() ? player.getUUID() : UUID.fromString(packet.transmitterId());
      api.getTransmitterService().removeTransmitter(id);
      syncTransmittersToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[TransmitterNetwork] Failed to delete transmitter: {}", e.getMessage(), e);
    }
  }

  public static void syncTransmittersToAll(final MinecraftServer server) {
    if (server == null) return;
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getTransmitterService() == null) return;
      final var json = JsonUtils.MAPPER.writeValueAsString(api.getTransmitterService().getTransmitters());
      final var packet = new ClientBoundTransmitterSyncPacket(json);
      for (final var p : PlayerLookup.all(server))
        ServerPlayNetworking.send(p, packet);
    } catch (final Exception ignored) {}
  }
}
