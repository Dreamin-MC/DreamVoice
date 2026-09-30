package fr.dreamin.dreamvoice.core.wiretap.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.recording.item.CassetteItem;
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

public final class WiretapCmd {

  private final @Nullable VoiceWiretapService wiretapService =
    DreamVoice.getService(VoiceWiretapService.class);

  private @Nullable VoiceWiretapService requireWiretapService(final @NotNull CommandSender sender) {
    if (this.wiretapService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return null;
    }
    return this.wiretapService;
  }

  @Suggestions("wiretaps")
  public List<String> suggWiretaps(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    if (this.wiretapService == null)
      return List.of();

    return this.wiretapService.getWiretaps().stream()
      .map(VoiceWiretap::getName)
      .filter(name -> name.startsWith(in.toLowerCase()))
      .sorted()
      .collect(Collectors.toList());
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

  @CommandDescription("Create a spatial wiretap listening point at current location")
  @CommandMethod("wiretap create <name> [distance] [filter]")
  @CommandPermission("dreamvoice.wiretap.manage")
  private void createWiretap(
    final @NotNull CommandSender sender,
    @Argument("name") final @NotNull String name,
    @Argument("distance") final @Nullable Double distance,
    @Argument(value = "filter", suggestions = "voice_filters") final @Nullable String filterId
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    final var loc = player.getLocation();
    final var wt = wiretapService.createWiretap(name, LocationUtils.toVoiceLocation(loc));
    if (distance != null)
      wt.setDistance(distance);
    if (filterId != null && !filterId.equalsIgnoreCase("none"))
      wt.setFilterId(filterId.toLowerCase());

    sender.sendMessage(Component.translatable("wiretap.created", Component.text(wt.getName())));
  }

  @CommandDescription("Add a spatial wiretap (alias for create)")
  @CommandMethod("wiretap add <name> [distance] [filter]")
  @CommandPermission("dreamvoice.wiretap.manage")
  private void addWiretap(
    final @NotNull CommandSender sender,
    @Argument("name") final @NotNull String name,
    @Argument("distance") final @Nullable Double distance,
    @Argument(value = "filter", suggestions = "voice_filters") final @Nullable String filterId
  ) {
    createWiretap(sender, name, distance, filterId);
  }

  @CommandDescription("Delete a wiretap listening point")
  @CommandMethod("wiretap delete <name>")
  @CommandPermission("dreamvoice.wiretap.manage")
  private void deleteWiretap(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name
  ) {
    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    final var wt = wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.translatable("wiretap.not_found", Component.text(name)));
      return;
    }

