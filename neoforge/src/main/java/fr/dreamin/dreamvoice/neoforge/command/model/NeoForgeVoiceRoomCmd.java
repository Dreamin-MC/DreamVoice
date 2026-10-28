package fr.dreamin.dreamvoice.neoforge.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.room.model.AcousticRoom;
import fr.dreamin.dreamvoice.neoforge.command.NeoForgeCommandUtils;
import fr.dreamin.dreamvoice.neoforge.command.annotation.DreamCmd;
import net.minecraft.commands.CommandSourceStack;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@DreamCmd
public final class NeoForgeVoiceRoomCmd {

  @Suggestions("room_ids")
  public List<String> suggestRoomIds(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    final var api = DreamVoiceAPI.get();
    if (api.getRoomService() == null)
      return Collections.emptyList();
    return api.getRoomService().getRooms().stream()
      .map(AcousticRoom::getId)
      .filter(id -> id.toLowerCase().startsWith(input.toLowerCase()))
      .toList();
  }

  @Command("voice room list")
  @Command("room|voiceroom list")
  @CommandDescription("List all active acoustic rooms")
  @Permission("dreamvoice.admin.room.list")
  public void list(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getRoomService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rooms = api.getRoomService().getRooms();
    if (rooms.isEmpty()) {
      NeoForgeCommandUtils.sendSuccess(source, "room.empty");
      return;
    }

    NeoForgeCommandUtils.sendSuccess(source, "room.list_header", rooms.size());
    for (final var rm : rooms)
      NeoForgeCommandUtils.sendSuccess(source, "room.list_item", rm.getId(), rm.getId(), rm.getPresetId(), 100);
  }

  @Command("voice room delete <id>")
  @Command("room|voiceroom delete <id>")
  @Command("voice room remove <id>")
  @Command("room|voiceroom remove <id>")
  @CommandDescription("Delete an acoustic room by ID")
  @Permission("dreamvoice.admin.room.delete")
  public void delete(
    final @NotNull CommandSourceStack source,
    @Argument(value = "id", suggestions = "room_ids") final @NotNull String id
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRoomService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var room = api.getRoomService().getRoom(id);
    if (room.isEmpty()) {
      NeoForgeCommandUtils.sendFailure(source, "room.not_found", id);
      return;
    }

    api.getRoomService().unregisterRoom(id);
    NeoForgeCommandUtils.sendSuccess(source, "room.deleted", id);
  }
}

