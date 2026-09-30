package fr.dreamin.dreamvoice.fabric.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.fabric.command.FabricCommandUtils;
import fr.dreamin.dreamvoice.fabric.command.annotation.DreamCmd;
import net.minecraft.commands.CommandSourceStack;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@DreamCmd
public final class FabricVoiceFilterCmd {

  @Suggestions("filters")
  public List<String> suggestFilters(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    final var api = DreamVoiceAPI.get();
    if (api.getFilterService() == null)
      return Collections.emptyList();
    return api.getFilterService().getAvailableFilters().stream()
      .map(VoiceFilter::getId)
      .filter(id -> id.toLowerCase().startsWith(input.toLowerCase()))
      .toList();
  }

  @Suggestions("filter_players")
  public List<String> suggestPlayers(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    return FabricCommandUtils.suggestPlayers(ctx.sender(), input);
  }

  @Command("filter|voicefilter list")
  @CommandDescription("List all registered voice filters")
  @Permission("dreamvoice.filter.list")
  public void list(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getFilterService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var filters = api.getFilterService().getAvailableFilters();
    FabricCommandUtils.sendSuccess(source, "filter.list_header", filters.size());
    for (final var f : filters)
      FabricCommandUtils.sendSuccess(source, "filter.list_item", f.getId(), f.getName(), f.getPriority());
  }

  @Command("filter|voicefilter clear [target]")
  @CommandDescription("Clear all voice filters from a player")
  @Permission("dreamvoice.admin.filter.clear")
  public void clear(
    final @NotNull CommandSourceStack source,
    @Argument(value = "target", suggestions = "filter_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getFilterService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var target = FabricCommandUtils.resolvePlayer(source, targetName);
    if (target == null) {
      FabricCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    api.getFilterService().clearFilters(target.getUUID());
    FabricCommandUtils.sendSuccess(source, "filter.cleared", target.getName().getString());
  }

  @Command("filter|voicefilter apply <filter> [target]")
  @Command("filter|voicefilter set <filter> [target]")
  @CommandDescription("Apply a voice filter to a player")
  @Permission("dreamvoice.admin.filter.apply")
  public void apply(
    final @NotNull CommandSourceStack source,
    @Argument(value = "filter", suggestions = "filters") final @NotNull String filterId,
    @Argument(value = "target", suggestions = "filter_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getFilterService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var target = FabricCommandUtils.resolvePlayer(source, targetName);
    if (target == null) {
      FabricCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    final var filter = api.getFilterService().getFilter(filterId);
    if (filter == null) {
      FabricCommandUtils.sendFailure(source, "filter.not_found", filterId);
      return;
    }

    api.getFilterService().addFilter(target.getUUID(), filter.getId());
    FabricCommandUtils.sendSuccess(source, "filter.applied_to", filter.getId(), target.getName().getString());
  }

  @Command("filter|voicefilter remove <filter> [target]")
  @CommandDescription("Remove a voice filter from a player")
  @Permission("dreamvoice.admin.filter.remove")
  public void remove(
    final @NotNull CommandSourceStack source,
    @Argument(value = "filter", suggestions = "filters") final @NotNull String filterId,
    @Argument(value = "target", suggestions = "filter_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getFilterService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var target = FabricCommandUtils.resolvePlayer(source, targetName);
    if (target == null) {
      FabricCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    api.getFilterService().removeFilter(target.getUUID(), filterId);
    FabricCommandUtils.sendSuccess(source, "filter.removed_from", filterId, target.getName().getString());
  }
}
