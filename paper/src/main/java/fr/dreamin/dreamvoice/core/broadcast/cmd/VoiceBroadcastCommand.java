package fr.dreamin.dreamvoice.core.broadcast.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.broadcast.model.BroadcastPoint;
import fr.dreamin.dreamvoice.api.broadcast.service.VoiceBroadcastService;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.utils.LocationUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class VoiceBroadcastCommand {

  private @Nullable VoiceBroadcastService requireBroadcastService(final @NotNull CommandSender sender) {
    final var service = DreamVoice.getService(VoiceBroadcastService.class);
    if (service == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return null;
    }
    return service;
  }

  @Suggestions("broadcast_points")
  public List<String> suggBroadcastPoints(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    final var service = DreamVoice.getService(VoiceBroadcastService.class);
    if (service == null)
      return List.of();

    final var list = new ArrayList<String>();
    for (final var point : service.getBroadcastPoints())
      if (point.getName().toLowerCase().startsWith(in.toLowerCase()))
        list.add(point.getName());
    return list;
  }

  @Suggestions("speakers")
  public List<String> suggSpeakers(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    final var service = DreamVoice.getService(VoiceSpeakerService.class);
    if (service == null)
      return List.of();

    final var list = new ArrayList<String>();
    for (final var spk : service.getSpeakers())
      if (spk.getName().toLowerCase().startsWith(in.toLowerCase()))
        list.add(spk.getName());
    return list;
  }

  @Suggestions("filter_ids")
  public List<String> suggFilterIds(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    final var service = DreamVoice.getService(VoiceFilterService.class);
    if (service == null)
      return List.of();

    return service.getAvailableFilters().stream()
      .map(VoiceFilter::getId)
      .filter(id -> id.toLowerCase().startsWith(in.toLowerCase()))
      .toList();
  }

  @CommandDescription("Create a new voice broadcast point at your location")
  @CommandMethod("voice broadcast create <name> [radius]")
  @CommandPermission("dreamvoice.broadcast.manage")
  private void create(
    final @NotNull CommandSender sender,
    @Argument("name") final @NotNull String name,
    @Argument("radius") final @Nullable Double radius
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var service = requireBroadcastService(sender);
    if (service == null)
      return;

    if (service.getBroadcastPoint(name) != null) {
      sender.sendMessage(Component.translatable("broadcast.already_exists", Component.text(name)));
      return;
    }

    final var rad = (radius != null && radius > 0) ? radius : 2.0;
    final var point = new BroadcastPoint(name, LocationUtils.toVoiceLocation(player.getLocation()));
    point.setRadius(rad);
    service.register(point);
    service.save();

    player.sendMessage(Component.translatable("broadcast.created", Component.text(name), Component.text(rad)));
  }

  @CommandDescription("Remove a voice broadcast point")
  @CommandMethod("voice broadcast remove <name>")
  @CommandPermission("dreamvoice.broadcast.manage")
  private void remove(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "broadcast_points") final @NotNull String name
  ) {
    final var service = requireBroadcastService(sender);
    if (service == null)
      return;

    if (service.getBroadcastPoint(name) == null) {
      sender.sendMessage(Component.translatable("broadcast.not_found", Component.text(name)));
      return;
    }

    service.unregister(name);
    service.save();
    sender.sendMessage(Component.translatable("broadcast.removed", Component.text(name)));
  }

  @CommandDescription("Toggle a broadcast point on or off")
  @CommandMethod("voice broadcast toggle <name>")
  @CommandPermission("dreamvoice.broadcast.manage")
  private void toggle(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "broadcast_points") final @NotNull String name
  ) {
    final var service = requireBroadcastService(sender);
    if (service == null)
      return;

    final var point = service.getBroadcastPoint(name);
    if (point == null) {
      sender.sendMessage(Component.translatable("broadcast.not_found", Component.text(name)));
      return;
    }

    final var state = point.toggle();
    service.save();
    sender.sendMessage(Component.translatable("broadcast.toggled", Component.text(name), Component.text(state ? "ON" : "OFF")));
  }

  @CommandDescription("Link a speaker to a broadcast point")
  @CommandMethod("voice broadcast link <name> <speaker>")
  @CommandPermission("dreamvoice.broadcast.manage")
  private void link(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "broadcast_points") final @NotNull String name,
    @Argument(value = "speaker", suggestions = "speakers") final @NotNull String speaker
  ) {
    final var service = requireBroadcastService(sender);
    if (service == null)
      return;

    final var point = service.getBroadcastPoint(name);
    if (point == null) {
      sender.sendMessage(Component.translatable("broadcast.not_found", Component.text(name)));
      return;
    }

    point.linkSpeaker(speaker);
    service.save();
    sender.sendMessage(Component.translatable("broadcast.linked", Component.text(name), Component.text(speaker)));
  }

  @CommandDescription("Unlink a speaker from a broadcast point")
  @CommandMethod("voice broadcast unlink <name> <speaker>")
  @CommandPermission("dreamvoice.broadcast.manage")
  private void unlink(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "broadcast_points") final @NotNull String name,
    @Argument(value = "speaker", suggestions = "speakers") final @NotNull String speaker
  ) {
    final var service = requireBroadcastService(sender);
    if (service == null)
      return;

    final var point = service.getBroadcastPoint(name);
    if (point == null) {
      sender.sendMessage(Component.translatable("broadcast.not_found", Component.text(name)));
      return;
    }

    point.unlinkSpeaker(speaker);
    service.save();
    sender.sendMessage(Component.translatable("broadcast.unlinked", Component.text(name), Component.text(speaker)));
  }

  @CommandDescription("Set whether broadcast routes to all speakers")
  @CommandMethod("voice broadcast setall <name> <enabled>")
  @CommandPermission("dreamvoice.broadcast.manage")
  private void setAll(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "broadcast_points") final @NotNull String name,
    @Argument("enabled") final boolean enabled
  ) {
    final var service = requireBroadcastService(sender);
    if (service == null)
      return;

    final var point = service.getBroadcastPoint(name);
    if (point == null) {
      sender.sendMessage(Component.translatable("broadcast.not_found", Component.text(name)));
      return;
    }

    point.setAllSpeakers(enabled);
    service.save();
    sender.sendMessage(Component.translatable("broadcast.all_speakers", Component.text(name), Component.text(String.valueOf(enabled))));
  }

  @CommandDescription("Set or clear audio filter on a broadcast point")
  @CommandMethod("voice broadcast setfilter <name> [filter]")
  @CommandPermission("dreamvoice.broadcast.manage")
  private void setFilter(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "broadcast_points") final @NotNull String name,
    @Argument(value = "filter", suggestions = "filter_ids") final @Nullable String filter
  ) {
    final var service = requireBroadcastService(sender);
    if (service == null)
      return;

    final var point = service.getBroadcastPoint(name);
    if (point == null) {
      sender.sendMessage(Component.translatable("broadcast.not_found", Component.text(name)));
      return;
    }

    if (filter == null || filter.equalsIgnoreCase("none") || filter.equalsIgnoreCase("clear")) {
      point.setFilterId(null);
      service.save();
      sender.sendMessage(Component.translatable("broadcast.filter_cleared", Component.text(name)));
      return;
    }

    point.setFilterId(filter.toLowerCase());
    service.save();
    sender.sendMessage(Component.translatable("broadcast.filter_set", Component.text(name), Component.text(filter)));
  }

  @CommandDescription("List all voice broadcast points")
  @CommandMethod("voice broadcast list")
  @CommandPermission("dreamvoice.broadcast.manage")
  private void list(final @NotNull CommandSender sender) {
    final var service = requireBroadcastService(sender);
    if (service == null)
      return;

    final var points = service.getBroadcastPoints();
    if (points.isEmpty()) {
      sender.sendMessage(Component.translatable("broadcast.list_empty"));
      return;
    }

    sender.sendMessage(Component.translatable("broadcast.list_header", Component.text(points.size())));
    for (final var point : points) {
      final var targets = point.isAllSpeakers() ? "ALL" : String.join(", ", point.getTargetSpeakers());
      sender.sendMessage(Component.translatable(
        "broadcast.list_item",
        Component.text(point.getName()),
        Component.text(point.isEnabled() ? "ON" : "OFF"),
        Component.text(point.getRadius()),
        Component.text(targets),
        Component.text(point.getFilterId() != null ? point.getFilterId() : "none")
      ));
    }
  }

  @CommandDescription("Show info about a broadcast point")
  @CommandMethod("voice broadcast info <name>")
  @CommandPermission("dreamvoice.broadcast.manage")
  private void info(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "broadcast_points") final @NotNull String name
  ) {
    final var service = requireBroadcastService(sender);
    if (service == null)
      return;

    final var point = service.getBroadcastPoint(name);
    if (point == null) {
      sender.sendMessage(Component.translatable("broadcast.not_found", Component.text(name)));
      return;
    }

    final var targets = point.isAllSpeakers() ? "ALL" : String.join(", ", point.getTargetSpeakers());
    final var loc = point.getLocation();
    final var locStr = String.format("%.1f, %.1f, %.1f (%s)", loc.x(), loc.y(), loc.z(), loc.world());

    sender.sendMessage(Component.translatable("broadcast.info_header", Component.text(point.getName())));
    sender.sendMessage(Component.translatable("broadcast.info_state", Component.text(point.isEnabled() ? "ENABLED" : "DISABLED")));
    sender.sendMessage(Component.translatable("broadcast.info_location", Component.text(locStr)));
    sender.sendMessage(Component.translatable("broadcast.info_radius", Component.text(point.getRadius())));
    sender.sendMessage(Component.translatable("broadcast.info_speakers", Component.text(targets)));
    sender.sendMessage(Component.translatable("broadcast.info_filter", Component.text(point.getFilterId() != null ? point.getFilterId() : "none")));
    if (point.getTargetEntityUuid() != null) {
      final var entity = Bukkit.getEntity(point.getTargetEntityUuid());
      if (entity != null)
        sender.sendMessage(Component.translatable("broadcast.info_entity", Component.text(entity.getType().name())));
    }
  }

}
