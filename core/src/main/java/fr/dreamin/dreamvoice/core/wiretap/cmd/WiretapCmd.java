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
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class WiretapCmd {

  private final @NotNull VoiceWiretapService wiretapService =
    DreamVoice.getService(VoiceWiretapService.class);

  @Suggestions("wiretaps")
  public List<String> suggWiretaps(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
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

    final var loc = player.getLocation();
    final var wt = this.wiretapService.createWiretap(name, loc);
    if (distance != null)
      wt.setDistance(distance);
    if (filterId != null && !filterId.equalsIgnoreCase("none"))
      wt.setFilterId(filterId.toLowerCase());

    sender.sendMessage(
      Component.text("[WIRETAP] Point d'écoute '", NamedTextColor.GREEN)
        .append(Component.text(wt.getName(), NamedTextColor.YELLOW))
        .append(Component.text(String.format("' créé en (%.1f, %.1f, %.1f) [Portée=%.1fm, Filtre=%s] !", loc.getX(), loc.getY(), loc.getZ(), wt.getDistance(), wt.getFilterId() != null ? wt.getFilterId() : "none"), NamedTextColor.GREEN))
    );
  }

  @CommandDescription("Delete a wiretap listening point")
  @CommandMethod("wiretap delete <name>")
  @CommandPermission("dreamvoice.wiretap.manage")
  private void deleteWiretap(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name
  ) {
    final var wt = this.wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.text("[WIRETAP] Point d'écoute introuvable: " + name, NamedTextColor.RED));
      return;
    }

    this.wiretapService.removeWiretap(name);
    sender.sendMessage(Component.text("[WIRETAP] Point d'écoute '" + name + "' supprimé avec succès.", NamedTextColor.GREEN));
  }

  @CommandDescription("Listen / Subscribe to a wiretap")
  @CommandMethod("wiretap listen <name> [target]")
  @CommandPermission("dreamvoice.wiretap.use")
  private void listenWiretap(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name,
    @Argument("target") final @Nullable Player targetArg
  ) {
    final var target = resolvePlayer(sender, targetArg);
    if (target == null) return;

    final var wt = this.wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.text("[WIRETAP] Point d'écoute introuvable: " + name, NamedTextColor.RED));
      return;
    }

    wt.addListener(target.getUniqueId());
    sender.sendMessage(
      Component.text("[WIRETAP] ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(" écoute désormais le micro '", NamedTextColor.GREEN))
        .append(Component.text(wt.getName(), NamedTextColor.AQUA))
        .append(Component.text("' en direct !", NamedTextColor.GREEN))
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
    final var target = resolvePlayer(sender, targetArg);
    if (target == null) return;

    final var wt = this.wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.text("[WIRETAP] Point d'écoute introuvable: " + name, NamedTextColor.RED));
      return;
    }

    wt.removeListener(target.getUniqueId());
    sender.sendMessage(
      Component.text("[WIRETAP] ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(" n'écoute plus le micro '", NamedTextColor.GREEN))
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
    final var wt = this.wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.text("[WIRETAP] Point d'écoute introuvable: " + name, NamedTextColor.RED));
      return;
    }

    if (wt.isRecording()) {
      sender.sendMessage(Component.text("[WIRETAP] Cet espion est déjà en train d'enregistrer !", NamedTextColor.RED));
      return;
    }

    this.wiretapService.startRecording(name);
    sender.sendMessage(
      Component.text("[WIRETAP] Enregistrement démarré sur '", NamedTextColor.GREEN)
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
    final var wt = this.wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.text("[WIRETAP] Point d'écoute introuvable: " + name, NamedTextColor.RED));
      return;
    }

    if (!wt.isRecording()) {
      sender.sendMessage(Component.text("[WIRETAP] Aucun enregistrement en cours sur ce micro.", NamedTextColor.GRAY));
      return;
    }

    final var rec = this.wiretapService.stopRecording(name);
    if (rec == null) {
      sender.sendMessage(Component.text("[WIRETAP] Erreur lors de l'arrêt de l'enregistrement.", NamedTextColor.RED));
      return;
    }

    sender.sendMessage(
      Component.text("[WIRETAP] Enregistrement arrêté sur '", NamedTextColor.GREEN)
        .append(Component.text(name, NamedTextColor.YELLOW))
        .append(Component.text(String.format("' (Durée: %.1fs, ID: %s)", rec.getDurationSeconds(), rec.getUuid().toString().substring(0, 8)), NamedTextColor.AQUA))
    );

    if (giveCassette != null && giveCassette && sender instanceof Player player) {
      final var recService = DreamVoice.getService(VoiceRecordingService.class);
      if (recService != null) {
        final var item = recService.createCassette(rec);
        player.getInventory().addItem(item);
        player.sendMessage(Component.text("[WIRETAP] Cassette audio ajoutée à votre inventaire !", NamedTextColor.GREEN));
      }
    }
  }

  @CommandDescription("Give a cassette of the latest wiretap recording")
  @CommandMethod("wiretap cassette <name> <player>")
  @CommandPermission("dreamvoice.wiretap.record")
  private void giveCassette(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name,
    @Argument("player") final @NotNull Player player
  ) {
    final var wt = this.wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.text("[WIRETAP] Point d'écoute introuvable: " + name, NamedTextColor.RED));
      return;
    }

    final var recordings = wt.getRecordings();
    if (recordings.isEmpty()) {
      sender.sendMessage(Component.text("[WIRETAP] Aucun enregistrement disponible pour ce micro.", NamedTextColor.GRAY));
      return;
    }

    final var latest = recordings.get(recordings.size() - 1);
    final var recService = DreamVoice.getService(VoiceRecordingService.class);
    if (recService != null) {
      final var item = recService.createCassette(latest);
      player.getInventory().addItem(item);
      sender.sendMessage(Component.text("[WIRETAP] Cassette de l'espion '" + name + "' donnée à " + player.getName() + " !", NamedTextColor.GREEN));
    }
  }

  @CommandDescription("Show detailed info of a wiretap")
  @CommandMethod("wiretap info <name>")
  @CommandPermission("dreamvoice.wiretap.manage")
  private void showInfo(
    final @NotNull CommandSender sender,
    @Argument(value = "name", suggestions = "wiretaps") final @NotNull String name
  ) {
    final var wt = this.wiretapService.getWiretap(name);
    if (wt == null) {
      sender.sendMessage(Component.text("[WIRETAP] Point d'écoute introuvable: " + name, NamedTextColor.RED));
      return;
    }

    final var loc = wt.getLocation();
    sender.sendMessage(Component.text("==== [WIRETAP: " + wt.getName().toUpperCase() + "] ====", NamedTextColor.GOLD));
    sender.sendMessage(Component.text("Position: ", NamedTextColor.GRAY).append(Component.text(String.format("%.1f, %.1f, %.1f (%s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld() != null ? loc.getWorld().getName() : "?"), NamedTextColor.AQUA)));
    sender.sendMessage(Component.text("Portée: ", NamedTextColor.GRAY).append(Component.text(wt.getDistance() + "m", NamedTextColor.YELLOW)));
    sender.sendMessage(Component.text("Filtre: ", NamedTextColor.GRAY).append(Component.text(wt.getFilterId() != null ? wt.getFilterId() : "none", NamedTextColor.AQUA)));
    sender.sendMessage(Component.text("Enregistrement en cours: ", NamedTextColor.GRAY).append(Component.text(wt.isRecording() ? "OUI 🔴" : "NON", wt.isRecording() ? NamedTextColor.RED : NamedTextColor.GREEN)));
    sender.sendMessage(Component.text("Nombre d'enregistrements: ", NamedTextColor.GRAY).append(Component.text(wt.getRecordings().size(), NamedTextColor.YELLOW)));
    sender.sendMessage(Component.text("Auditeurs connectés: ", NamedTextColor.GRAY).append(Component.text(wt.getListeners().size() + " joueur(s)", NamedTextColor.AQUA)));
  }

  @CommandDescription("List all active wiretaps")
  @CommandMethod("wiretap list")
  @CommandPermission("dreamvoice.wiretap.use")
  private void listWiretaps(final @NotNull CommandSender sender) {
    final var wiretaps = this.wiretapService.getWiretaps();
    if (wiretaps.isEmpty()) {
      sender.sendMessage(Component.text("[WIRETAP] Aucun point d'écoute actif.", NamedTextColor.GRAY));
      return;
    }

    sender.sendMessage(
      Component.text("[WIRETAP] Points d'écoute actifs (", NamedTextColor.GRAY)
        .append(Component.text(wiretaps.size(), NamedTextColor.YELLOW))
        .append(Component.text("):", NamedTextColor.GRAY))
    );

    for (final var wt : wiretaps) {
      final var loc = wt.getLocation();
      sender.sendMessage(
        Component.text(" - Micro '", NamedTextColor.GRAY)
          .append(Component.text(wt.getName(), NamedTextColor.AQUA))
          .append(Component.text(String.format("' @ (%.1f, %.1f, %.1f) [Portée=%.1fm, Auditeurs=%d, Rec=%s]", loc.getX(), loc.getY(), loc.getZ(), wt.getDistance(), wt.getListeners().size(), wt.isRecording() ? "REC" : "IDLE"), NamedTextColor.YELLOW))
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
