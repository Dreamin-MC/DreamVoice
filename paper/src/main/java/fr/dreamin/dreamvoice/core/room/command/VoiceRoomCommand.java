package fr.dreamin.dreamvoice.core.room.command;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import fr.dreamin.dreamvoice.api.room.service.VoiceRoomService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class VoiceRoomCommand {

  @CommandMethod("voice room list")
  @CommandPermission("dreamvoice.admin")
  public void listRooms(final @NotNull CommandSender sender) {
    final var roomService = DreamVoice.getService(VoiceRoomService.class);
    if (roomService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return;
    }

    final var rooms = roomService.getRooms();
    if (rooms.isEmpty()) {
      sender.sendMessage(Component.translatable("room.empty"));
      return;
    }

    sender.sendMessage(Component.translatable("room.list_header", Component.text(rooms.size())));
    for (final var room : rooms) {
      final var isolation = roomService.getEffectiveIsolation(room);
      sender.sendMessage(Component.translatable("room.list_item",
        Component.text(room.getId()),
        Component.text(room.getName()),
        Component.text(room.getPresetId()),
        Component.text(isolation)
      ));
    }
  }

  @CommandMethod("voice room info <id>")
  @CommandPermission("dreamvoice.admin")
  public void roomInfo(final @NotNull CommandSender sender, final @NotNull @Argument("id") String id) {
    final var roomService = DreamVoice.getService(VoiceRoomService.class);
    if (roomService == null)
      return;

    final var opt = roomService.getRoom(id);
    if (opt.isEmpty()) {
      sender.sendMessage(Component.translatable("room.not_found", Component.text(id)));
      return;
    }

    final var room = opt.get();
    final var isolation = roomService.getEffectiveIsolation(room);
    sender.sendMessage(Component.translatable("room.info_header", Component.text(room.getId()), Component.text(room.getName())));
    sender.sendMessage(Component.translatable("room.info_preset", Component.text(room.getPresetId())));
    sender.sendMessage(Component.translatable("room.info_isolation", Component.text(isolation)));
    sender.sendMessage(Component.translatable("room.info_cuboids", Component.text(room.getCuboids().size())));
  }

  @CommandMethod("voice room delete <id>")
  @CommandPermission("dreamvoice.admin")
  public void deleteRoom(final @NotNull CommandSender sender, final @NotNull @Argument("id") String id) {
    final var roomService = DreamVoice.getService(VoiceRoomService.class);
    if (roomService == null)
      return;

    roomService.unregisterRoom(id);
    roomService.save();
    sender.sendMessage(Component.translatable("room.deleted", Component.text(id)));
  }

  @CommandMethod("voice room reload")
  @CommandPermission("dreamvoice.admin")
  public void reloadRooms(final @NotNull CommandSender sender) {
    final var roomService = DreamVoice.getService(VoiceRoomService.class);
    if (roomService == null)
      return;

    roomService.reload();
    sender.sendMessage(Component.translatable("room.reloaded"));
  }

}
