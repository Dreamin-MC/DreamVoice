package fr.dreamin.dreamvoice.core.transmitter.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import fr.dreamin.dreamvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
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
      sender.sendMessage(Component.translatable("common.service_unavailable"));
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
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    transmissionService.createTransmitter(player.getUniqueId());
    sender.sendMessage(Component.translatable("transmitter.enabled"));
  }

  // ------------------------------------------------
  // DISABLE
  // ------------------------------------------------

  @CommandMethod("transmitter disable")
  @CommandPermission("dreamvoice.transmitter.disable")
  @CommandDescription("Disable transmitter mode")
  private void disable(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    transmissionService.removeTransmitter(player.getUniqueId());
    sender.sendMessage(Component.translatable("transmitter.disabled"));
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
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    if (!transmissionService.isTransmitter(player.getUniqueId())) {
      sender.sendMessage(Component.translatable("transmitter.not_active"));
      return;
    }

    if (distance != null && distance <= 0) {
      sender.sendMessage(Component.translatable("transmitter.distance_positive"));
      return;
    }

    if (distance != null) {
      transmissionService.addReceiver(player.getUniqueId(), target.getUniqueId(), distance);
      sender.sendMessage(Component.translatable("transmitter.receiver_added_range", Component.text(target.getName()), Component.text(distance)));
    } else {
      transmissionService.addReceiver(player.getUniqueId(), target.getUniqueId());
      sender.sendMessage(Component.translatable("transmitter.receiver_added", Component.text(target.getName())));
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
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    transmissionService.removeReceiver(player.getUniqueId(), target.getUniqueId());
    sender.sendMessage(Component.translatable("transmitter.receiver_removed", Component.text(target.getName())));
  }

  // ------------------------------------------------
  // LIST
  // ------------------------------------------------

  @CommandMethod("transmitter list")
  @CommandPermission("dreamvoice.transmitter.list")
  @CommandDescription("List receivers")
  private void list(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    if (!transmissionService.isTransmitter(player.getUniqueId())) {
      sender.sendMessage(Component.translatable("transmitter.not_active"));
      return;
    }

    final var receivers = transmissionService.getReceivers(player.getUniqueId());

    if (receivers.isEmpty()) {
      sender.sendMessage(Component.translatable("transmitter.list_empty"));
      return;
    }

    sender.sendMessage(Component.translatable("transmitter.list_header", Component.text(receivers.size())));

    for (final var config : receivers) {
      final var target = Bukkit.getPlayer(config.getUuid());
      if (target == null)
        continue;

      final var rangeText = config.hasMaxDistance()
        ? config.getMaxDistance() + "m"
        : "infinite";

      sender.sendMessage(Component.translatable("transmitter.list_item", Component.text(target.getName()), Component.text(rangeText)));
    }
  }

  @CommandDescription("Clear all receivers from your transmitter")
  @CommandMethod("transmitter clear")
  @CommandPermission("dreamvoice.transmitter.modify")
  private void clearReceivers(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    transmissionService.clearReceivers(player.getUniqueId());
    sender.sendMessage(Component.translatable("transmitter.cleared"));
  }

  @CommandDescription("Save all transmitters to disk")
  @CommandMethod("transmitter save")
  @CommandPermission("dreamvoice.transmitter.save")
  private void saveTransmitters(final @NotNull CommandSender sender) {
    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    transmissionService.save();
    sender.sendMessage(Component.translatable("persistence.transmitters_saved"));
  }

  @CommandDescription("Reload all transmitters from disk")
  @CommandMethod("transmitter reload")
  @CommandPermission("dreamvoice.transmitter.reload")
  private void reloadTransmitters(final @NotNull CommandSender sender) {
    final var transmissionService = requireTransmissionService(sender);
    if (transmissionService == null)
      return;

    transmissionService.load();
    sender.sendMessage(Component.translatable("persistence.transmitters_reloaded"));
  }

}
