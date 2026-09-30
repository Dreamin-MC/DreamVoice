package fr.dreamin.dreamvoice.fabric.network.handler;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.room.model.AcousticRoom;
import fr.dreamin.dreamvoice.api.room.model.RoomReverbConfig;
import fr.dreamin.dreamvoice.api.room.model.VoiceCuboid;
import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamServerReceiver;
import fr.dreamin.dreamvoice.fabric.network.model.room.ClientBoundRoomSyncPacket;
import fr.dreamin.dreamvoice.fabric.network.model.room.ServerBoundRoomCreatePacket;
import fr.dreamin.dreamvoice.fabric.network.model.room.ServerBoundRoomDeletePacket;
import fr.dreamin.dreamvoice.fabric.network.model.room.ServerBoundRoomUpdatePacket;
import fr.dreamin.dreamvoice.fabric.network.utils.JsonUtils;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;

public final class RoomServerNetworkHandler {

  @DreamServerReceiver
  public static void handleRoomCreate(ServerBoundRoomCreatePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var node = JsonUtils.MAPPER.readTree(packet.json());
      final var api = DreamVoiceAPI.get();
      if (api.getRoomService() == null) return;

      final var id = node.has("id") ? node.get("id").asText() : "room_" + System.currentTimeMillis();
      final var name = node.has("name") ? node.get("name").asText() : id;
      final var preset = node.has("preset") ? node.get("preset").asText("default") : "default";

      final var room = AcousticRoom.builder()
        .id(id)
        .name(name)
        .presetId(preset)
        .cuboids(new ArrayList<>())
        .build();

      if (node.has("isolationPct"))
        room.setIsolationPctOverride(node.get("isolationPct").asInt());

      if (node.has("reverb") && node.get("reverb").isObject()) {
        final var rNode = node.get("reverb");
        final var reverb = new RoomReverbConfig();
        if (rNode.has("decay")) reverb.setDecay((float) rNode.get("decay").asDouble());
        if (rNode.has("roomSizeMs")) reverb.setRoomSizeMs(rNode.get("roomSizeMs").asInt());
        if (rNode.has("wetGain")) reverb.setWetGain((float) rNode.get("wetGain").asDouble());
        room.setReverbOverride(reverb);
      }

      if (node.has("cuboids") && node.get("cuboids").isArray())
        for (final var cNode : node.get("cuboids")) {
          final var world = cNode.has("world") ? cNode.get("world").asText() : player.level().dimension().identifier().toString();
          final var c = VoiceCuboid.of(
            world,
            cNode.get("minX").asDouble(), cNode.get("minY").asDouble(), cNode.get("minZ").asDouble(),
            cNode.get("maxX").asDouble(), cNode.get("maxY").asDouble(), cNode.get("maxZ").asDouble()
          );
          room.getCuboids().add(c);
        }

      api.getRoomService().registerRoom(room);
      syncRoomsToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[RoomNetwork] Failed to create room: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleRoomUpdate(ServerBoundRoomUpdatePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var node = JsonUtils.MAPPER.readTree(packet.json());
      final var api = DreamVoiceAPI.get();
      if (api.getRoomService() == null || !node.has("id")) return;

      final var id = node.get("id").asText();
      final var roomOpt = api.getRoomService().getRoom(id);
      if (roomOpt.isEmpty()) return;
      final var room = roomOpt.get();

      if (node.has("name")) room.setName(node.get("name").asText());
      if (node.has("preset")) room.setPresetId(node.get("preset").asText());
      if (node.has("isolationPct")) room.setIsolationPctOverride(node.get("isolationPct").asInt());

      syncRoomsToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[RoomNetwork] Failed to update room: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleRoomDelete(ServerBoundRoomDeletePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getRoomService() == null) return;
      api.getRoomService().unregisterRoom(packet.roomId());
      syncRoomsToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[RoomNetwork] Failed to delete room: {}", e.getMessage(), e);
    }
  }

  public static void syncRoomsToAll(final MinecraftServer server) {
    if (server == null) return;
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getRoomService() == null) return;
      final var json = JsonUtils.MAPPER.writeValueAsString(api.getRoomService().getRooms());
      final var packet = new ClientBoundRoomSyncPacket(json);
      for (final var p : PlayerLookup.all(server))
        ServerPlayNetworking.send(p, packet);
    } catch (final Exception ignored) {}
  }
}
