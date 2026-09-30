package fr.dreamin.dreamvoice.fabric.network.handler;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamServerReceiver;
import fr.dreamin.dreamvoice.fabric.network.model.radio.ClientBoundRadioSyncPacket;
import fr.dreamin.dreamvoice.fabric.network.model.radio.ServerBoundRadioCreatePacket;
import fr.dreamin.dreamvoice.fabric.network.model.radio.ServerBoundRadioJoinPacket;
import fr.dreamin.dreamvoice.fabric.network.model.radio.ServerBoundRadioLeavePacket;
import fr.dreamin.dreamvoice.fabric.network.utils.JsonUtils;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class RadioServerNetworkHandler {

  @DreamServerReceiver
  public static void handleRadioCreate(ServerBoundRadioCreatePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var node = JsonUtils.MAPPER.readTree(packet.json());
      final var api = DreamVoiceAPI.get();
      if (api.getRadioService() == null) return;

      final var name = node.has("name") ? node.get("name").asText() : "radio_" + System.currentTimeMillis();
      final var channel = api.getRadioService().getOrCreateChannel(name);

      if (node.has("filter"))
        channel.setFilterId(node.get("filter").asText());
      if (node.has("rogerBeep"))
        channel.setRogerBeep(node.get("rogerBeep").asBoolean());

      syncRadioToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[RadioNetwork] Failed to create radio channel: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleRadioJoin(ServerBoundRadioJoinPacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getRadioService() == null) return;
      final var pUuid = packet.playerUuid().isBlank() ? player.getUUID() : UUID.fromString(packet.playerUuid());
      api.getRadioService().joinChannel(pUuid, packet.channelName());
      syncRadioToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[RadioNetwork] Failed to join radio: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleRadioLeave(ServerBoundRadioLeavePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getRadioService() == null) return;
      final var pUuid = packet.playerUuid().isBlank() ? player.getUUID() : UUID.fromString(packet.playerUuid());
      api.getRadioService().leaveChannel(pUuid);
      syncRadioToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[RadioNetwork] Failed to leave radio: {}", e.getMessage(), e);
    }
  }

  public static void syncRadioToAll(final MinecraftServer server) {
    if (server == null) return;
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getRadioService() == null) return;
      final var json = JsonUtils.MAPPER.writeValueAsString(api.getRadioService().getChannels());
      final var packet = new ClientBoundRadioSyncPacket(json);
      for (final var p : PlayerLookup.all(server))
        ServerPlayNetworking.send(p, packet);
    } catch (final Exception ignored) {}
  }
}
