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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
    sender.sendMessage(Component.translatable("filter.list_header", Component.text(filters.size())));
    for (final var filter : filters)
      sender.sendMessage(Component.translatable("filter.list_item",
        Component.text(filter.getId()),
        Component.text(filter.getName()),
        Component.text(filter.getPriority())
      ));
  }

  @CommandDescription("Apply a voice filter to a player")
  @CommandMethod("voice filter set <player> <filter>")
  @CommandPermission("dreamvoice.admin")
  public void setFilter(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target,
    @Argument(value = "filter", suggestions = "filter_ids") final @NotNull String filterId
  ) {
    final var service = DreamVoice.getService(VoiceFilterService.class);
    if (service == null)
      return;

    if (service.getFilter(filterId) == null) {
      sender.sendMessage(Component.translatable("filter.not_found", Component.text(filterId)));
      return;
    }

    service.addFilter(target.getUniqueId(), filterId);
    sender.sendMessage(Component.translatable("filter.applied_to", Component.text(filterId), Component.text(target.getName())));
  }

  @CommandDescription("Remove a voice filter from a player")
  @CommandMethod("voice filter remove <player> <filter>")
  @CommandPermission("dreamvoice.admin")
  public void removeFilter(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target,
    @Argument(value = "filter", suggestions = "filter_ids") final @NotNull String filterId
  ) {
    final var service = DreamVoice.getService(VoiceFilterService.class);
    if (service == null)
      return;

    service.removeFilter(target.getUniqueId(), filterId);
    sender.sendMessage(Component.translatable("filter.removed_from", Component.text(filterId), Component.text(target.getName())));
  }

  @CommandDescription("Clear all voice filters from a player")
  @CommandMethod("voice filter clear <player>")
  @CommandPermission("dreamvoice.admin")
  public void clearFilters(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target
  ) {
    final var service = DreamVoice.getService(VoiceFilterService.class);
    if (service == null)
      return;

    service.clearFilters(target.getUniqueId());
    sender.sendMessage(Component.translatable("filter.cleared", Component.text(target.getName())));
  }

  @CommandDescription("Enable or disable automatic environmental voice filters for a player")
  @CommandMethod("voice filter auto <player> <enabled>")
  @CommandPermission("dreamvoice.admin")
  public void setAutoEnvironment(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target,
    @Argument("enabled") final boolean enabled
  ) {
    final var service = DreamVoice.getService(VoiceFilterService.class);
    if (service == null)
      return;

    service.setAutoEnvironmentEnabled(target.getUniqueId(), enabled);
    sender.sendMessage(Component.translatable("filter.auto", Component.text(enabled ? "enabled" : "disabled"), Component.text(target.getName())));
  }

  @CommandDescription("Reload all custom and file-based filters from disk")
  @CommandMethod("voice filter reload")
  @CommandPermission("dreamvoice.admin")
  public void reloadFilters(final @NotNull CommandSender sender) {
    final var service = DreamVoice.getService(VoiceFilterService.class);
    if (service == null)
      return;

    service.reloadFilters();
    sender.sendMessage(Component.translatable("filter.reloaded"));
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
    if (file != null && file.exists())
      sender.sendMessage(Component.translatable("filter.export_success", Component.text(filterId), Component.text(file.getName())));
    else
      sender.sendMessage(Component.translatable("filter.export_fail", Component.text(filterId)));
  }

}
