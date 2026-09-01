package fr.dreamin.dreamvoice.core.transmitter.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import fr.dreamin.dreamvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TransmitterCmd {

  private final @Nullable VoiceTransmitterService transmissionService =
    DreamVoice.getService(VoiceTransmitterService.class);

  private @Nullable VoiceTransmitterService requireTransmissionService(final @NotNull CommandSender sender) {
    if (this.transmissionService == null) {
      sender.sendMessage(Component.text("[TRANSMITTER] Transmitter service unavailable.", NamedTextColor.RED));
      return null;
    }
    return this.transmissionService;
  }

  // ------------------------------------------------
  // ENABLE
  // ------------------------------------------------

  @CommandMethod("transmitter enable")
  @CommandPermission("dreamvoice.transmitter.enable")
  @CommandDescription("Enable transmitter mode")
  private void enable(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    transmissionService.createTransmitter(player);

    sender.sendMessage(
      Component.text("[TRANSMITTER] Transmitter mode enabled.", NamedTextColor.GREEN)
    );
  }

  // ------------------------------------------------
  // DISABLE
  // ------------------------------------------------

  @CommandMethod("transmitter disable")
  @CommandPermission("dreamvoice.transmitter.disable")
  @CommandDescription("Disable transmitter mode")
  private void disable(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    transmissionService.removeTransmitter(player);

    sender.sendMessage(
      Component.text("[TRANSMITTER] Transmitter mode disabled.", NamedTextColor.RED)
    );
  }

  // ------------------------------------------------
  // ADD RECEIVER
  // ------------------------------------------------

  @CommandMethod("transmitter add <player> [distance]")
  @CommandPermission("dreamvoice.transmitter.modify")
  @CommandDescription("Add receiver with optional distance")
  private void addReceiver(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target,
    @Argument("distance") final @Nullable Double distance
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    if (!transmissionService.isTransmitter(player)) {
      sender.sendMessage(Component.text("[TRANSMITTER] You are not a transmitter! Enable it first with /transmitter enable.", NamedTextColor.RED));
      return;
    }

    if (distance != null && distance <= 0) {
      sender.sendMessage(Component.text("[TRANSMITTER] Distance must be > 0.", NamedTextColor.RED));
      return;
    }

    if (distance != null) {
      transmissionService.addReceiver(player, target, distance);
      sender.sendMessage(
        Component.text("[TRANSMITTER] Added receiver: ", NamedTextColor.GREEN)
          .append(Component.text(target.getName(), NamedTextColor.AQUA))
          .append(Component.text(" (range: " + distance + "m)", NamedTextColor.GRAY))
      );
    } else {
      transmissionService.addReceiver(player, target);
      sender.sendMessage(
        Component.text("[TRANSMITTER] Added receiver: ", NamedTextColor.GREEN)
          .append(Component.text(target.getName(), NamedTextColor.AQUA))
          .append(Component.text(" (infinite range)", NamedTextColor.GRAY))
      );
    }
  }

  // ------------------------------------------------
  // REMOVE RECEIVER
  // ------------------------------------------------

  @CommandMethod("transmitter remove <player>")
  @CommandPermission("dreamvoice.transmitter.modify")
  @CommandDescription("Remove receiver")
  private void removeReceiver(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    transmissionService.removeReceiver(player, target);

    sender.sendMessage(
      Component.text("[TRANSMITTER] Removed receiver: ", NamedTextColor.YELLOW)
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
    );
  }

  // ------------------------------------------------
  // LIST
  // ------------------------------------------------

  @CommandMethod("transmitter list")
  @CommandPermission("dreamvoice.transmitter.list")
  @CommandDescription("List receivers")
  private void list(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    if (!transmissionService.isTransmitter(player)) {
      sender.sendMessage(Component.text("[TRANSMITTER] You are not a transmitter!", NamedTextColor.RED));
      return;
    }

    final var receivers = transmissionService.getReceivers(player);

    if (receivers.isEmpty()) {
      sender.sendMessage(Component.text("[TRANSMITTER] No active receivers configured.", NamedTextColor.GRAY));
      return;
    }

    sender.sendMessage(
      Component.text("[TRANSMITTER] Configured receivers (" + receivers.size() + "):", NamedTextColor.YELLOW)
    );

    for (final var config : receivers) {
      final var target = Bukkit.getPlayer(config.getUuid());
      if (target == null)
        continue;

      final var rangeText = config.hasMaxDistance()
        ? config.getMaxDistance() + "m"
        : "infinite";

      sender.sendMessage(
        Component.text(" - ", NamedTextColor.GRAY)
          .append(Component.text(target.getName(), NamedTextColor.AQUA))
          .append(Component.text(" | Range: ", NamedTextColor.GRAY))
          .append(Component.text(rangeText, NamedTextColor.GREEN))
      );
    }
  }

  @CommandDescription("Clear all receivers from your transmitter")
  @CommandMethod("transmitter clear")
  @CommandPermission("dreamvoice.transmitter.modify")
  private void clearReceivers(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    transmissionService.clearReceivers(player);
    sender.sendMessage(Component.text("[TRANSMITTER] All receivers cleared.", NamedTextColor.YELLOW));
  }

  @CommandDescription("Save all transmitters to disk")
  @CommandMethod("transmitter save")
  @CommandPermission("dreamvoice.transmitter.save")
  private void saveTransmitters(final @NotNull CommandSender sender) {
    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    transmissionService.save();
    sender.sendMessage(Component.text("[TRANSMITTER] All transmitters successfully saved to disk!", NamedTextColor.GREEN));
  }

  @CommandDescription("Reload all transmitters from disk")
  @CommandMethod("transmitter reload")
  @CommandPermission("dreamvoice.transmitter.reload")
  private void reloadTransmitters(final @NotNull CommandSender sender) {
    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    transmissionService.load();
    sender.sendMessage(Component.text("[TRANSMITTER] Transmitters successfully reloaded from disk!", NamedTextColor.GREEN));
  }

}
