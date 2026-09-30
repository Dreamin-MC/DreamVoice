package fr.dreamin.dreamvoice.fabric.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import fr.dreamin.dreamvoice.fabric.command.FabricCommandUtils;
import fr.dreamin.dreamvoice.fabric.command.annotation.DreamCmd;
import fr.dreamin.dreamvoice.fabric.item.FabricCassetteItem;
import net.minecraft.commands.CommandSourceStack;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@DreamCmd
public final class FabricWiretapCmd {

  @Suggestions("wiretaps")
  public List<String> suggestWiretaps(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    final var api = DreamVoiceAPI.get();
    if (api.getWiretapService() == null)
      return Collections.emptyList();
    return api.getWiretapService().getWiretaps().stream()
      .map(VoiceWiretap::getName)
      .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
      .toList();
  }

  @Suggestions("wiretap_players")
  public List<String> suggestPlayers(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    return FabricCommandUtils.suggestPlayers(ctx.sender(), input);
  }

  @Command("wiretap|voicewiretap list")
  @CommandDescription("List all active wiretaps")
  @Permission("dreamvoice.admin.wiretap.list")
  public void list(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getWiretapService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var wiretaps = api.getWiretapService().getWiretaps();
    FabricCommandUtils.sendSuccess(source, "wiretap.list_header", wiretaps.size());
    for (final var wt : wiretaps)
      FabricCommandUtils.sendSuccess(source, "wiretap.list_item", wt.getName(), wt.getDistance(), wt.getListeners().size());
  }

  @Command("wiretap|voicewiretap create <name> [radius]")
  @Command("wiretap|voicewiretap add <name> [radius]")
  @CommandDescription("Create a new wiretap at your current position")
  @Permission("dreamvoice.admin.wiretap.create")
  public void create(
    final @NotNull CommandSourceStack source,
    @Argument("name") final @NotNull String name,
    @Argument("radius") @Default("10.0") final double radius
  ) {
    if (!source.isPlayer()) {
      FabricCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var api = DreamVoiceAPI.get();
    if (api.getWiretapService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var p = source.getPlayer();
    if (p == null) {
      FabricCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var loc = FabricCommandUtils.toVoiceLocation(p);

    final var wt = api.getWiretapService().createWiretap(name, loc);
    wt.setDistance(radius);
    FabricCommandUtils.sendSuccess(source, "wiretap.created", name);
  }

  @Command("wiretap|voicewiretap delete <name>")
  @Command("wiretap|voicewiretap remove <name>")
  @CommandDescription("Delete a wiretap by name")
  @Permission("dreamvoice.admin.wiretap.delete")
  public void delete(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getWiretapService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var wt = api.getWiretapService().getWiretap(name);
    if (wt == null) {
      FabricCommandUtils.sendFailure(source, "wiretap.not_found", name);
      return;
    }

    api.getWiretapService().removeWiretap(name);
    FabricCommandUtils.sendSuccess(source, "wiretap.removed", name);
  }

  @Command("wiretap|voicewiretap sub <name> [target]")
  @Command("wiretap|voicewiretap listen <name> [target]")
  @CommandDescription("Subscribe a player to a wiretap")
  @Permission("dreamvoice.admin.wiretap.sub")
  public void subscribe(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name,
    @Argument(value = "target", suggestions = "wiretap_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getWiretapService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var target = FabricCommandUtils.resolvePlayer(source, targetName);
    if (target == null) {
      FabricCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    final var success = api.getWiretapService().addListener(name, target.getUUID());
    if (success)
      FabricCommandUtils.sendSuccess(source, "wiretap.subscribed", target.getName().getString(), name);
    else
      FabricCommandUtils.sendFailure(source, "wiretap.not_found", name);
  }

  @Command("wiretap|voicewiretap unsub <name> [target]")
  @Command("wiretap|voicewiretap unlisten <name> [target]")
  @CommandDescription("Unsubscribe a player from a wiretap")
  @Permission("dreamvoice.admin.wiretap.unsub")
  public void unsubscribe(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name,
    @Argument(value = "target", suggestions = "wiretap_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getWiretapService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var target = FabricCommandUtils.resolvePlayer(source, targetName);
    if (target == null) {
      FabricCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    api.getWiretapService().removeListener(name, target.getUUID());
    FabricCommandUtils.sendSuccess(source, "wiretap.unsubscribed", target.getName().getString(), name);
  }

  @Command("wiretap|voicewiretap cassette <name> [target]")
  @CommandDescription("Give a cassette of the latest wiretap recording")
  @Permission("dreamvoice.admin.wiretap.record")
  public void cassette(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name,
    @Argument(value = "target", suggestions = "wiretap_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getWiretapService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var wt = api.getWiretapService().getWiretap(name);
    if (wt == null) {
      FabricCommandUtils.sendFailure(source, "wiretap.not_found", name);
      return;
    }

    final var recordings = wt.getRecordings();
    if (recordings.isEmpty()) {
      FabricCommandUtils.sendFailure(source, "wiretap.no_recordings");
      return;
    }

    final var targetPlayer = FabricCommandUtils.resolvePlayer(source, targetName);
    if (targetPlayer == null) {
      FabricCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    final var latest = recordings.getLast();
    final var item = FabricCassetteItem.create(latest, name, targetPlayer);
    targetPlayer.getInventory().add(item);
    FabricCommandUtils.sendSuccess(source, "record.cassette_given", latest.getUuid().toString().substring(0, 8), targetPlayer.getName().getString());
  }
}
