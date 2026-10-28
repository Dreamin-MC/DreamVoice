package fr.dreamin.dreamvoice.neoforge.network.handler;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.neoforge.DreamVoiceNeoForge;
import fr.dreamin.dreamvoice.neoforge.network.annotation.DreamServerReceiver;
import fr.dreamin.dreamvoice.neoforge.network.model.radio.ClientBoundRadioSyncPacket;
import fr.dreamin.dreamvoice.neoforge.network.model.radio.ServerBoundRadioCreatePacket;
import fr.dreamin.dreamvoice.neoforge.network.model.radio.ServerBoundRadioJoinPacket;
import fr.dreamin.dreamvoice.neoforge.network.model.radio.ServerBoundRadioLeavePacket;
import fr.dreamin.dreamvoice.neoforge.network.utils.JsonUtils;


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
      DreamVoiceNeoForge.LOGGER.error("[RadioNetwork] Failed to create radio channel: {}", e.getMessage(), e);
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
      DreamVoiceNeoForge.LOGGER.error("[RadioNetwork] Failed to join radio: {}", e.getMessage(), e);
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
      DreamVoiceNeoForge.LOGGER.error("[RadioNetwork] Failed to leave radio: {}", e.getMessage(), e);
    }
  }

  public static void syncRadioToAll(final MinecraftServer server) {
    if (server == null) return;
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getRadioService() == null) return;
      final var json = JsonUtils.MAPPER.writeValueAsString(api.getRadioService().getChannels());
      final var packet = new ClientBoundRadioSyncPacket(json);
      for (final var p : server.getPlayerList().getPlayers())
        p.connection.send(packet);
    } catch (final Exception ignored) {}
  }
}

