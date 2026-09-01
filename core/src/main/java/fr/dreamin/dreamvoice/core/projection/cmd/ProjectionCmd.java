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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
      sender.sendMessage(Component.text("[PROJECTION] Projection service unavailable.", NamedTextColor.RED));
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
    if (target == null) return;

    final var loc = target.getLocation();
    final var proj = projectionService.createProjection(target, loc);

    sender.sendMessage(
      Component.text("[PROJECTION] Voice anchor created for ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(String.format(" at (%.1f, %.1f, %.1f)!", loc.getX(), loc.getY(), loc.getZ()), NamedTextColor.GREEN))
    );
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
    if (target == null) return;

    if (!projectionService.hasProjection(target)) {
      sender.sendMessage(Component.text("[PROJECTION] No active anchor for this player.", NamedTextColor.GRAY));
      return;
    }

    projectionService.removeProjection(target);
    sender.sendMessage(
      Component.text("[PROJECTION] Voice anchor removed for ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(".", NamedTextColor.GREEN))
    );
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
    if (target == null) return;

    final var proj = projectionService.getProjection(target);
    if (proj == null) {
      sender.sendMessage(Component.text("[PROJECTION] No active anchor for " + target.getName(), NamedTextColor.RED));
      return;
    }

    // Find nearest non-player entity within 5 blocks
    final var loc = target.getLocation();
    final var nearby = loc.getWorld().getNearbyEntities(loc, 5.0, 5.0, 5.0).stream()
      .filter(e -> !e.getUniqueId().equals(target.getUniqueId()))
      .findFirst()
      .orElse(null);

    if (nearby == null) {
      sender.sendMessage(Component.text("[PROJECTION] No entity found nearby to attach anchor.", NamedTextColor.RED));
      return;
    }

    proj.setAnchorEntity(nearby);
    sender.sendMessage(
      Component.text("[PROJECTION] Anchor of ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(" attached to entity ", NamedTextColor.GREEN))
        .append(Component.text(nearby.getType().name() + " (" + nearby.getUniqueId().toString().substring(0, 8) + ")", NamedTextColor.AQUA))
        .append(Component.text("! Audio will now follow its movements.", NamedTextColor.GREEN))
    );
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
    if (target == null) return;

    final var proj = projectionService.getProjection(target);
    if (proj == null) {
      sender.sendMessage(Component.text("[PROJECTION] No active anchor for " + target.getName(), NamedTextColor.RED));
      return;
    }

    proj.setAnchorLocation(proj.getAnchorLocation());
    proj.setAnchorEntity(null);
    sender.sendMessage(
      Component.text("[PROJECTION] Anchor of ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(" detached from entity (position frozen).", NamedTextColor.GREEN))
    );
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

    final var proj = projectionService.getProjection(target);
    if (proj == null) {
      sender.sendMessage(Component.text("[PROJECTION] No active anchor for " + target.getName(), NamedTextColor.RED));
      return;
    }

    proj.setDistance(distance);
    sender.sendMessage(
      Component.text("[PROJECTION] Distance set to ", NamedTextColor.GREEN)
        .append(Component.text(distance + "m", NamedTextColor.YELLOW))
        .append(Component.text(" for ", NamedTextColor.GREEN))
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
    );
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

    final var proj = projectionService.getProjection(target);
    if (proj == null) {
      sender.sendMessage(Component.text("[PROJECTION] No active anchor for " + target.getName(), NamedTextColor.RED));
      return;
    }

    proj.setFilterId(filterId.equalsIgnoreCase("none") ? null : filterId.toLowerCase());
    sender.sendMessage(
      Component.text("[PROJECTION] Filter set to ", NamedTextColor.GREEN)
        .append(Component.text(filterId, NamedTextColor.YELLOW))
        .append(Component.text(" for ", NamedTextColor.GREEN))
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
    );
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

    final var proj = projectionService.getProjection(target);
    if (proj == null) {
      sender.sendMessage(Component.text("[PROJECTION] No active anchor for " + target.getName(), NamedTextColor.RED));
      return;
    }
    proj.setEmitVoiceAtAnchor(enabled);
    sender.sendMessage(Component.text("[PROJECTION] Voice emission at anchor: " + (enabled ? "ON" : "OFF"), NamedTextColor.YELLOW));
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

    final var proj = projectionService.getProjection(target);
    if (proj == null) {
      sender.sendMessage(Component.text("[PROJECTION] No active anchor for " + target.getName(), NamedTextColor.RED));
      return;
    }
    proj.setEmitVoiceAtPlayer(enabled);
    sender.sendMessage(Component.text("[PROJECTION] Voice emission at player/camera: " + (enabled ? "ON" : "OFF"), NamedTextColor.YELLOW));
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

    final var proj = projectionService.getProjection(target);
    if (proj == null) {
      sender.sendMessage(Component.text("[PROJECTION] No active anchor for " + target.getName(), NamedTextColor.RED));
      return;
    }
    proj.setHearAnchorEnvironment(enabled);
    sender.sendMessage(Component.text("[PROJECTION] Listening around anchor: " + (enabled ? "ON" : "OFF"), NamedTextColor.YELLOW));
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

    final var proj = projectionService.getProjection(target);
    if (proj == null) {
      sender.sendMessage(Component.text("[PROJECTION] No active anchor for " + target.getName(), NamedTextColor.RED));
      return;
    }
    proj.setHearPlayerEnvironment(enabled);
    sender.sendMessage(Component.text("[PROJECTION] Listening around player/camera: " + (enabled ? "ON" : "OFF"), NamedTextColor.YELLOW));
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

    final var proj = projectionService.getProjection(target);
    if (proj == null) {
      sender.sendMessage(Component.text("[PROJECTION] No active anchor for " + target.getName(), NamedTextColor.RED));
      return;
    }
    proj.setApplyVoiceWall(enabled);
    sender.sendMessage(Component.text("[PROJECTION] VoiceWall for anchor: " + (enabled ? "ON" : "OFF"), NamedTextColor.YELLOW));
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
    if (target == null) return;

    final var proj = projectionService.getProjection(target);
    if (proj == null) {
      sender.sendMessage(Component.text("[PROJECTION] No active anchor for " + target.getName(), NamedTextColor.GRAY));
      return;
    }

    final var loc = proj.getAnchorLocation();
    final var attached = proj.getAnchorEntity() != null ? proj.getAnchorEntity().getType().name() : "None";

    sender.sendMessage(Component.text("==== [PROJECTION INFO] ====", NamedTextColor.GOLD));
    sender.sendMessage(Component.text("Player: ", NamedTextColor.GRAY).append(Component.text(target.getName(), NamedTextColor.YELLOW)));
    sender.sendMessage(Component.text("Anchor Position: ", NamedTextColor.GRAY).append(Component.text(String.format("%.1f, %.1f, %.1f (%s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld() != null ? loc.getWorld().getName() : "?"), NamedTextColor.AQUA)));
    sender.sendMessage(Component.text("Attached Entity: ", NamedTextColor.GRAY).append(Component.text(attached, NamedTextColor.LIGHT_PURPLE)));
    sender.sendMessage(Component.text("Range: ", NamedTextColor.GRAY).append(Component.text(proj.getDistance() + "m", NamedTextColor.YELLOW)));
    sender.sendMessage(Component.text("Filter: ", NamedTextColor.GRAY).append(Component.text(proj.getFilterId() != null ? proj.getFilterId() : "none", NamedTextColor.AQUA)));
    sender.sendMessage(Component.text("Emission Anchor / Player: ", NamedTextColor.GRAY).append(Component.text(proj.isEmitVoiceAtAnchor() + " / " + proj.isEmitVoiceAtPlayer(), NamedTextColor.GREEN)));
    sender.sendMessage(Component.text("Listening Anchor / Player: ", NamedTextColor.GRAY).append(Component.text(proj.isHearAnchorEnvironment() + " / " + proj.isHearPlayerEnvironment(), NamedTextColor.GREEN)));
    sender.sendMessage(Component.text("VoiceWall: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(proj.isApplyVoiceWall()), NamedTextColor.YELLOW)));
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
      sender.sendMessage(Component.text("[PROJECTION] No active voice anchors.", NamedTextColor.GRAY));
      return;
    }

    sender.sendMessage(
      Component.text("[PROJECTION] Active anchors (", NamedTextColor.GRAY)
        .append(Component.text(projections.size(), NamedTextColor.YELLOW))
        .append(Component.text("):", NamedTextColor.GRAY))
    );

    for (final var p : projections) {
      final var pl = Bukkit.getPlayer(p.getPlayerUuid());
      final var name = pl != null ? pl.getName() : p.getPlayerUuid().toString().substring(0, 8);
      final var loc = p.getAnchorLocation();

      sender.sendMessage(
        Component.text(" - Player: ", NamedTextColor.GRAY)
          .append(Component.text(name, NamedTextColor.AQUA))
          .append(Component.text(String.format(" @ (%.1f, %.1f, %.1f in %s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld() != null ? loc.getWorld().getName() : "?"), NamedTextColor.YELLOW))
          .append(Component.text(" [Dist=" + p.getDistance() + "m, Filter=" + (p.getFilterId() != null ? p.getFilterId() : "none") + "]", NamedTextColor.DARK_GRAY))
      );
    }
  }

  private @Nullable Player resolvePlayer(final @NotNull CommandSender sender, final @Nullable Player targetArg) {
    if (targetArg != null)
      return targetArg;
    if (sender instanceof Player p)
      return p;
    sender.sendMessage(Component.text("Specify a player!", NamedTextColor.RED));
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
    sender.sendMessage(Component.text("[PROJECTION] All voice projections successfully saved to disk!", NamedTextColor.GREEN));
  }

  @CommandDescription("Reload all projections from disk")
  @CommandMethod("projection reload")
  @CommandPermission("dreamvoice.projection.reload")
  private void reloadProjections(final @NotNull CommandSender sender) {
    final var projectionService = requireProjectionService(sender);
    if (projectionService == null)
      return;

    projectionService.load();
    sender.sendMessage(Component.text("[PROJECTION] Projections successfully reloaded from disk (" + projectionService.getProjections().size() + " active)!", NamedTextColor.GREEN));
  }

}
