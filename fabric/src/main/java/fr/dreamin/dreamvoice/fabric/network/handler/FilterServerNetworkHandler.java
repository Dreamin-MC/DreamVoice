package fr.dreamin.dreamvoice.fabric.network.handler;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamServerReceiver;
import fr.dreamin.dreamvoice.fabric.network.model.filter.ClientBoundFilterSyncPacket;
import fr.dreamin.dreamvoice.fabric.network.model.filter.ServerBoundFilterApplyPacket;
import fr.dreamin.dreamvoice.fabric.network.model.filter.ServerBoundFilterClearPacket;
import fr.dreamin.dreamvoice.fabric.network.model.filter.ServerBoundFilterRemovePacket;
import fr.dreamin.dreamvoice.fabric.network.model.filter.ServerBoundFilterTogglePacket;
import fr.dreamin.dreamvoice.fabric.network.utils.JsonUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class FilterServerNetworkHandler {

  @DreamServerReceiver
  public static void handleFilterApply(ServerBoundFilterApplyPacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getFilterService() == null) return;
      final var targetUuid = packet.playerUuid().isBlank() ? player.getUUID() : UUID.fromString(packet.playerUuid());
      api.getFilterService().addFilter(targetUuid, packet.filterName());
      syncPlayerFilters(server, targetUuid);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[FilterNetwork] Failed to apply filter: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleFilterRemove(ServerBoundFilterRemovePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getFilterService() == null) return;
      final var targetUuid = packet.playerUuid().isBlank() ? player.getUUID() : UUID.fromString(packet.playerUuid());
      api.getFilterService().removeFilter(targetUuid, packet.filterName());
      syncPlayerFilters(server, targetUuid);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[FilterNetwork] Failed to remove filter: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleFilterToggle(ServerBoundFilterTogglePacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getFilterService() == null) return;
      final var targetUuid = packet.playerUuid().isBlank() ? player.getUUID() : UUID.fromString(packet.playerUuid());
      final var current = api.getFilterService().getActiveFilters(targetUuid);
      final var has = current.stream().anyMatch(f -> f.getId().equalsIgnoreCase(packet.filterName()));
      if (has)
        api.getFilterService().removeFilter(targetUuid, packet.filterName());
      else
        api.getFilterService().addFilter(targetUuid, packet.filterName());
      syncPlayerFilters(server, targetUuid);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[FilterNetwork] Failed to toggle filter: {}", e.getMessage(), e);
    }
  }

  @DreamServerReceiver
  public static void handleFilterClear(ServerBoundFilterClearPacket packet, ServerPlayer player, MinecraftServer server) {
    try {
      final var api = DreamVoiceAPI.get();
      if (api.getFilterService() == null) return;
      final var targetUuid = packet.playerUuid().isBlank() ? player.getUUID() : UUID.fromString(packet.playerUuid());
      api.getFilterService().clearFilters(targetUuid);
      syncPlayerFilters(server, targetUuid);
    } catch (final Exception e) {
      DreamVoiceFabric.LOGGER.error("[FilterNetwork] Failed to clear filters: {}", e.getMessage(), e);
    }
  }

  public static void syncPlayerFilters(final MinecraftServer server, final UUID playerUuid) {
    if (server == null) return;
    try {
      final var p = server.getPlayerList().getPlayer(playerUuid);
      if (p == null) return;
      final var api = DreamVoiceAPI.get();
      if (api.getFilterService() == null) return;
      final var active = api.getFilterService().getActiveFilters(playerUuid);
      final var json = JsonUtils.MAPPER.writeValueAsString(active);
      ServerPlayNetworking.send(p, new ClientBoundFilterSyncPacket(playerUuid.toString(), json));
    } catch (final Exception ignored) {}
  }
}
