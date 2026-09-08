package fr.dreamin.dreamvoice.core.room.command;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import fr.dreamin.dreamapi.api.cuboid.Cuboid;
import fr.dreamin.dreamvoice.api.room.model.AcousticRoom;
import fr.dreamin.dreamvoice.api.room.service.VoiceRoomService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public final class VoiceRoomCommand {

  @CommandMethod("voice room list")
  @CommandPermission("dreamvoice.admin")
  public void listRooms(final @NotNull CommandSender sender) {
    final var roomService = DreamVoice.getService(VoiceRoomService.class);
    if (roomService == null) {
      sender.sendMessage(Component.text("VoiceRoomService is unavailable.", NamedTextColor.RED));
      return;
    }

    final var rooms = roomService.getRooms();
    if (rooms.isEmpty()) {
      sender.sendMessage(Component.text("No acoustic rooms registered.", NamedTextColor.YELLOW));
      return;
    }

    sender.sendMessage(Component.text("=== Acoustic Rooms (" + rooms.size() + ") ===", NamedTextColor.GOLD));
    for (final var room : rooms) {
      final var isolation = roomService.getEffectiveIsolation(room);
      sender.sendMessage(
        Component.text(" - ", NamedTextColor.GRAY)
          .append(Component.text(room.getId(), NamedTextColor.AQUA))
          .append(Component.text(" (\"" + room.getName() + "\")", NamedTextColor.WHITE))
          .append(Component.text(" [Preset: " + room.getPresetId() + ", Isolation: " + isolation + "%]", NamedTextColor.GREEN))
      );
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
      sender.sendMessage(Component.text("Room '" + id + "' not found.", NamedTextColor.RED));
      return;
    }

    final var room = opt.get();
    final var isolation = roomService.getEffectiveIsolation(room);
    sender.sendMessage(Component.text("Room Info: " + room.getId() + " (" + room.getName() + ")", NamedTextColor.GOLD));
    sender.sendMessage(Component.text("Preset: " + room.getPresetId(), NamedTextColor.AQUA));
    sender.sendMessage(Component.text("Effective Isolation: " + isolation + "%", NamedTextColor.YELLOW));
    sender.sendMessage(Component.text("Boundaries (Cuboids): " + room.getCuboids().size(), NamedTextColor.GRAY));
  }

  @CommandMethod("voice room delete <id>")
  @CommandPermission("dreamvoice.admin")
  public void deleteRoom(final @NotNull CommandSender sender, final @NotNull @Argument("id") String id) {
    final var roomService = DreamVoice.getService(VoiceRoomService.class);
    if (roomService == null)
      return;

    roomService.unregisterRoom(id);
    roomService.save();
    sender.sendMessage(Component.text("Acoustic room '" + id + "' deleted.", NamedTextColor.GREEN));
  }

  @CommandMethod("voice room reload")
  @CommandPermission("dreamvoice.admin")
  public void reloadRooms(final @NotNull CommandSender sender) {
    final var roomService = DreamVoice.getService(VoiceRoomService.class);
    if (roomService == null)
      return;

    roomService.reload();
    sender.sendMessage(Component.text("Acoustic rooms and presets reloaded from configuration.", NamedTextColor.GREEN));
  }

}
