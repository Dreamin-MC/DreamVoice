package fr.dreamin.dreamvoice.core.projection.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.projection.model.VoiceProjection;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
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
      sender.sendMessage(Component.text("[PROJECTION] Service projection indisponible.", NamedTextColor.RED));
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
      Component.text("[PROJECTION] Ancre vocale créée pour ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(String.format(" en (%.1f, %.1f, %.1f) !", loc.getX(), loc.getY(), loc.getZ()), NamedTextColor.GREEN))
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
      sender.sendMessage(Component.text("[PROJECTION] Aucune ancre active pour ce joueur.", NamedTextColor.GRAY));
      return;
    }

    projectionService.removeProjection(target);
    sender.sendMessage(
      Component.text("[PROJECTION] Ancre vocale supprimée pour ", NamedTextColor.GREEN)
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
      sender.sendMessage(Component.text("[PROJECTION] Aucune ancre active pour " + target.getName(), NamedTextColor.RED));
      return;
    }

    // Find nearest non-player entity within 5 blocks
    final var loc = target.getLocation();
    final var nearby = loc.getWorld().getNearbyEntities(loc, 5.0, 5.0, 5.0).stream()
      .filter(e -> !e.getUniqueId().equals(target.getUniqueId()))
      .findFirst()
      .orElse(null);

    if (nearby == null) {
      sender.sendMessage(Component.text("[PROJECTION] Aucune entité trouvée à proximité pour accrocher l'ancre.", NamedTextColor.RED));
      return;
    }

    proj.setAnchorEntity(nearby);
    sender.sendMessage(
      Component.text("[PROJECTION] Ancre de ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(" accrochée à l'entité ", NamedTextColor.GREEN))
        .append(Component.text(nearby.getType().name() + " (" + nearby.getUniqueId().toString().substring(0, 8) + ")", NamedTextColor.AQUA))
        .append(Component.text(" ! L'audio suivra ses déplacements.", NamedTextColor.GREEN))
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
      sender.sendMessage(Component.text("[PROJECTION] Aucune ancre active pour " + target.getName(), NamedTextColor.RED));
      return;
    }

    proj.setAnchorLocation(proj.getAnchorLocation());
    proj.setAnchorEntity(null);
    sender.sendMessage(
      Component.text("[PROJECTION] Ancre de ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(" détachée de l'entité (position figée).", NamedTextColor.GREEN))
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
      sender.sendMessage(Component.text("[PROJECTION] Aucune ancre active pour " + target.getName(), NamedTextColor.RED));
      return;
    }

    proj.setDistance(distance);
    sender.sendMessage(
      Component.text("[PROJECTION] Portée définie sur ", NamedTextColor.GREEN)
        .append(Component.text(distance + "m", NamedTextColor.YELLOW))
        .append(Component.text(" pour ", NamedTextColor.GREEN))
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
      sender.sendMessage(Component.text("[PROJECTION] Aucune ancre active pour " + target.getName(), NamedTextColor.RED));
      return;
    }

    proj.setFilterId(filterId.equalsIgnoreCase("none") ? null : filterId.toLowerCase());
    sender.sendMessage(
      Component.text("[PROJECTION] Filtre de l'ancre défini sur ", NamedTextColor.GREEN)
        .append(Component.text(filterId, NamedTextColor.YELLOW))
        .append(Component.text(" pour ", NamedTextColor.GREEN))
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
      sender.sendMessage(Component.text("[PROJECTION] Aucune ancre active pour " + target.getName(), NamedTextColor.RED));
      return;
    }
    proj.setEmitVoiceAtAnchor(enabled);
    sender.sendMessage(Component.text("[PROJECTION] Émission voix à l'ancre : " + (enabled ? "ON" : "OFF"), NamedTextColor.YELLOW));
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
      sender.sendMessage(Component.text("[PROJECTION] Aucune ancre active pour " + target.getName(), NamedTextColor.RED));
      return;
    }
    proj.setEmitVoiceAtPlayer(enabled);
    sender.sendMessage(Component.text("[PROJECTION] Émission voix à la caméra : " + (enabled ? "ON" : "OFF"), NamedTextColor.YELLOW));
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
      sender.sendMessage(Component.text("[PROJECTION] Aucune ancre active pour " + target.getName(), NamedTextColor.RED));
      return;
    }
    proj.setHearAnchorEnvironment(enabled);
    sender.sendMessage(Component.text("[PROJECTION] Écoute autour de l'ancre : " + (enabled ? "ON" : "OFF"), NamedTextColor.YELLOW));
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
      sender.sendMessage(Component.text("[PROJECTION] Aucune ancre active pour " + target.getName(), NamedTextColor.RED));
      return;
    }
    proj.setHearPlayerEnvironment(enabled);
    sender.sendMessage(Component.text("[PROJECTION] Écoute autour de la caméra : " + (enabled ? "ON" : "OFF"), NamedTextColor.YELLOW));
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
      sender.sendMessage(Component.text("[PROJECTION] Aucune ancre active pour " + target.getName(), NamedTextColor.RED));
      return;
    }
    proj.setApplyVoiceWall(enabled);
    sender.sendMessage(Component.text("[PROJECTION] VoiceWall pour l'ancre : " + (enabled ? "ON" : "OFF"), NamedTextColor.YELLOW));
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
      sender.sendMessage(Component.text("[PROJECTION] Aucune ancre active pour " + target.getName(), NamedTextColor.GRAY));
      return;
    }

    final var loc = proj.getAnchorLocation();
    final var attached = proj.getAnchorEntity() != null ? proj.getAnchorEntity().getType().name() : "None";

    sender.sendMessage(Component.text("==== [PROJECTION INFO] ====", NamedTextColor.GOLD));
    sender.sendMessage(Component.text("Joueur: ", NamedTextColor.GRAY).append(Component.text(target.getName(), NamedTextColor.YELLOW)));
    sender.sendMessage(Component.text("Position Ancre: ", NamedTextColor.GRAY).append(Component.text(String.format("%.1f, %.1f, %.1f (%s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld() != null ? loc.getWorld().getName() : "?"), NamedTextColor.AQUA)));
    sender.sendMessage(Component.text("Entité attachée: ", NamedTextColor.GRAY).append(Component.text(attached, NamedTextColor.LIGHT_PURPLE)));
    sender.sendMessage(Component.text("Portée: ", NamedTextColor.GRAY).append(Component.text(proj.getDistance() + "m", NamedTextColor.YELLOW)));
    sender.sendMessage(Component.text("Filtre: ", NamedTextColor.GRAY).append(Component.text(proj.getFilterId() != null ? proj.getFilterId() : "none", NamedTextColor.AQUA)));
    sender.sendMessage(Component.text("Émission Ancre / Caméra: ", NamedTextColor.GRAY).append(Component.text(proj.isEmitVoiceAtAnchor() + " / " + proj.isEmitVoiceAtPlayer(), NamedTextColor.GREEN)));
    sender.sendMessage(Component.text("Écoute Ancre / Caméra: ", NamedTextColor.GRAY).append(Component.text(proj.isHearAnchorEnvironment() + " / " + proj.isHearPlayerEnvironment(), NamedTextColor.GREEN)));
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
      sender.sendMessage(Component.text("[PROJECTION] Aucune ancre vocale active.", NamedTextColor.GRAY));
      return;
    }

    sender.sendMessage(
      Component.text("[PROJECTION] Ancres actives (", NamedTextColor.GRAY)
        .append(Component.text(projections.size(), NamedTextColor.YELLOW))
        .append(Component.text("):", NamedTextColor.GRAY))
    );

    for (final var p : projections) {
      final var pl = Bukkit.getPlayer(p.getPlayerUuid());
      final var name = pl != null ? pl.getName() : p.getPlayerUuid().toString().substring(0, 8);
      final var loc = p.getAnchorLocation();

      sender.sendMessage(
        Component.text(" - Joueur: ", NamedTextColor.GRAY)
          .append(Component.text(name, NamedTextColor.AQUA))
          .append(Component.text(String.format(" @ (%.1f, %.1f, %.1f in %s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld() != null ? loc.getWorld().getName() : "?"), NamedTextColor.YELLOW))
          .append(Component.text(" [Dist=" + p.getDistance() + "m, Filtre=" + (p.getFilterId() != null ? p.getFilterId() : "none") + "]", NamedTextColor.DARK_GRAY))
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

}
