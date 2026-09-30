package fr.dreamin.dreamvoice.fabric.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.fabric.command.FabricCommandUtils;
import fr.dreamin.dreamvoice.fabric.command.annotation.DreamCmd;
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

import java.util.List;

@DreamCmd
public final class FabricProjectionCmd {

  @Suggestions("projection_players")
  public List<String> suggestPlayers(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    return FabricCommandUtils.suggestPlayers(ctx.sender(), input);
  }

  @Command("projection|voiceprojection list")
  @CommandDescription("List all active voice projections")
  @Permission("dreamvoice.admin.projection.list")
  public void list(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getProjectionService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var projections = api.getProjectionService().getProjections();
    if (projections.isEmpty()) {
      FabricCommandUtils.sendSuccess(source, "projection.list_empty");
      return;
    }

    FabricCommandUtils.sendSuccess(source, "projection.list_header", projections.size());
    for (final var prj : projections)
      FabricCommandUtils.sendSuccess(source, "projection.list_item", FabricCommandUtils.resolvePlayerName(source.getServer(), prj.getPlayerUuid()), prj.getDistance());
  }

  @Command("projection|voiceprojection create [distance]")
  @Command("projection|voiceprojection add [distance]")
  @CommandDescription("Create a voice projection at your current position")
  @Permission("dreamvoice.admin.projection.create")
  public void create(
    final @NotNull CommandSourceStack source,
    @Argument("distance") @Default("10.0") final double distance
  ) {
    if (!source.isPlayer()) {
      FabricCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var api = DreamVoiceAPI.get();
    if (api.getProjectionService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var p = source.getPlayer();
    if (p == null) {
      FabricCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var loc = FabricCommandUtils.toVoiceLocation(p);

    final var prj = api.getProjectionService().createProjection(p.getUUID(), loc);
    prj.setDistance(distance);
    FabricCommandUtils.sendSuccess(source, "projection.created", p.getName().getString(), (int) p.getX(), (int) p.getY(), (int) p.getZ());
  }

  @Command("projection|voiceprojection remove [target]")
  @Command("projection|voiceprojection delete [target]")
  @CommandDescription("Remove a player's voice projection")
  @Permission("dreamvoice.admin.projection.remove")
  public void remove(
    final @NotNull CommandSourceStack source,
    @Argument(value = "target", suggestions = "projection_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getProjectionService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var target = FabricCommandUtils.resolvePlayer(source, targetName);
    if (target == null) {
      FabricCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    if (!api.getProjectionService().hasProjection(target.getUUID())) {
      FabricCommandUtils.sendFailure(source, "projection.target_none", target.getName().getString());
      return;
    }

    api.getProjectionService().removeProjection(target.getUUID());
    FabricCommandUtils.sendSuccess(source, "projection.removed", target.getName().getString());
  }
}
