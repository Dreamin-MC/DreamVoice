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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
      sender.sendMessage(Component.text("[WIRETAP] Wiretap service unavailable.", NamedTextColor.RED));
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
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    final var loc = player.getLocation();
    final var wt = wiretapService.createWiretap(name, loc);
    if (distance != null)
      wt.setDistance(distance);
    if (filterId != null && !filterId.equalsIgnoreCase("none"))
      wt.setFilterId(filterId.toLowerCase());

    sender.sendMessage(
      Component.text("[WIRETAP] Listening point '", NamedTextColor.GREEN)
        .append(Component.text(wt.getName(), NamedTextColor.YELLOW))
        .append(Component.text(String.format("' created at (%.1f, %.1f, %.1f) [Range=%.1fm, Filter=%s]!", loc.getX(), loc.getY(), loc.getZ(), wt.getDistance(), wt.getFilterId() != null ? wt.getFilterId() : "none"), NamedTextColor.GREEN))
    );
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
      sender.sendMessage(Component.text("[WIRETAP] Listening point not found: " + name, NamedTextColor.RED));
      return;
    }

    wiretapService.removeWiretap(name);
    sender.sendMessage(Component.text("[WIRETAP] Listening point '" + name + "' successfully deleted.", NamedTextColor.GREEN));
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
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    final var wt = wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.text("[WIRETAP] Listening point not found: " + name, NamedTextColor.RED));
      return;
    }

    final var loc = player.getLocation();
    final var nearby = loc.getWorld().getNearbyEntities(loc, 5.0, 5.0, 5.0).stream()
      .filter(e -> !e.getUniqueId().equals(player.getUniqueId()))
      .findFirst()
      .orElse(null);

    if (nearby == null) {
      sender.sendMessage(Component.text("[WIRETAP] No entity found nearby (5 blocks) to attach wiretap.", NamedTextColor.RED));
      return;
    }

    wt.setTargetEntity(nearby);
    sender.sendMessage(
      Component.text("[WIRETAP] Wiretap '", NamedTextColor.GREEN)
        .append(Component.text(wt.getName(), NamedTextColor.YELLOW))
        .append(Component.text("' attached to entity ", NamedTextColor.GREEN))
        .append(Component.text(nearby.getType().name() + " (" + nearby.getUniqueId().toString().substring(0, 8) + ")", NamedTextColor.AQUA))
        .append(Component.text("! Listening will now follow its movements.", NamedTextColor.GREEN))
    );
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
      sender.sendMessage(Component.text("[WIRETAP] Listening point not found: " + name, NamedTextColor.RED));
      return;
    }

    if (!wt.isAttachedToEntity()) {
      sender.sendMessage(Component.text("[WIRETAP] This wiretap is not attached to any entity.", NamedTextColor.GRAY));
      return;
    }

    wiretapService.detachFromEntity(name);
    sender.sendMessage(
      Component.text("[WIRETAP] Wiretap '", NamedTextColor.GREEN)
        .append(Component.text(wt.getName(), NamedTextColor.YELLOW))
        .append(Component.text("' detached from entity (position frozen).", NamedTextColor.GREEN))
    );
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
      sender.sendMessage(Component.text("[WIRETAP] Listening point not found: " + name, NamedTextColor.RED));
      return;
    }

    wt.addListener(target.getUniqueId());
    sender.sendMessage(
      Component.text("[WIRETAP] ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(" is now listening to wiretap '", NamedTextColor.GREEN))
        .append(Component.text(wt.getName(), NamedTextColor.AQUA))
        .append(Component.text("' live!", NamedTextColor.GREEN))
    );
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
      sender.sendMessage(Component.text("[WIRETAP] Listening point not found: " + name, NamedTextColor.RED));
      return;
    }

    wt.removeListener(target.getUniqueId());
    sender.sendMessage(
      Component.text("[WIRETAP] ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(" stopped listening to wiretap '", NamedTextColor.GREEN))
        .append(Component.text(wt.getName(), NamedTextColor.AQUA))
        .append(Component.text("'.", NamedTextColor.GREEN))
    );
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
      sender.sendMessage(Component.text("[WIRETAP] Listening point not found: " + name, NamedTextColor.RED));
      return;
    }

    if (wt.isRecording()) {
      sender.sendMessage(Component.text("[WIRETAP] This wiretap is already recording!", NamedTextColor.RED));
      return;
    }

    wiretapService.startRecording(name);
    sender.sendMessage(
      Component.text("[WIRETAP] Recording started on '", NamedTextColor.GREEN)
        .append(Component.text(name, NamedTextColor.YELLOW))
        .append(Component.text("' 🔴", NamedTextColor.RED))
    );
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
      sender.sendMessage(Component.text("[WIRETAP] Listening point not found: " + name, NamedTextColor.RED));
      return;
    }

    if (!wt.isRecording()) {
      sender.sendMessage(Component.text("[WIRETAP] No active recording on this wiretap.", NamedTextColor.GRAY));
      return;
    }

    final var rec = wiretapService.stopRecording(name);
    if (rec == null) {
      sender.sendMessage(Component.text("[WIRETAP] Error while stopping recording.", NamedTextColor.RED));
      return;
    }

    sender.sendMessage(
      Component.text("[WIRETAP] Recording stopped on '", NamedTextColor.GREEN)
        .append(Component.text(name, NamedTextColor.YELLOW))
        .append(Component.text(String.format("' (Duration: %.1fs, ID: %s)", rec.getDurationSeconds(), rec.getUuid().toString().substring(0, 8)), NamedTextColor.AQUA))
    );

    if (giveCassette != null && giveCassette && sender instanceof Player player) {
      final var recService = DreamVoice.getService(VoiceRecordingService.class);
      if (recService == null) {
        sender.sendMessage(Component.text("[WIRETAP] Recording service unavailable.", NamedTextColor.RED));
        return;
      }
      final var item = recService.createCassette(rec);
      player.getInventory().addItem(item);
      player.sendMessage(Component.text("[WIRETAP] Voice cassette added to your inventory!", NamedTextColor.GREEN));
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
      sender.sendMessage(Component.text("[WIRETAP] Listening point not found: " + name, NamedTextColor.RED));
      return;
    }

    final var recordings = wt.getRecordings();
    if (recordings.isEmpty()) {
      sender.sendMessage(Component.text("[WIRETAP] No recordings available for this wiretap.", NamedTextColor.GRAY));
      return;
    }

    final var latest = recordings.getLast();
    final var recService = DreamVoice.getService(VoiceRecordingService.class);
    if (recService == null) {
      sender.sendMessage(Component.text("[WIRETAP] Recording service unavailable.", NamedTextColor.RED));
      return;
    }

    final var item = recService.createCassette(latest);
    player.getInventory().addItem(item);
    sender.sendMessage(Component.text("[WIRETAP] Cassette of wiretap '" + name + "' given to " + player.getName() + "!", NamedTextColor.GREEN));
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
      sender.sendMessage(Component.text("[WIRETAP] Listening point not found: " + name, NamedTextColor.RED));
      return;
    }

    final var loc = wt.getLocation();
    final var attached = wt.getTargetEntity() != null ? wt.getTargetEntity().getType().name() + " (" + wt.getTargetEntity().getUniqueId().toString().substring(0, 8) + ")" : "None";

    sender.sendMessage(Component.text("==== [WIRETAP: " + wt.getName().toUpperCase() + "] ====", NamedTextColor.GOLD));
    sender.sendMessage(Component.text("Position: ", NamedTextColor.GRAY).append(Component.text(String.format("%.1f, %.1f, %.1f (%s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld() != null ? loc.getWorld().getName() : "?"), NamedTextColor.AQUA)));
    sender.sendMessage(Component.text("Attached Entity: ", NamedTextColor.GRAY).append(Component.text(attached, NamedTextColor.LIGHT_PURPLE)));
    sender.sendMessage(Component.text("Range: ", NamedTextColor.GRAY).append(Component.text(wt.getDistance() + "m", NamedTextColor.YELLOW)));
    sender.sendMessage(Component.text("Filter: ", NamedTextColor.GRAY).append(Component.text(wt.getFilterId() != null ? wt.getFilterId() : "none", NamedTextColor.AQUA)));
    sender.sendMessage(Component.text("Recording: ", NamedTextColor.GRAY).append(Component.text(wt.isRecording() ? "YES 🔴" : "NO", wt.isRecording() ? NamedTextColor.RED : NamedTextColor.GREEN)));
    sender.sendMessage(Component.text("Recordings Count: ", NamedTextColor.GRAY).append(Component.text(wt.getRecordings().size(), NamedTextColor.YELLOW)));
    sender.sendMessage(Component.text("Connected Listeners: ", NamedTextColor.GRAY).append(Component.text(wt.getListeners().size() + " player(s)", NamedTextColor.AQUA)));
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
      sender.sendMessage(Component.text("[WIRETAP] No active listening points.", NamedTextColor.GRAY));
      return;
    }

    sender.sendMessage(
      Component.text("[WIRETAP] Active listening points (", NamedTextColor.GRAY)
        .append(Component.text(wiretaps.size(), NamedTextColor.YELLOW))
        .append(Component.text("):", NamedTextColor.GRAY))
    );

    for (final var wt : wiretaps) {
      final var loc = wt.getLocation();
      final var attached = wt.getTargetEntity() != null ? " [Attached=" + wt.getTargetEntity().getType().name() + "]" : "";
      sender.sendMessage(
        Component.text(" - Wiretap '", NamedTextColor.GRAY)
          .append(Component.text(wt.getName(), NamedTextColor.AQUA))
          .append(Component.text(String.format("' @ (%.1f, %.1f, %.1f) [Range=%.1fm, Listeners=%d, Rec=%s]%s", loc.getX(), loc.getY(), loc.getZ(), wt.getDistance(), wt.getListeners().size(), wt.isRecording() ? "REC" : "IDLE", attached), NamedTextColor.YELLOW))
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

  @CommandDescription("Save all wiretaps to disk")
  @CommandMethod("wiretap save")
  @CommandPermission("dreamvoice.wiretap.save")
  private void saveWiretaps(final @NotNull CommandSender sender) {
    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    wiretapService.save();
    sender.sendMessage(Component.text("[WIRETAP] All listening points successfully saved to disk!", NamedTextColor.GREEN));
  }

  @CommandDescription("Reload all wiretaps from disk")
  @CommandMethod("wiretap reload")
  @CommandPermission("dreamvoice.wiretap.reload")
  private void reloadWiretaps(final @NotNull CommandSender sender) {
    final var wiretapService = requireWiretapService(sender);
    if (wiretapService == null)
      return;

    wiretapService.load();
    sender.sendMessage(Component.text("[WIRETAP] Listening points successfully reloaded from disk (" + wiretapService.getWiretaps().size() + " active)!", NamedTextColor.GREEN));
  }

}
