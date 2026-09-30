package fr.dreamin.dreamvoice.core.projection.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
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
import java.util.stream.Collectors;

public final class ProjectionCmd {

  private final @Nullable VoiceProjectionService projectionService =
    DreamVoice.getService(VoiceProjectionService.class);

  private @Nullable VoiceProjectionService requireProjectionService(final @NotNull CommandSender sender) {
    if (this.projectionService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return null;
    }
    return this.projectionService;
  }

  @Suggestions("voice_filters")
  public List<String> suggFilters(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    final var list = new ArrayList<String>();
    list.add("none");
    if (filterService != null) {
      filterService.getAvailableFilters().stream()
        .map(VoiceFilter::getId)
        .forEach(list::add);
    }
    return list.stream()
      .filter(id -> id.startsWith(in.toLowerCase()))
      .sorted()
      .collect(Collectors.toList());
  }

  @CommandDescription("Create a body anchor / voice projection for a player")
  @CommandMethod("projection create [target]")
  @CommandPermission("dreamvoice.projection.use")
  private void createProjection(
    final @NotNull CommandSender sender,
    @Argument("target") final @Nullable Player targetArg
  ) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    final var target = resolvePlayer(sender, targetArg);
    if (target == null)
      return;

    final var loc = target.getLocation();
    projectionService.createProjection(target.getUniqueId(), LocationUtils.toVoiceLocation(loc));

