package fr.dreamin.dreamvoice.fabric.network.handler;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamServerReceiver;
import fr.dreamin.dreamvoice.fabric.network.model.wiretap.ClientBoundWiretapSyncPacket;
import fr.dreamin.dreamvoice.fabric.network.model.wiretap.ServerBoundWiretapCreatePacket;
import fr.dreamin.dreamvoice.fabric.network.model.wiretap.ServerBoundWiretapDeletePacket;
import fr.dreamin.dreamvoice.fabric.network.model.wiretap.ServerBoundWiretapSubscribePacket;
import fr.dreamin.dreamvoice.fabric.network.model.wiretap.ServerBoundWiretapUnsubscribePacket;
import fr.dreamin.dreamvoice.fabric.network.utils.JsonUtils;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class WiretapServerNetworkHandler {

  @DreamServerReceiver
  public static void handleWiretapCreate(ServerBoundWiretapCreatePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var node = JsonUtils.MAPPER.readTree(packet.json());
      final var api = DreamVoiceAPI.get();
      if (api.getWiretapService() == null) return;

      final var id = node.has("id") ? UUID.fromString(node.get("id").asText()) : UUID.randomUUID();
      final var name = node.has("name") ? node.get("name").asText() : "Wiretap-" + id.toString().substring(0, 4);
      final var world = node.has("world") ? node.get("world").asText() : player.level().dimension().identifier().toString();
      final var x = node.has("x") ? node.get("x").asDouble() : player.getX();
      final var y = node.has("y") ? node.get("y").asDouble() : player.getY();
      final var z = node.has("z") ? node.get("z").asDouble() : player.getZ();
      final var distance = node.has("distance") ? node.get("distance").asDouble() : 12.0;

      final var wiretap = new VoiceWiretap(id, name, new VoiceLocation(world, x, y, z));
      wiretap.setDistance(distance);

      if (node.has("entityUuid"))
        wiretap.setTargetEntityUuid(UUID.fromString(node.get("entityUuid").asText()));
      if (node.has("filterId"))
        wiretap.setFilterId(node.get("filterId").asText());

      api.getWiretapService().register(wiretap);
      syncWiretapsToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[WiretapNetwork] Failed to handle create: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleWiretapDelete(ServerBoundWiretapDeletePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getWiretapService() == null) return;
      api.getWiretapService().removeWiretap(UUID.fromString(packet.wiretapId()));
      syncWiretapsToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[WiretapNetwork] Failed to handle delete: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleWiretapSubscribe(ServerBoundWiretapSubscribePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getWiretapService() == null) return;
      final var wt = api.getWiretapService().getWiretap(UUID.fromString(packet.wiretapId()));
      if (wt != null) {
        api.getWiretapService().addListener(wt.getName(), UUID.fromString(packet.playerUuid()));
        syncWiretapsToAll(server);
      }
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[WiretapNetwork] Failed to handle subscribe: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleWiretapUnsubscribe(ServerBoundWiretapUnsubscribePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getWiretapService() == null) return;
      final var wt = api.getWiretapService().getWiretap(UUID.fromString(packet.wiretapId()));
      if (wt != null) {
        api.getWiretapService().removeListener(wt.getName(), UUID.fromString(packet.playerUuid()));
        syncWiretapsToAll(server);
      }
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[WiretapNetwork] Failed to handle unsubscribe: {}", e.getMessage(), e);
    }
  }

  public static void syncWiretapsToAll(final MinecraftServer server) {
    if (server == null) return;
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getWiretapService() == null) return;
      final var json = JsonUtils.MAPPER.writeValueAsString(api.getWiretapService().getWiretaps());
      final var packet = new ClientBoundWiretapSyncPacket(json);
      for (final var p : PlayerLookup.all(server))
        ServerPlayNetworking.send(p, packet);
    } catch (final Exception ignored) {}
  }
}
