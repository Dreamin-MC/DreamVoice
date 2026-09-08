package fr.dreamin.dreamvoice.core.filter.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Stream;

public final class VoiceFilterCommand {

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

  @Suggestions("export_formats")
  public List<String> suggFormats(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    return Stream.of("yml", "json", "java")
      .filter(f -> f.startsWith(in.toLowerCase()))
      .toList();
  }

  @CommandDescription("List all available voice filters")
  @CommandMethod("voice filter list")
  @CommandPermission("dreamvoice.admin")
  public void listFilters(final @NotNull CommandSender sender) {
    final var service = DreamVoice.getService(VoiceFilterService.class);
    if (service == null)
      return;

    final var filters = service.getAvailableFilters();
    sender.sendMessage(Component.text("=== Available Voice Filters (" + filters.size() + ") ===", NamedTextColor.GOLD));
    for (final var filter : filters) {
      sender.sendMessage(
        Component.text(" - ", NamedTextColor.GRAY)
          .append(Component.text(filter.getId(), NamedTextColor.AQUA))
          .append(Component.text(" (\"" + filter.getName() + "\")", NamedTextColor.WHITE))
          .append(Component.text(" [Priority: " + filter.getPriority() + "]", NamedTextColor.YELLOW))
      );
    }
  }

  @CommandDescription("Apply a voice filter to a player")
  @CommandMethod("voice filter set <player> <filter>")
  @CommandPermission("dreamvoice.admin")
  public void setFilter(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull org.bukkit.entity.Player target,
    @Argument(value = "filter", suggestions = "filter_ids") final @NotNull String filterId
  ) {
    final var service = DreamVoice.getService(VoiceFilterService.class);
    if (service == null)
      return;

    if (service.getFilter(filterId) == null) {
      sender.sendMessage(Component.text("[VoiceFilter] Unknown filter: " + filterId, NamedTextColor.RED));
      return;
    }

    service.addFilter(target.getUniqueId(), filterId);
    sender.sendMessage(
      Component.text("[VoiceFilter] Filter ", NamedTextColor.GREEN)
        .append(Component.text(filterId, NamedTextColor.YELLOW))
        .append(Component.text(" applied to ", NamedTextColor.GREEN))
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
    );
  }

  @CommandDescription("Remove a voice filter from a player")
  @CommandMethod("voice filter remove <player> <filter>")
  @CommandPermission("dreamvoice.admin")
  public void removeFilter(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull org.bukkit.entity.Player target,
    @Argument(value = "filter", suggestions = "filter_ids") final @NotNull String filterId
  ) {
    final var service = DreamVoice.getService(VoiceFilterService.class);
    if (service == null)
      return;

    service.removeFilter(target.getUniqueId(), filterId);
    sender.sendMessage(
      Component.text("[VoiceFilter] Filter ", NamedTextColor.YELLOW)
        .append(Component.text(filterId, NamedTextColor.YELLOW))
        .append(Component.text(" removed from ", NamedTextColor.GREEN))
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
    );
  }

  @CommandDescription("Clear all voice filters from a player")
  @CommandMethod("voice filter clear <player>")
  @CommandPermission("dreamvoice.admin")
  public void clearFilters(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull org.bukkit.entity.Player target
  ) {
    final var service = DreamVoice.getService(VoiceFilterService.class);
    if (service == null)
      return;

    service.clearFilters(target.getUniqueId());
    sender.sendMessage(
      Component.text("[VoiceFilter] All voice filters cleared for ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
    );
  }

  @CommandDescription("Enable or disable automatic environmental voice filters for a player")
  @CommandMethod("voice filter auto <player> <enabled>")
  @CommandPermission("dreamvoice.admin")
  public void setAutoEnvironment(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull org.bukkit.entity.Player target,
    @Argument("enabled") final boolean enabled
  ) {
    final var service = DreamVoice.getService(VoiceFilterService.class);
    if (service == null)
      return;

    service.setAutoEnvironmentEnabled(target.getUniqueId(), enabled);
    sender.sendMessage(
      Component.text("[VoiceFilter] Automatic environment filters ", NamedTextColor.GREEN)
        .append(Component.text(enabled ? "enabled" : "disabled", enabled ? NamedTextColor.YELLOW : NamedTextColor.RED))
        .append(Component.text(" for ", NamedTextColor.GREEN))
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
    );
  }

  @CommandDescription("Reload all custom and file-based filters from disk")
  @CommandMethod("voice filter reload")
  @CommandPermission("dreamvoice.admin")
  public void reloadFilters(final @NotNull CommandSender sender) {
    final var service = DreamVoice.getService(VoiceFilterService.class);
    if (service == null)
      return;

    service.reloadFilters();
    sender.sendMessage(Component.text("[VoiceFilter] Filters reloaded from modules/filter/filters/.", NamedTextColor.GREEN));
  }

  @CommandDescription("Export any registered filter to a file format (.yml, .json, or .java)")
  @CommandMethod("voice filter export <filter> <format>")
  @CommandPermission("dreamvoice.admin")
  public void exportFilter(
    final @NotNull CommandSender sender,
    @Argument(value = "filter", suggestions = "filter_ids") final @NotNull String filterId,
    @Argument(value = "format", suggestions = "export_formats") final @NotNull String format
  ) {
    final var service = DreamVoice.getService(VoiceFilterService.class);
    if (service == null)
      return;

    final var file = service.exportFilter(filterId, format);
    if (file != null && file.exists()) {
      sender.sendMessage(
        Component.text("[VoiceFilter] Filter '", NamedTextColor.GREEN)
          .append(Component.text(filterId, NamedTextColor.AQUA))
          .append(Component.text("' exported successfully to: ", NamedTextColor.GREEN))
          .append(Component.text(file.getName(), NamedTextColor.YELLOW))
      );
    } else {
      sender.sendMessage(
        Component.text("[VoiceFilter] Failed to export filter '", NamedTextColor.RED)
          .append(Component.text(filterId, NamedTextColor.WHITE))
          .append(Component.text("'. Check if filter exists and format (yml, json, java) is valid.", NamedTextColor.RED))
      );
    }
  }

}
