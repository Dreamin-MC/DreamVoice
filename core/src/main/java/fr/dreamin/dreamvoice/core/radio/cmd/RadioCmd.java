package fr.dreamin.dreamvoice.core.radio.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.radio.model.RadioChannel;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public final class RadioCmd {

  private final @NotNull VoiceRadioService radioService =
    DreamVoice.getService(VoiceRadioService.class);

  @Suggestions("radio_channels")
  public List<String> suggChannels(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    return this.radioService.getChannels().stream()
      .map(RadioChannel::getName)
      .filter(name -> name.startsWith(in.toLowerCase()))
      .sorted()
      .collect(Collectors.toList());
  }

  @Suggestions("voice_filters")
  public List<String> suggFilters(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    final var filterService = DreamVoice.getService(fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService.class);
    final var list = new java.util.ArrayList<String>();
    list.add("none");
    if (filterService != null) {
      filterService.getAvailableFilters().stream()
        .map(fr.dreamin.dreamvoice.api.filter.model.VoiceFilter::getId)
        .forEach(list::add);
    }

    return list.stream()
      .filter(id -> id.startsWith(in.toLowerCase()))
      .sorted()
      .collect(Collectors.toList());
  }


  @CommandDescription("Create a new radio frequency channel")
  @CommandMethod("radio create <channel> [filter] [rogerBeep]")
  @CommandPermission("dreamvoice.radio.manage")
  private void createRadio(
    final @NotNull CommandSender sender,
    @Argument("channel") final @NotNull String channelName,
    @Argument(value = "filter", suggestions = "voice_filters") final @org.jetbrains.annotations.Nullable String filterId,
    @Argument("rogerBeep") final @org.jetbrains.annotations.Nullable Boolean rogerBeep
  ) {
    final var channel = this.radioService.getOrCreateChannel(channelName);
    if (filterId != null)
      channel.setFilterId(filterId.toLowerCase());
    if (rogerBeep != null)
      channel.setRogerBeep(rogerBeep);

    sender.sendMessage(
      Component.text("[RADIO] Canal de fréquence ", NamedTextColor.GREEN)
        .append(Component.text(channel.getName().toUpperCase(), NamedTextColor.YELLOW))
        .append(Component.text(" créé avec succès ! [Filtre=", NamedTextColor.GREEN))
        .append(Component.text(channel.getFilterId(), NamedTextColor.AQUA))
        .append(Component.text(", RogerBeep=" + (channel.isRogerBeep() ? "ON" : "OFF") + "]", NamedTextColor.DARK_GRAY))
    );
  }

  @CommandDescription("Delete a radio frequency channel")
  @CommandMethod("radio delete <channel>")
  @CommandPermission("dreamvoice.radio.manage")
  private void deleteRadio(
    final @NotNull CommandSender sender,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName
  ) {
    final var ch = this.radioService.getChannel(channelName);
    if (ch == null) {
      sender.sendMessage(Component.text("[RADIO] Canal introuvable: " + channelName, NamedTextColor.RED));
      return;
    }

    this.radioService.removeChannel(channelName);
    sender.sendMessage(
      Component.text("[RADIO] Canal ", NamedTextColor.GREEN)
        .append(Component.text(channelName.toUpperCase(), NamedTextColor.YELLOW))
        .append(Component.text(" supprimé avec succès.", NamedTextColor.GREEN))
    );
  }

  @CommandDescription("Join or tune into a radio frequency channel")
  @CommandMethod("radio join <channel>")
  @CommandPermission("dreamvoice.radio.use")
  private void joinRadio(
    final @NotNull CommandSender sender,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    this.radioService.joinChannel(player.getUniqueId(), channelName);
    player.sendMessage(
      Component.text("[RADIO] Connecté à la fréquence ", NamedTextColor.GREEN)
        .append(Component.text(channelName.toUpperCase(), NamedTextColor.YELLOW))
        .append(Component.text(" ! Vous communiquez maintenant sur ce canal.", NamedTextColor.GREEN))
    );
  }


  @CommandDescription("Leave your current radio channel")
  @CommandMethod("radio leave")
  @CommandPermission("dreamvoice.radio.use")
  private void leaveRadio(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    final var current = this.radioService.getChannelOfPlayer(player.getUniqueId());
    if (current == null) {
      player.sendMessage(Component.text("[RADIO] Vous n'êtes connecté à aucune fréquence.", NamedTextColor.GRAY));
      return;
    }

    this.radioService.leaveChannel(player.getUniqueId());
    player.sendMessage(
      Component.text("[RADIO] Déconnecté de la fréquence ", NamedTextColor.YELLOW)
        .append(Component.text(current.getName().toUpperCase(), NamedTextColor.AQUA))
        .append(Component.text(".", NamedTextColor.YELLOW))
    );
  }

  @CommandDescription("List all active radio frequencies")
  @CommandMethod("radio list")
  @CommandPermission("dreamvoice.radio.use")
  private void listRadio(final @NotNull CommandSender sender) {
    final var channels = this.radioService.getChannels();
    if (channels.isEmpty()) {
      sender.sendMessage(Component.text("[RADIO] Aucune fréquence radio active actuellement.", NamedTextColor.GRAY));
      return;
    }

    sender.sendMessage(
      Component.text("[RADIO] Fréquences actives (", NamedTextColor.GRAY)
        .append(Component.text(channels.size(), NamedTextColor.YELLOW))
        .append(Component.text("):", NamedTextColor.GRAY))
    );

    for (final var ch : channels) {
      sender.sendMessage(
        Component.text(" - Canal ", NamedTextColor.GRAY)
          .append(Component.text(ch.getName().toUpperCase(), NamedTextColor.AQUA))
          .append(Component.text(" : ", NamedTextColor.GRAY))
          .append(Component.text(ch.getMembers().size() + " joueur(s)", NamedTextColor.YELLOW))
          .append(Component.text(" [RogerBeep=" + (ch.isRogerBeep() ? "ON" : "OFF") + ", Filtre=" + ch.getFilterId() + "]", NamedTextColor.DARK_GRAY))
      );
    }
  }

  @CommandDescription("Toggle Roger Beep on a radio frequency")
  @CommandMethod("radio rogerbeep <channel> <enabled>")
  @CommandPermission("dreamvoice.radio.manage")
  private void toggleRogerBeep(
    final @NotNull CommandSender sender,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName,
    @Argument("enabled") final boolean enabled
  ) {
    final var ch = this.radioService.getChannel(channelName);
    if (ch == null) {
      sender.sendMessage(Component.text("[RADIO] Canal radio introuvable: " + channelName, NamedTextColor.RED));
      return;
    }

    ch.setRogerBeep(enabled);
    sender.sendMessage(
      Component.text("[RADIO] Roger Beep pour le canal ", NamedTextColor.GREEN)
        .append(Component.text(channelName.toUpperCase(), NamedTextColor.AQUA))
        .append(Component.text(" : ", NamedTextColor.GREEN))
        .append(Component.text(enabled ? "ACTIVÉ" : "DÉSACTIVÉ", enabled ? NamedTextColor.YELLOW : NamedTextColor.RED))
    );
  }

  @CommandDescription("Change the audio filter of a radio frequency")
  @CommandMethod("radio filter <channel> <filter>")
  @CommandPermission("dreamvoice.radio.manage")
  private void setFilter(
    final @NotNull CommandSender sender,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName,
    @Argument(value = "filter", suggestions = "voice_filters") final @NotNull String filterId
  ) {

    final var ch = this.radioService.getChannel(channelName);
    if (ch == null) {
      sender.sendMessage(Component.text("[RADIO] Canal radio introuvable: " + channelName, NamedTextColor.RED));
      return;
    }

    ch.setFilterId(filterId.toLowerCase());
    sender.sendMessage(
      Component.text("[RADIO] Filtre du canal ", NamedTextColor.GREEN)
        .append(Component.text(channelName.toUpperCase(), NamedTextColor.AQUA))
        .append(Component.text(" défini sur ", NamedTextColor.GREEN))
        .append(Component.text(filterId, NamedTextColor.YELLOW))
    );
  }

}
