package fr.dreamin.dreamvoice.neoforge.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.neoforge.command.NeoForgeCommandUtils;
import fr.dreamin.dreamvoice.neoforge.command.annotation.DreamCmd;
import net.minecraft.commands.CommandSourceStack;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

@DreamCmd
public final class NeoForgeTransmitterCmd {

  @Suggestions("transmitter_players")
  public List<String> suggestPlayers(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    return NeoForgeCommandUtils.suggestPlayers(ctx.sender(), input);
  }

  @Command("voice transmitter enable")
  @Command("transmitter|voicetransmitter enable")
  @CommandDescription("Enable global voice transmitter")
  @Permission("dreamvoice.admin.transmitter.toggle")
  public void enable(final @NotNull CommandSourceStack source) {
    if (!source.isPlayer()) {
      NeoForgeCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var api = DreamVoiceAPI.get();
    if (api.getTransmitterService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    api.getTransmitterService().createTransmitter(Objects.requireNonNull(source.getPlayer()).getUUID());
    NeoForgeCommandUtils.sendSuccess(source, "transmitter.enabled");
  }

  @Command("voice transmitter disable")
  @Command("transmitter|voicetransmitter disable")
  @CommandDescription("Disable global voice transmitter")
  @Permission("dreamvoice.admin.transmitter.toggle")
  public void disable(final @NotNull CommandSourceStack source) {
    if (!source.isPlayer()) {
      NeoForgeCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var api = DreamVoiceAPI.get();
    if (api.getTransmitterService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    api.getTransmitterService().removeTransmitter(Objects.requireNonNull(source.getPlayer()).getUUID());
    NeoForgeCommandUtils.sendSuccess(source, "transmitter.disabled");
  }

  @Command("voice transmitter list")
  @Command("transmitter|voicetransmitter list")
  @CommandDescription("List all active transmitter targets")
  @Permission("dreamvoice.admin.transmitter.list")
  public void list(final @NotNull CommandSourceStack source) {
    if (!source.isPlayer()) {
      NeoForgeCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var api = DreamVoiceAPI.get();
    if (api.getTransmitterService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var receivers = api.getTransmitterService().getReceivers(Objects.requireNonNull(source.getPlayer()).getUUID());
    if (receivers.isEmpty()) {
      NeoForgeCommandUtils.sendSuccess(source, "transmitter.list_empty");
      return;
    }

    NeoForgeCommandUtils.sendSuccess(source, "transmitter.list_header", receivers.size());
    for (final var r : receivers)
      NeoForgeCommandUtils.sendSuccess(source, "transmitter.list_item", r.getUuid().toString(), (r.getMaxDistance() != null ? r.getMaxDistance() + "m" : "infinite"));
  }

  @Command("voice transmitter clear")
  @Command("transmitter|voicetransmitter clear")
  @CommandDescription("Clear all transmitters")
  @Permission("dreamvoice.admin.transmitter.clear")
  public void clear(final @NotNull CommandSourceStack source) {
    if (!source.isPlayer()) {
      NeoForgeCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var api = DreamVoiceAPI.get();
    if (api.getTransmitterService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    api.getTransmitterService().clearReceivers(Objects.requireNonNull(source.getPlayer()).getUUID());
    NeoForgeCommandUtils.sendSuccess(source, "transmitter.cleared");
  }

  @Command("voice transmitter add <target> [distance]")
  @Command("transmitter|voicetransmitter add <target> [distance]")
  @CommandDescription("Add a transmitter target")
  @Permission("dreamvoice.admin.transmitter.add")
  public void add(
    final @NotNull CommandSourceStack source,
    @Argument(value = "target", suggestions = "transmitter_players") final @NotNull String targetName,
    @Argument("distance") @Default("10.0") final double distance
  ) {
    if (!source.isPlayer()) {
      NeoForgeCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var api = DreamVoiceAPI.get();
    if (api.getTransmitterService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var target = NeoForgeCommandUtils.resolvePlayer(source, targetName);
    if (target == null) {
      NeoForgeCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    api.getTransmitterService().addReceiver(Objects.requireNonNull(source.getPlayer()).getUUID(), target.getUUID(), distance);
    NeoForgeCommandUtils.sendSuccess(source, "transmitter.receiver_added_range", target.getName().getString(), (int) distance);
  }

  @Command("voice transmitter remove <target>")
  @Command("transmitter|voicetransmitter remove <target>")
  @CommandDescription("Remove a transmitter target")
  @Permission("dreamvoice.admin.transmitter.remove")
  public void remove(
    final @NotNull CommandSourceStack source,
    @Argument(value = "target", suggestions = "transmitter_players") final @NotNull String targetName
  ) {
    if (!source.isPlayer()) {
      NeoForgeCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var api = DreamVoiceAPI.get();
    if (api.getTransmitterService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var target = NeoForgeCommandUtils.resolvePlayer(source, targetName);
    if (target == null) {
      NeoForgeCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    api.getTransmitterService().removeReceiver(Objects.requireNonNull(source.getPlayer()).getUUID(), target.getUUID());
    NeoForgeCommandUtils.sendSuccess(source, "transmitter.receiver_removed", target.getName().getString());
  }
}

