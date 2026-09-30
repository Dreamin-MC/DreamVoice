package fr.dreamin.dreamvoice.fabric.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.broadcast.model.BroadcastPoint;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@DreamCmd
public final class FabricBroadcastCmd {

  @Suggestions("broadcasts")
  public List<String> suggestBroadcasts(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    final var api = DreamVoiceAPI.get();
    if (api.getBroadcastService() == null)
      return Collections.emptyList();
    return api.getBroadcastService().getBroadcastPoints().stream()
      .map(BroadcastPoint::getName)
      .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
      .toList();
  }

  @Suggestions("broadcast_speakers")
  public List<String> suggestSpeakers(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeakerService() == null)
      return Collections.emptyList();
    return api.getSpeakerService().getSpeakers().stream()
      .map(Speaker::getName)
      .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
      .toList();
  }

  @Command("broadcast|voicebroadcast list")
  @CommandDescription("List all active broadcast points")
  @Permission("dreamvoice.admin.broadcast.list")
  public void list(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getBroadcastService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var points = api.getBroadcastService().getBroadcastPoints();
    if (points.isEmpty()) {
      FabricCommandUtils.sendSuccess(source, "broadcast.list_empty");
      return;
    }

    FabricCommandUtils.sendSuccess(source, "broadcast.list_header", points.size());
    for (final var pt : points)
      FabricCommandUtils.sendSuccess(source, "broadcast.list_item", pt.getName(), pt.isEnabled() ? "ENABLED" : "DISABLED", pt.getRadius(), pt.getTargetSpeakers().size(), pt.getFilterId() != null ? pt.getFilterId() : "none");
  }

  @Command("broadcast|voicebroadcast create <name> [radius]")
  @CommandDescription("Create a new broadcast point at your current position")
  @Permission("dreamvoice.admin.broadcast.create")
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
    if (api.getBroadcastService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    if (api.getBroadcastService().getBroadcastPoint(name) != null) {
      FabricCommandUtils.sendFailure(source, "broadcast.already_exists", name);
      return;
    }

    final var p = source.getPlayer();
    if (p == null) {
      FabricCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var loc = FabricCommandUtils.toVoiceLocation(p);
    final var pt = new BroadcastPoint(UUID.randomUUID(), name, loc, radius, false, null, true);

    api.getBroadcastService().register(pt);
    FabricCommandUtils.sendSuccess(source, "broadcast.created", name, radius);
  }

  @Command("broadcast|voicebroadcast delete <name>")
  @Command("broadcast|voicebroadcast remove <name>")
  @CommandDescription("Delete a broadcast point by name")
  @Permission("dreamvoice.admin.broadcast.delete")
  public void delete(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "broadcasts") final @NotNull String name
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getBroadcastService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var pt = api.getBroadcastService().getBroadcastPoint(name);
    if (pt == null) {
      FabricCommandUtils.sendFailure(source, "broadcast.not_found", name);
      return;
    }

    api.getBroadcastService().unregister(name);
    FabricCommandUtils.sendSuccess(source, "broadcast.removed", name);
  }

  @Command("broadcast|voicebroadcast toggle <name>")
  @CommandDescription("Toggle a broadcast point active state")
  @Permission("dreamvoice.admin.broadcast.toggle")
  public void toggle(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "broadcasts") final @NotNull String name
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getBroadcastService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var pt = api.getBroadcastService().getBroadcastPoint(name);
    if (pt == null) {
      FabricCommandUtils.sendFailure(source, "broadcast.not_found", name);
      return;
    }

    final var newState = !pt.isEnabled();
    pt.setEnabled(newState);

    FabricCommandUtils.sendSuccess(source, "broadcast.toggled", name, newState ? "ENABLED" : "DISABLED");
  }

  @Command("broadcast|voicebroadcast link <name> <speaker>")
  @CommandDescription("Link a speaker to a broadcast point")
  @Permission("dreamvoice.admin.broadcast.link")
  public void link(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "broadcasts") final @NotNull String name,
    @Argument(value = "speaker", suggestions = "broadcast_speakers") final @NotNull String speakerName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getBroadcastService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var pt = api.getBroadcastService().getBroadcastPoint(name);
    if (pt == null) {
      FabricCommandUtils.sendFailure(source, "broadcast.not_found", name);
      return;
    }

    pt.linkSpeaker(speakerName);
    FabricCommandUtils.sendSuccess(source, "broadcast.linked", name, speakerName);
  }

  @Command("broadcast|voicebroadcast unlink <name> <speaker>")
  @CommandDescription("Unlink a speaker from a broadcast point")
  @Permission("dreamvoice.admin.broadcast.unlink")
  public void unlink(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "broadcasts") final @NotNull String name,
    @Argument(value = "speaker", suggestions = "broadcast_speakers") final @NotNull String speakerName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getBroadcastService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var pt = api.getBroadcastService().getBroadcastPoint(name);
    if (pt == null) {
      FabricCommandUtils.sendFailure(source, "broadcast.not_found", name);
      return;
    }

    pt.unlinkSpeaker(speakerName);
    FabricCommandUtils.sendSuccess(source, "broadcast.unlinked", name, speakerName);
  }
}
