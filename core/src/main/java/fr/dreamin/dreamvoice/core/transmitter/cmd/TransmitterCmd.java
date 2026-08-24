package fr.dreamin.dreamvoice.core.transmitter.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import fr.dreamin.dreaminvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class TransmitterCmd {

  private final @NotNull VoiceTransmitterService transmissionService =
    DreamVoice.getService(VoiceTransmitterService.class);

  // ------------------------------------------------
  // ENABLE
  // ------------------------------------------------

  @CommandMethod("transmitter enable")
  @CommandPermission("dreamvoice.transmitter.enable")
  @CommandDescription("Enable transmitter mode")
  private void enable(CommandSender sender) {

    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    this.transmissionService.createTransmitter(player);

    sender.sendMessage(
      Component.text("Transmitter enabled.", NamedTextColor.GREEN)
    );
  }

  // ------------------------------------------------
  // DISABLE
  // ------------------------------------------------

  @CommandMethod("transmitter disable")
  @CommandPermission("dreamvoice.transmitter.disable")
  @CommandDescription("Disable transmitter mode")
  private void disable(CommandSender sender) {

    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    this.transmissionService.removeTransmitter(player);

    sender.sendMessage(
      Component.text("Transmitter disabled.", NamedTextColor.RED)
    );
  }

  // ------------------------------------------------
  // ADD RECEIVER
  // ------------------------------------------------

  @CommandMethod("transmitter add <player> <distance>")
  @CommandPermission("dreamvoice.transmitter.modify")
  @CommandDescription("Add receiver with distance")
  private void addReceiver(
    CommandSender sender,
    @Argument("player") Player target,
    @Argument("distance") double distance
  ) {

    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    if (!this.transmissionService.isTransmitter(player)) {
      sender.sendMessage(Component.text("You are not a transmitter!", NamedTextColor.RED));
      return;
    }

    if (distance <= 0) {
      sender.sendMessage(Component.text("Distance must be > 0.", NamedTextColor.RED));
      return;
    }

    this.transmissionService.addReceiver(player, target, distance);

    sender.sendMessage(
      Component.text("Added receiver: ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
        .append(Component.text(" (range: " + distance + ")", NamedTextColor.GRAY))
    );
  }

  // ------------------------------------------------
  // REMOVE RECEIVER
  // ------------------------------------------------

  @CommandMethod("transmitter remove <player>")
  @CommandPermission("dreamvoice.transmitter.modify")
  @CommandDescription("Remove receiver")
  private void removeReceiver(
    CommandSender sender,
    @Argument("player") Player target
  ) {

    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    this.transmissionService.removeReceiver(player, target);

    sender.sendMessage(
      Component.text("Removed receiver: ", NamedTextColor.YELLOW)
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
    );
  }

  // ------------------------------------------------
  // LIST
  // ------------------------------------------------

  @CommandMethod("transmitter list")
  @CommandPermission("dreamvoice.transmitter.list")
  @CommandDescription("List receivers")
  private void list(CommandSender sender) {

    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    if (!this.transmissionService.isTransmitter(player)) {
      sender.sendMessage(Component.text("You are not a transmitter!", NamedTextColor.RED));
      return;
    }

    final var receivers = this.transmissionService.getReceivers(player);

    if (receivers.isEmpty()) {
      sender.sendMessage(Component.text("No receivers.", NamedTextColor.GRAY));
      return;
    }

    sender.sendMessage(
      Component.text("Receivers (" + receivers.size() + "):", NamedTextColor.YELLOW)
    );

    for (final var config : receivers) {
      final var target = Bukkit.getPlayer(config.getUuid());
      if (target == null) continue;

      sender.sendMessage(
        Component.text("- ", NamedTextColor.GRAY)
          .append(Component.text(target.getName(), NamedTextColor.AQUA))
          .append(Component.text(" | range: ", NamedTextColor.GRAY))
          .append(Component.text(config.getMaxDistance(), NamedTextColor.GREEN))
      );
    }
  }
}