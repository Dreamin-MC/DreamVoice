package fr.dreamin.dreamvoice.fabric.network.handler;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.projection.model.VoiceProjection;
import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamServerReceiver;
import fr.dreamin.dreamvoice.fabric.network.model.projection.ClientBoundProjectionSyncPacket;
import fr.dreamin.dreamvoice.fabric.network.model.projection.ServerBoundProjectionCreatePacket;
import fr.dreamin.dreamvoice.fabric.network.model.projection.ServerBoundProjectionDeletePacket;
import fr.dreamin.dreamvoice.fabric.network.utils.JsonUtils;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class ProjectionServerNetworkHandler {

  @DreamServerReceiver
  public static void handleProjectionCreate(ServerBoundProjectionCreatePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var node = JsonUtils.MAPPER.readTree(packet.json());
      final var api = DreamVoiceAPI.get();
      if (api.getProjectionService() == null) return;

      final var id = node.has("id") ? UUID.fromString(node.get("id").asText()) : UUID.randomUUID();
      final var playerUuid = node.has("playerUuid") ? UUID.fromString(node.get("playerUuid").asText()) : player.getUUID();
      final var world = node.has("world") ? node.get("world").asText() : player.level().dimension().identifier().toString();
      final var x = node.has("x") ? node.get("x").asDouble() : player.getX();
      final var y = node.has("y") ? node.get("y").asDouble() : player.getY();
      final var z = node.has("z") ? node.get("z").asDouble() : player.getZ();
      final var distance = node.has("distance") ? node.get("distance").asDouble() : 16.0;

      final var projection = new VoiceProjection(id, playerUuid, new VoiceLocation(world, x, y, z));
      projection.setDistance(distance);

      if (node.has("anchorEntityUuid"))
        projection.setAnchorEntityUuid(UUID.fromString(node.get("anchorEntityUuid").asText()));
      if (node.has("hearAnchorEnvironment"))
        projection.setHearAnchorEnvironment(node.get("hearAnchorEnvironment").asBoolean());
      if (node.has("emitVoiceAtAnchor"))
        projection.setEmitVoiceAtAnchor(node.get("emitVoiceAtAnchor").asBoolean());

      api.getProjectionService().registerProjection(projection);
      syncProjectionsToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[ProjectionNetwork] Failed to create projection: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleProjectionDelete(ServerBoundProjectionDeletePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getProjectionService() == null) return;
      api.getProjectionService().removeProjection(UUID.fromString(packet.projectionId()));
      syncProjectionsToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[ProjectionNetwork] Failed to delete projection: {}", e.getMessage(), e);
    }
  }

  public static void syncProjectionsToAll(final MinecraftServer server) {
    if (server == null) return;
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getProjectionService() == null) return;
      final var json = JsonUtils.MAPPER.writeValueAsString(api.getProjectionService().getProjections());
      final var packet = new ClientBoundProjectionSyncPacket(json);
      for (final var p : PlayerLookup.all(server))
        ServerPlayNetworking.send(p, packet);
    } catch (final Exception ignored) {}
  }
}
