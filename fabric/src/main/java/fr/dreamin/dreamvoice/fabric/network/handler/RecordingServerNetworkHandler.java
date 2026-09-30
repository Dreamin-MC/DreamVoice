package fr.dreamin.dreamvoice.fabric.network.handler;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.item.FabricCassetteItem;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamServerReceiver;
import fr.dreamin.dreamvoice.fabric.network.model.recording.ClientBoundRecordingSyncPacket;
import fr.dreamin.dreamvoice.fabric.network.model.recording.ServerBoundCassetteCreatePacket;
import fr.dreamin.dreamvoice.fabric.network.model.recording.ServerBoundRecordingPlayPacket;
import fr.dreamin.dreamvoice.fabric.network.model.recording.ServerBoundRecordingStartPacket;
import fr.dreamin.dreamvoice.fabric.network.model.recording.ServerBoundRecordingStopPacket;
import fr.dreamin.dreamvoice.fabric.network.model.recording.ServerBoundRecordingStopPlayPacket;
import fr.dreamin.dreamvoice.fabric.network.utils.JsonUtils;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class RecordingServerNetworkHandler {

  @DreamServerReceiver
  public static void handleRecordingStart(ServerBoundRecordingStartPacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getRecordingService() == null) return;
      final var targetUuid = packet.targetPlayerUuid().isBlank() ? player.getUUID() : UUID.fromString(packet.targetPlayerUuid());
      api.getRecordingService().startRecording(targetUuid);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[RecordingNetwork] Failed to start recording: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleRecordingStop(ServerBoundRecordingStopPacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getRecordingService() == null) return;
      final var targetUuid = packet.targetPlayerUuid().isBlank() ? player.getUUID() : UUID.fromString(packet.targetPlayerUuid());
      final var rec = api.getRecordingService().getVoiceRecording(targetUuid);
      api.getRecordingService().stopRecording(targetUuid);

      if (rec != null && packet.giveCassette()) {
        final var targetPlayer = server.getPlayerList().getPlayer(targetUuid);
        final var authorName = targetPlayer != null ? targetPlayer.getName().getString() : player.getName().getString();
        final var cassette = FabricCassetteItem.create(rec, authorName, player);
        player.getInventory().add(cassette);
      }

      syncRecordingsToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[RecordingNetwork] Failed to stop recording: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleRecordingPlay(ServerBoundRecordingPlayPacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getRecordingService() == null || api.getAPI() == null) return;

      final var recUuid = UUID.fromString(packet.recordingUuid());
      final var rec = api.getRecordingService().getVoiceRecording(recUuid);
      if (rec == null) return;

      final var targetUuid = packet.targetPlayerUuid().isBlank() ? player.getUUID() : UUID.fromString(packet.targetPlayerUuid());
      final var conn = api.getAPI().getConnectionOf(targetUuid);
      if (conn != null)
        api.getRecordingService().playRecordingTo(conn, rec);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[RecordingNetwork] Failed to play recording: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleRecordingStopPlay(ServerBoundRecordingStopPlayPacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getRecordingService() == null) return;
      api.getRecordingService().stopRecording(UUID.fromString(packet.recordingUuid()));
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[RecordingNetwork] Failed to stop playback: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleCassetteCreate(ServerBoundCassetteCreatePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getRecordingService() == null) return;
      final var recUuid = UUID.fromString(packet.recordingUuid());
      final var rec = api.getRecordingService().getVoiceRecording(recUuid);
      if (rec != null) {
        final var author = packet.customTitle().isBlank() ? player.getName().getString() : packet.customTitle();
        final var cassette = FabricCassetteItem.create(rec, author, player);
        player.getInventory().add(cassette);
      }
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[RecordingNetwork] Failed to create cassette: {}", e.getMessage(), e);
    }
  }

  public static void syncRecordingsToAll(final MinecraftServer server) {
    if (server == null) return;
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getRecordingService() == null) return;
      final var json = JsonUtils.MAPPER.writeValueAsString(api.getRecordingService().getVoiceRecordings());
      final var packet = new ClientBoundRecordingSyncPacket(json);
      for (final var p : PlayerLookup.all(server))
        ServerPlayNetworking.send(p, packet);
    } catch (final Exception ignored) {}
  }
}