    wiretapService.removeWiretap(name);
    sender.sendMessage(Component.translatable("wiretap.removed", Component.text(name)));
  }

  @CommandDescription("Remove a wiretap (alias for delete)")
  @CommandMethod("wiretap remove <name>")
  @CommandPermission("dreamvoice.wiretap.manage")
  private void removeWiretap(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name
  ) {
    deleteWiretap(sender, name);
  }

  @CommandDescription("Attach a wiretap to the nearest entity or target entity")
  @CommandMethod("wiretap attach <name>")
  @CommandPermission("dreamvoice.wiretap.manage")
  private void attachWiretap(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    final var wt = wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.translatable("wiretap.not_found", Component.text(name)));
      return;
    }

    final var loc = player.getLocation();
    final var nearby = loc.getWorld().getNearbyEntities(loc, 5.0, 5.0, 5.0).stream()
      .filter(e -> !e.getUniqueId().equals(player.getUniqueId()))
      .findFirst()
      .orElse(null);

    if (nearby == null) {
      sender.sendMessage(Component.translatable("wiretap.no_entity_nearby"));
      return;
    }

    wt.setTargetEntityUuid(nearby.getUniqueId());
    sender.sendMessage(Component.translatable("wiretap.attached",
      Component.text(wt.getName()),
      Component.text(nearby.getType().name() + " (" + nearby.getUniqueId().toString().substring(0, 8) + ")")
    ));
  }

  @CommandDescription("Detach a wiretap from its attached entity")
  @CommandMethod("wiretap detach <name>")
  @CommandPermission("dreamvoice.wiretap.manage")
  private void detachWiretap(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name
  ) {
    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    final var wt = wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.translatable("wiretap.not_found", Component.text(name)));
      return;
    }

    if (!wt.isAttachedToEntity()) {
      sender.sendMessage(Component.translatable("wiretap.not_attached"));
      return;
    }

    wiretapService.detachFromEntity(name);
    sender.sendMessage(Component.translatable("wiretap.detached", Component.text(wt.getName())));
  }

  @CommandDescription("Listen / Subscribe to a wiretap")
  @CommandMethod("wiretap listen <name> [target]")
  @CommandPermission("dreamvoice.wiretap.use")
  private void listenWiretap(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name,
    @Argument("target") final @Nullable Player targetArg
  ) {
    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    final var target = resolvePlayer(sender, targetArg);
    if (target == null) return;

    final var wt = wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.translatable("wiretap.not_found", Component.text(name)));
      return;
    }

    wt.addListener(target.getUniqueId());
    sender.sendMessage(Component.translatable("wiretap.subscribed", Component.text(target.getName()), Component.text(wt.getName())));
  }

  @CommandDescription("Stop listening / Unsubscribe from a wiretap")
  @CommandMethod("wiretap unlisten <name> [target]")
  @CommandPermission("dreamvoice.wiretap.use")
  private void unlistenWiretap(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name,
    @Argument("target") final @Nullable Player targetArg
  ) {
    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    final var target = resolvePlayer(sender, targetArg);
    if (target == null) return;

    final var wt = wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.translatable("wiretap.not_found", Component.text(name)));
      return;
    }

    wt.removeListener(target.getUniqueId());
    sender.sendMessage(Component.translatable("wiretap.unsubscribed", Component.text(target.getName()), Component.text(wt.getName())));
  }

  @CommandDescription("Start recording a wiretap")
  @CommandMethod("wiretap record start <name>")
  @CommandPermission("dreamvoice.wiretap.record")
  private void startRecord(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name
  ) {
    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    final var wt = wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.translatable("wiretap.not_found", Component.text(name)));
      return;
    }

    if (wt.isRecording()) {
      sender.sendMessage(Component.translatable("wiretap.already_recording"));
      return;
    }

    wiretapService.startRecording(name);
    sender.sendMessage(Component.translatable("wiretap.record_started", Component.text(name)));
  }

  @CommandDescription("Stop recording a wiretap and optionally give a cassette")
  @CommandMethod("wiretap record stop <name> [giveCassette]")
  @CommandPermission("dreamvoice.wiretap.record")
  private void stopRecord(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name,
    @Argument("giveCassette") final @Nullable Boolean giveCassette
  ) {
    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    final var wt = wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.translatable("wiretap.not_found", Component.text(name)));
      return;
    }

    if (!wt.isRecording()) {
      sender.sendMessage(Component.translatable("wiretap.not_recording"));
      return;
    }

    final var rec = wiretapService.stopRecording(name);
    if (rec == null) {
      sender.sendMessage(Component.translatable("record.failed", Component.text("Error while stopping recording")));
      return;
    }

    sender.sendMessage(Component.translatable("wiretap.record_stopped",
      Component.text(name),
      Component.text(String.format("%.1f", rec.getDurationSeconds())),
      Component.text(rec.getUuid().toString().substring(0, 8))
    ));

    if (giveCassette != null && giveCassette && sender instanceof Player player) {
      final var recService = DreamVoice.getService(VoiceRecordingService.class);
      if (recService == null) {
        sender.sendMessage(Component.translatable("common.service_unavailable"));
        return;
      }
      final var item = CassetteItem.create(rec, name, player);
      player.getInventory().addItem(item);
      player.sendMessage(Component.translatable("record.segment_cassette_given"));
    }
  }

  @CommandDescription("Give a cassette of the latest wiretap recording")
  @CommandMethod("wiretap cassette <name> [player]")
  @CommandPermission("dreamvoice.wiretap.record")
  private void giveCassette(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name,
    @Argument("player") final @Nullable Player targetArg
  ) {
    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    final var player = resolvePlayer(sender, targetArg);
    if (player == null)
      return;

    final var wt = wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.translatable("wiretap.not_found", Component.text(name)));
      return;
    }

    final var recordings = wt.getRecordings();
    if (recordings.isEmpty()) {
      sender.sendMessage(Component.translatable("wiretap.no_recordings"));
      return;
    }

    final var latest = recordings.getLast();
    final var recService = DreamVoice.getService(VoiceRecordingService.class);
    if (recService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return;
    }

    final var item = CassetteItem.create(latest, name, player);
    player.getInventory().addItem(item);
    sender.sendMessage(Component.translatable("record.cassette_given", Component.text(latest.getUuid().toString().substring(0, 8)), Component.text(player.getName())));
  }

  @CommandDescription("Show detailed info of a wiretap")
  @CommandMethod("wiretap info <name>")
  @CommandPermission("dreamvoice.wiretap.manage")
  private void showInfo(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name
  ) {
    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    final var wt = wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.translatable("wiretap.not_found", Component.text(name)));
      return;
    }

    final var loc = wt.getLocation();
    final var targetEntity = wt.getTargetEntityUuid() != null ? Bukkit.getEntity(wt.getTargetEntityUuid()) : null;
    final var attached = targetEntity != null ? targetEntity.getType().name() + " (" + targetEntity.getUniqueId().toString().substring(0, 8) + ")" : "None";

    sender.sendMessage(Component.translatable("wiretap.info_header", Component.text(wt.getName().toUpperCase())));
    sender.sendMessage(Component.translatable("wiretap.info_position", Component.text(String.format("%.1f, %.1f, %.1f (%s)", loc.x(), loc.y(), loc.z(), loc.world()))));
    sender.sendMessage(Component.translatable("wiretap.info_attached", Component.text(attached)));
    sender.sendMessage(Component.translatable("wiretap.info_range", Component.text(wt.getDistance())));
    sender.sendMessage(Component.translatable("wiretap.info_filter", Component.text(wt.getFilterId() != null ? wt.getFilterId() : "none")));
    sender.sendMessage(Component.translatable("wiretap.info_recording", Component.text(wt.isRecording() ? "YES" : "NO")));
    sender.sendMessage(Component.translatable("wiretap.info_recordings_count", Component.text(wt.getRecordings().size())));
    sender.sendMessage(Component.translatable("wiretap.info_listeners", Component.text(wt.getListeners().size())));
  }

  @CommandDescription("List all active wiretaps")
  @CommandMethod("wiretap list")
  @CommandPermission("dreamvoice.wiretap.use")
  private void listWiretaps(final @NotNull CommandSender sender) {
    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    final var wiretaps = wiretapService.getWiretaps();
    if (wiretaps.isEmpty()) {
      sender.sendMessage(Component.translatable("wiretap.list_empty"));
      return;
    }

    sender.sendMessage(Component.translatable("wiretap.list_header", Component.text(wiretaps.size())));

    for (final var wt : wiretaps) {
      sender.sendMessage(Component.translatable("wiretap.list_item",
        Component.text(wt.getName()),
        Component.text(wt.getDistance()),
        Component.text(wt.getListeners().size())
      ));
    }
  }

  private @Nullable Player resolvePlayer(final @NotNull CommandSender sender, final @Nullable Player targetArg) {
    if (targetArg != null)
      return targetArg;
    if (sender instanceof Player p)
      return p;
    sender.sendMessage(Component.translatable("common.specify_player"));
    return null;
  }

  @CommandDescription("Save all wiretaps to disk")
  @CommandMethod("wiretap save")
  @CommandPermission("dreamvoice.wiretap.save")
  private void saveWiretaps(final @NotNull CommandSender sender) {
    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    wiretapService.save();
    sender.sendMessage(Component.translatable("persistence.wiretaps_saved"));
  }

  @CommandDescription("Reload all wiretaps from disk")
  @CommandMethod("wiretap reload")
  @CommandPermission("dreamvoice.wiretap.reload")
  private void reloadWiretaps(final @NotNull CommandSender sender) {
    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    wiretapService.load();
    sender.sendMessage(Component.translatable("persistence.wiretaps_reloaded", Component.text(wiretapService.getWiretaps().size())));
  }

}
