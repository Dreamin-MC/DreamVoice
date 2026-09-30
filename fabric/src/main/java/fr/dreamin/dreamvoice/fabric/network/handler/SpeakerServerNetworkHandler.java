package fr.dreamin.dreamvoice.fabric.network.handler;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import fr.dreamin.dreamvoice.api.speaker.model.SpeakerMode;
import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamServerReceiver;
import fr.dreamin.dreamvoice.fabric.network.model.speaker.ClientBoundSpeakerSyncPacket;
import fr.dreamin.dreamvoice.fabric.network.model.speaker.ServerBoundSpeakerCreatePacket;
import fr.dreamin.dreamvoice.fabric.network.model.speaker.ServerBoundSpeakerDeletePacket;
import fr.dreamin.dreamvoice.fabric.network.model.speaker.ServerBoundSpeakerLinkPacket;
import fr.dreamin.dreamvoice.fabric.network.model.speaker.ServerBoundSpeakerPlaySoundPacket;
import fr.dreamin.dreamvoice.fabric.network.model.speaker.ServerBoundSpeakerStopSoundPacket;
import fr.dreamin.dreamvoice.fabric.network.model.speaker.ServerBoundSpeakerUnlinkPacket;
import fr.dreamin.dreamvoice.fabric.network.model.speaker.ServerBoundSpeakerUpdatePacket;
import fr.dreamin.dreamvoice.fabric.network.utils.JsonUtils;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class SpeakerServerNetworkHandler {

  @DreamServerReceiver
  public static void handleSpeakerCreate(ServerBoundSpeakerCreatePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var node = JsonUtils.MAPPER.readTree(packet.json());
      final var api = DreamVoiceAPI.get();
      if (api.getSpeakerService() == null) return;

      final var id = node.has("id") ? UUID.fromString(node.get("id").asText()) : UUID.randomUUID();
      final var name = node.has("name") ? node.get("name").asText() : "Speaker-" + id.toString().substring(0, 4);
      final var world = node.has("world") ? node.get("world").asText() : player.level().dimension().identifier().toString();
      final var x = node.has("x") ? node.get("x").asDouble() : player.getX();
      final var y = node.has("y") ? node.get("y").asDouble() : player.getY();
      final var z = node.has("z") ? node.get("z").asDouble() : player.getZ();
      final var distance = node.has("distance") ? (float) node.get("distance").asDouble() : 32.0f;
      final var modeStr = node.has("mode") ? node.get("mode").asText("GLOBAL") : "GLOBAL";
      final var mode = "RESTRICTED".equalsIgnoreCase(modeStr) ? SpeakerMode.RESTRICTED : SpeakerMode.GLOBAL;

      final var builder = Speaker.builder()
        .uuid(id)
        .name(name)
        .location(new VoiceLocation(world, x, y, z))
        .distance(distance)
        .mode(mode);

      if (node.has("entityUuid"))
        builder.targetEntity(UUID.fromString(node.get("entityUuid").asText()));

      if (node.has("allowedSpeakers") && node.get("allowedSpeakers").isArray())
        for (final var item : node.get("allowedSpeakers"))
          builder.allowSpeaker(UUID.fromString(item.asText()));

      final var speaker = builder.build();
      api.getSpeakerService().register(speaker);

      syncSpeakersToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[SpeakerNetwork] Failed to handle create: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleSpeakerUpdate(ServerBoundSpeakerUpdatePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var node = JsonUtils.MAPPER.readTree(packet.json());
      final var api = DreamVoiceAPI.get();
      if (api.getSpeakerService() == null || !node.has("id")) return;

      final var id = UUID.fromString(node.get("id").asText());
      final var speaker = api.getSpeakerService().getSpeaker(id);
      if (speaker == null) return;

      if (node.has("distance")) speaker.updateDistance((float) node.get("distance").asDouble());
      if (node.has("mode"))
        speaker.setMode("RESTRICTED".equalsIgnoreCase(node.get("mode").asText()) ? SpeakerMode.RESTRICTED : SpeakerMode.GLOBAL);
      if (node.has("allowedSpeakers") && node.get("allowedSpeakers").isArray()) {
        speaker.clearAllowedSpeakers();
        for (final var item : node.get("allowedSpeakers"))
          speaker.linkSpeaker(UUID.fromString(item.asText()));
      }

      syncSpeakersToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[SpeakerNetwork] Failed to handle update: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleSpeakerDelete(ServerBoundSpeakerDeletePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getSpeakerService() == null) return;
      api.getSpeakerService().unregister(UUID.fromString(packet.speakerId()));
      syncSpeakersToAll(server);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[SpeakerNetwork] Failed to handle delete: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleSpeakerLink(ServerBoundSpeakerLinkPacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getSpeakerService() == null) return;
      final var speaker = api.getSpeakerService().getSpeaker(UUID.fromString(packet.speakerId()));
      if (speaker != null) {
        speaker.linkSpeaker(UUID.fromString(packet.playerUuid()));
        syncSpeakersToAll(server);
      }
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[SpeakerNetwork] Failed to handle link: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleSpeakerUnlink(ServerBoundSpeakerUnlinkPacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getSpeakerService() == null) return;
      final var speaker = api.getSpeakerService().getSpeaker(UUID.fromString(packet.speakerId()));
      if (speaker != null) {
        speaker.unlinkSpeaker(UUID.fromString(packet.playerUuid()));
        syncSpeakersToAll(server);
      }
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[SpeakerNetwork] Failed to handle unlink: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleSpeakerPlaySound(ServerBoundSpeakerPlaySoundPacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getSpeakerService() == null) return;
      final var speaker = api.getSpeakerService().getSpeaker(UUID.fromString(packet.speakerId()));
      if (speaker != null)
        api.getSpeakerService().playSoundFile(speaker, packet.soundKey(), false);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[SpeakerNetwork] Failed to handle play sound: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleSpeakerStopSound(ServerBoundSpeakerStopSoundPacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getSpeakerService() == null) return;
      final var speaker = api.getSpeakerService().getSpeaker(UUID.fromString(packet.speakerId()));
      if (speaker != null)
        api.getSpeakerService().stopSound(speaker);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[SpeakerNetwork] Failed to handle stop sound: {}", e.getMessage(), e);
    }
  }

  public static void syncSpeakersToAll(final MinecraftServer server) {
    if (server == null) return;
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getSpeakerService() == null) return;
      final var json = JsonUtils.MAPPER.writeValueAsString(api.getSpeakerService().getSpeakers());
      final var packet = new ClientBoundSpeakerSyncPacket(json);
      for (final var p : PlayerLookup.all(server))
        ServerPlayNetworking.send(p, packet);
    } catch (final Exception ignored) {}
  }
}