    sender.sendMessage(Component.translatable("projection.created",
      Component.text(target.getName()),
      Component.text(String.format("%.1f", loc.getX())),
      Component.text(String.format("%.1f", loc.getY())),
      Component.text(String.format("%.1f", loc.getZ()))
    ));
  }

  @CommandDescription("Remove a player's body anchor / voice projection")
  @CommandMethod("projection remove [target]")
  @CommandPermission("dreamvoice.projection.use")
  private void removeProjection(
    final @NotNull CommandSender sender,
    @Argument("target") final @Nullable Player targetArg
  ) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    final var target = resolvePlayer(sender, targetArg);
    if (target == null)
      return;

    if (!projectionService.hasProjection(target.getUniqueId())) {
      sender.sendMessage(Component.translatable("projection.none"));
      return;
    }

    projectionService.removeProjection(target.getUniqueId());
    sender.sendMessage(Component.translatable("projection.removed", Component.text(target.getName())));
  }

  @CommandDescription("Attach a projection to the nearest entity or target entity")
  @CommandMethod("projection attach [target]")
  @CommandPermission("dreamvoice.projection.use")
  private void attachEntity(
    final @NotNull CommandSender sender,
    @Argument("target") final @Nullable Player targetArg
  ) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    final var target = resolvePlayer(sender, targetArg);
    if (target == null)
      return;

    final var proj = projectionService.getProjection(target.getUniqueId());
    if (proj == null) {
      sender.sendMessage(Component.translatable("projection.target_none", Component.text(target.getName())));
      return;
    }

    // Find nearest non-player entity within 5 blocks
    final var loc = target.getLocation();
    final var nearby = loc.getWorld().getNearbyEntities(loc, 5.0, 5.0, 5.0).stream()
      .filter(e -> !e.getUniqueId().equals(target.getUniqueId()))
      .findFirst()
      .orElse(null);

    if (nearby == null) {
      sender.sendMessage(Component.translatable("projection.no_entity_nearby"));
      return;
    }

    proj.setAnchorEntityUuid(nearby.getUniqueId());
    sender.sendMessage(Component.translatable("projection.attached", Component.text(target.getName()), Component.text(nearby.getType().name())));
  }

  @CommandDescription("Detach a projection from its attached entity")
  @CommandMethod("projection detach [target]")
  @CommandPermission("dreamvoice.projection.use")
  private void detachEntity(
    final @NotNull CommandSender sender,
    @Argument("target") final @Nullable Player targetArg
  ) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    final var target = resolvePlayer(sender, targetArg);
    if (target == null)
      return;

    final var proj = projectionService.getProjection(target.getUniqueId());
    if (proj == null) {
      sender.sendMessage(Component.translatable("projection.target_none", Component.text(target.getName())));
      return;
    }

    proj.setAnchorLocation(proj.getAnchorLocation());
    proj.setAnchorEntityUuid(null);
    sender.sendMessage(Component.translatable("projection.detached", Component.text(target.getName())));
  }

  @CommandDescription("Configure hearing/speaking distance of projection")
  @CommandMethod("projection distance <target> <distance>")
  @CommandPermission("dreamvoice.projection.use")
  private void setDistance(
    final @NotNull CommandSender sender,
    @Argument("target") final @NotNull Player target,
    @Argument("distance") final double distance
  ) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    final var proj = projectionService.getProjection(target.getUniqueId());
    if (proj == null) {
      sender.sendMessage(Component.translatable("projection.target_none", Component.text(target.getName())));
      return;
    }

    proj.setDistance(distance);
    sender.sendMessage(Component.translatable("projection.distance_set", Component.text(distance), Component.text(target.getName())));
  }

  @CommandDescription("Configure voice filter on projection")
  @CommandMethod("projection filter <target> <filter>")
  @CommandPermission("dreamvoice.projection.use")
  private void setFilter(
    final @NotNull CommandSender sender,
    @Argument("target") final @NotNull Player target,
    @Argument(value = "filter", suggestions = "voice_filters") final @NotNull String filterId
  ) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    final var proj = projectionService.getProjection(target.getUniqueId());
    if (proj == null) {
      sender.sendMessage(Component.translatable("projection.target_none", Component.text(target.getName())));
      return;
    }

    proj.setFilterId(filterId.equalsIgnoreCase("none") ? null : filterId.toLowerCase());
    sender.sendMessage(Component.translatable("projection.filter_set", Component.text(filterId), Component.text(target.getName())));
  }

  @CommandDescription("Toggle emitting voice at the anchor location")
  @CommandMethod("projection emit-anchor <target> <enabled>")
  @CommandPermission("dreamvoice.projection.use")
  private void setEmitAnchor(
    final @NotNull CommandSender sender,
    @Argument("target") final @NotNull Player target,
    @Argument("enabled") final boolean enabled
  ) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    final var proj = projectionService.getProjection(target.getUniqueId());
    if (proj == null) {
      sender.sendMessage(Component.translatable("projection.target_none", Component.text(target.getName())));
      return;
    }
    proj.setEmitVoiceAtAnchor(enabled);
    sender.sendMessage(Component.translatable("projection.emit_anchor", Component.text(enabled ? "ON" : "OFF")));
  }

  @CommandDescription("Toggle emitting voice at the camera/player location")
  @CommandMethod("projection emit-player <target> <enabled>")
  @CommandPermission("dreamvoice.projection.use")
  private void setEmitPlayer(
    final @NotNull CommandSender sender,
    @Argument("target") final @NotNull Player target,
    @Argument("enabled") final boolean enabled
  ) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    final var proj = projectionService.getProjection(target.getUniqueId());
    if (proj == null) {
      sender.sendMessage(Component.translatable("projection.target_none", Component.text(target.getName())));
      return;
    }
    proj.setEmitVoiceAtPlayer(enabled);
    sender.sendMessage(Component.translatable("projection.emit_player", Component.text(enabled ? "ON" : "OFF")));
  }

  @CommandDescription("Toggle hearing audio around the anchor location")
  @CommandMethod("projection hear-anchor <target> <enabled>")
  @CommandPermission("dreamvoice.projection.use")
  private void setHearAnchor(
    final @NotNull CommandSender sender,
    @Argument("target") final @NotNull Player target,
    @Argument("enabled") final boolean enabled
  ) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    final var proj = projectionService.getProjection(target.getUniqueId());
    if (proj == null) {
      sender.sendMessage(Component.translatable("projection.target_none", Component.text(target.getName())));
      return;
    }
    proj.setHearAnchorEnvironment(enabled);
    sender.sendMessage(Component.translatable("projection.hear_anchor", Component.text(enabled ? "ON" : "OFF")));
  }

  @CommandDescription("Toggle hearing audio around the camera/player location")
  @CommandMethod("projection hear-player <target> <enabled>")
  @CommandPermission("dreamvoice.projection.use")
  private void setHearPlayer(
    final @NotNull CommandSender sender,
    @Argument("target") final @NotNull Player target,
    @Argument("enabled") final boolean enabled
  ) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    final var proj = projectionService.getProjection(target.getUniqueId());
    if (proj == null) {
      sender.sendMessage(Component.translatable("projection.target_none", Component.text(target.getName())));
      return;
    }
    proj.setHearPlayerEnvironment(enabled);
    sender.sendMessage(Component.translatable("projection.hear_player", Component.text(enabled ? "ON" : "OFF")));
  }

  @CommandDescription("Toggle VoiceWall occlusion on projection")
  @CommandMethod("projection wall <target> <enabled>")
  @CommandPermission("dreamvoice.projection.use")
  private void setWall(
    final @NotNull CommandSender sender,
    @Argument("target") final @NotNull Player target,
    @Argument("enabled") final boolean enabled
  ) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    final var proj = projectionService.getProjection(target.getUniqueId());
    if (proj == null) {
      sender.sendMessage(Component.translatable("projection.target_none", Component.text(target.getName())));
      return;
    }
    proj.setApplyVoiceWall(enabled);
    sender.sendMessage(Component.translatable("projection.wall", Component.text(enabled ? "ON" : "OFF")));
  }

  @CommandDescription("Show detailed info of an active projection")
  @CommandMethod("projection info [target]")
  @CommandPermission("dreamvoice.projection.use")
  private void showInfo(
    final @NotNull CommandSender sender,
    @Argument("target") final @Nullable Player targetArg
  ) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    final var target = resolvePlayer(sender, targetArg);
    if (target == null)
      return;

    final var proj = projectionService.getProjection(target.getUniqueId());
    if (proj == null) {
      sender.sendMessage(Component.translatable("projection.target_none", Component.text(target.getName())));
      return;
    }

    final var loc = proj.getAnchorLocation();
    final var entity = proj.getAnchorEntityUuid() != null ? Bukkit.getEntity(proj.getAnchorEntityUuid()) : null;
    final var attached = entity != null ? entity.getType().name() : "None";

    sender.sendMessage(Component.translatable("projection.info_header", Component.text(target.getName().toUpperCase())));
    sender.sendMessage(Component.translatable("projection.info_player", Component.text(target.getName())));
    sender.sendMessage(Component.translatable("projection.info_position", Component.text(String.format("%.1f, %.1f, %.1f (%s)", loc.x(), loc.y(), loc.z(), loc.world()))));
    sender.sendMessage(Component.translatable("projection.info_attached", Component.text(attached)));
    sender.sendMessage(Component.translatable("projection.info_range", Component.text(proj.getDistance())));
    sender.sendMessage(Component.translatable("projection.info_filter", Component.text(proj.getFilterId() != null ? proj.getFilterId() : "none")));
    sender.sendMessage(Component.translatable("projection.info_emission", Component.text(proj.isEmitVoiceAtAnchor() + " / " + proj.isEmitVoiceAtPlayer())));
    sender.sendMessage(Component.translatable("projection.info_listening", Component.text(proj.isHearAnchorEnvironment() + " / " + proj.isHearPlayerEnvironment())));
    sender.sendMessage(Component.translatable("projection.info_voicewall", Component.text(String.valueOf(proj.isApplyVoiceWall()))));
  }

  @CommandDescription("List all active voice projections")
  @CommandMethod("projection list")
  @CommandPermission("dreamvoice.projection.use")
  private void listProjections(final @NotNull CommandSender sender) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    final var projections = projectionService.getProjections();
    if (projections.isEmpty()) {
      sender.sendMessage(Component.translatable("projection.list_empty"));
      return;
    }

    sender.sendMessage(Component.translatable("projection.list_header", Component.text(projections.size())));

    for (final var p : projections) {
      final var pl = Bukkit.getPlayer(p.getPlayerUuid());
      final var name = pl != null ? pl.getName() : p.getPlayerUuid().toString().substring(0, 8);
      sender.sendMessage(Component.translatable("projection.list_item", Component.text(name), Component.text(p.getDistance())));
    }
  }

  private @Nullable Player resolvePlayer(final @NotNull CommandSender sender, final @Nullable Player targetArg) {
    if (targetArg != null)
      return targetArg;
    if (sender instanceof Player p)
      return p;
    sender.sendMessage(Component.translatable("projection.specify_player"));
    return null;
  }

  @CommandDescription("Save all projections to disk")
  @CommandMethod("projection save")
  @CommandPermission("dreamvoice.projection.save")
  private void saveProjections(final @NotNull CommandSender sender) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    projectionService.save();
    sender.sendMessage(Component.translatable("persistence.projections_saved"));
  }

  @CommandDescription("Reload all projections from disk")
  @CommandMethod("projection reload")
  @CommandPermission("dreamvoice.projection.reload")
  private void reloadProjections(final @NotNull CommandSender sender) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    projectionService.load();
    sender.sendMessage(Component.translatable("persistence.projections_reloaded"));
  }

}
