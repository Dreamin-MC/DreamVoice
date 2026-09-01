package fr.dreamin.dreamvoice.core.radio.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.radio.model.RadioChannel;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
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

public final class RadioCmd {

  private final @Nullable VoiceRadioService radioService =
    DreamVoice.getService(VoiceRadioService.class);

  private @Nullable VoiceRadioService requireRadioService(final @NotNull CommandSender sender) {
    if (this.radioService == null) {
      sender.sendMessage(Component.text("[RADIO] Radio service unavailable.", NamedTextColor.RED));
      return null;
    }
    return this.radioService;
  }

  @Suggestions("radio_channels")
  public List<String> suggChannels(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    if (this.radioService == null)
      return List.of();

    return this.radioService.getChannels().stream()
      .map(RadioChannel::getName)
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


  @CommandDescription("Create a new radio frequency channel")
  @CommandMethod("radio create <channel> [filter] [rogerBeep]")
  @CommandPermission("dreamvoice.radio.manage")
  private void createRadio(
    final @NotNull CommandSender sender,
    @Argument("channel") final @NotNull String channelName,
    @Argument(value = "filter", suggestions = "voice_filters") final @Nullable String filterId,
    @Argument("rogerBeep") final @Nullable Boolean rogerBeep
  ) {
    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    final var channel = radioService.getOrCreateChannel(channelName);
    if (filterId != null)
      channel.setFilterId(filterId.toLowerCase());
    if (rogerBeep != null)
      channel.setRogerBeep(rogerBeep);

    sender.sendMessage(
      Component.text("[RADIO] Frequency channel ", NamedTextColor.GREEN)
        .append(Component.text(channel.getName().toUpperCase(), NamedTextColor.YELLOW))
        .append(Component.text(" successfully created! [Filter=", NamedTextColor.GREEN))
        .append(Component.text(channel.getFilterId(), NamedTextColor.AQUA))
        .append(Component.text(", RogerBeep=" + (channel.isRogerBeep() ? "ON" : "OFF") + "]", NamedTextColor.DARK_GRAY))
    );
  }

  @CommandDescription("Add a new radio frequency channel (alias for create)")
  @CommandMethod("radio add <channel> [filter] [rogerBeep]")
  @CommandPermission("dreamvoice.radio.manage")
  private void addRadio(
    final @NotNull CommandSender sender,
    @Argument("channel") final @NotNull String channelName,
    @Argument(value = "filter", suggestions = "voice_filters") final @Nullable String filterId,
    @Argument("rogerBeep") final @Nullable Boolean rogerBeep
  ) {
    createRadio(sender, channelName, filterId, rogerBeep);
  }

  @CommandDescription("Delete a radio frequency channel")
  @CommandMethod("radio delete <channel>")
  @CommandPermission("dreamvoice.radio.manage")
  private void deleteRadio(
    final @NotNull CommandSender sender,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName
  ) {
    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    final var ch = radioService.getChannel(channelName);
    if (ch == null) {
      sender.sendMessage(Component.text("[RADIO] Frequency channel not found: " + channelName, NamedTextColor.RED));
      return;
    }

    radioService.removeChannel(channelName);
    sender.sendMessage(
      Component.text("[RADIO] Frequency channel ", NamedTextColor.GREEN)
        .append(Component.text(channelName.toUpperCase(), NamedTextColor.YELLOW))
        .append(Component.text(" successfully deleted.", NamedTextColor.GREEN))
    );
  }

  @CommandDescription("Remove a radio frequency channel (alias for delete)")
  @CommandMethod("radio remove <channel>")
  @CommandPermission("dreamvoice.radio.manage")
  private void removeRadio(
    final @NotNull CommandSender sender,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName
  ) {
    deleteRadio(sender, channelName);
  }

  @CommandDescription("Show detailed info of a radio channel")
  @CommandMethod("radio info <channel>")
  @CommandPermission("dreamvoice.radio.use")
  private void infoRadio(
    final @NotNull CommandSender sender,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName
  ) {
    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    final var ch = radioService.getChannel(channelName);
    if (ch == null) {
      sender.sendMessage(Component.text("[RADIO] Frequency channel not found: " + channelName, NamedTextColor.RED));
      return;
    }

    sender.sendMessage(Component.text("==== [RADIO: " + ch.getName().toUpperCase() + "] ====", NamedTextColor.GOLD));
    sender.sendMessage(Component.text("Filter: ", NamedTextColor.GRAY).append(Component.text(ch.getFilterId() != null ? ch.getFilterId() : "none", NamedTextColor.AQUA)));
    sender.sendMessage(Component.text("Roger Beep: ", NamedTextColor.GRAY).append(Component.text(ch.isRogerBeep() ? "ENABLED" : "DISABLED", ch.isRogerBeep() ? NamedTextColor.GREEN : NamedTextColor.RED)));
    sender.sendMessage(Component.text("Members (" + ch.getMembers().size() + "): ", NamedTextColor.GRAY));
    for (final var uuid : ch.getMembers()) {
      final var p = Bukkit.getPlayer(uuid);
      final var name = p != null ? p.getName() : uuid.toString().substring(0, 8);
      sender.sendMessage(Component.text(" - ", NamedTextColor.DARK_GRAY).append(Component.text(name, NamedTextColor.YELLOW)));
    }
  }

  @CommandDescription("Kick a player from a radio channel")
  @CommandMethod("radio kick <channel> <player>")
  @CommandPermission("dreamvoice.radio.manage")
  private void kickRadio(
    final @NotNull CommandSender sender,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName,
    @Argument("player") final @NotNull Player target
  ) {
    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    final var ch = radioService.getChannel(channelName);
    if (ch == null) {
      sender.sendMessage(Component.text("[RADIO] Frequency channel not found: " + channelName, NamedTextColor.RED));
      return;
    }

    if (!ch.hasMember(target.getUniqueId())) {
      sender.sendMessage(Component.text("[RADIO] This player is not connected to this channel.", NamedTextColor.GRAY));
      return;
    }

    radioService.leaveChannel(target.getUniqueId());
    sender.sendMessage(Component.text("[RADIO] Player " + target.getName() + " removed from channel " + channelName.toUpperCase() + "!", NamedTextColor.GREEN));
    target.sendMessage(Component.text("[RADIO] You were removed from channel " + channelName.toUpperCase() + ".", NamedTextColor.YELLOW));
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

    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    radioService.joinChannel(player.getUniqueId(), channelName);
    player.sendMessage(
      Component.text("[RADIO] Connected to frequency ", NamedTextColor.GREEN)
        .append(Component.text(channelName.toUpperCase(), NamedTextColor.YELLOW))
        .append(Component.text("! You are now communicating on this channel.", NamedTextColor.GREEN))
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

    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    final var current = radioService.getChannelOfPlayer(player.getUniqueId());
    if (current == null) {
      player.sendMessage(Component.text("[RADIO] You are not connected to any radio frequency.", NamedTextColor.GRAY));
      return;
    }

    radioService.leaveChannel(player.getUniqueId());
    player.sendMessage(
      Component.text("[RADIO] Disconnected from frequency ", NamedTextColor.YELLOW)
        .append(Component.text(current.getName().toUpperCase(), NamedTextColor.AQUA))
        .append(Component.text(".", NamedTextColor.YELLOW))
    );
  }

  @CommandDescription("List all active radio frequencies")
  @CommandMethod("radio list")
  @CommandPermission("dreamvoice.radio.use")
  private void listRadio(final @NotNull CommandSender sender) {
    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    final var channels = radioService.getChannels();
    if (channels.isEmpty()) {
      sender.sendMessage(Component.text("[RADIO] No active radio frequencies.", NamedTextColor.GRAY));
      return;
    }

    sender.sendMessage(
      Component.text("[RADIO] Active frequencies (", NamedTextColor.GRAY)
        .append(Component.text(channels.size(), NamedTextColor.YELLOW))
        .append(Component.text("):", NamedTextColor.GRAY))
    );

    for (final var ch : channels) {
      sender.sendMessage(
        Component.text(" - Channel ", NamedTextColor.GRAY)
          .append(Component.text(ch.getName().toUpperCase(), NamedTextColor.AQUA))
          .append(Component.text(": ", NamedTextColor.GRAY))
          .append(Component.text(ch.getMembers().size() + " player(s)", NamedTextColor.YELLOW))
          .append(Component.text(" [RogerBeep=" + (ch.isRogerBeep() ? "ON" : "OFF") + ", Filter=" + ch.getFilterId() + "]", NamedTextColor.DARK_GRAY))
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
    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    final var ch = radioService.getChannel(channelName);
    if (ch == null) {
      sender.sendMessage(Component.text("[RADIO] Frequency channel not found: " + channelName, NamedTextColor.RED));
      return;
    }

    ch.setRogerBeep(enabled);
    sender.sendMessage(
      Component.text("[RADIO] Roger Beep for channel ", NamedTextColor.GREEN)
        .append(Component.text(channelName.toUpperCase(), NamedTextColor.AQUA))
        .append(Component.text(": ", NamedTextColor.GREEN))
        .append(Component.text(enabled ? "ENABLED" : "DISABLED", enabled ? NamedTextColor.YELLOW : NamedTextColor.RED))
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
    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    final var ch = radioService.getChannel(channelName);
    if (ch == null) {
      sender.sendMessage(Component.text("[RADIO] Frequency channel not found: " + channelName, NamedTextColor.RED));
      return;
    }

    ch.setFilterId(filterId.toLowerCase());
    sender.sendMessage(
      Component.text("[RADIO] Filter for channel ", NamedTextColor.GREEN)
        .append(Component.text(channelName.toUpperCase(), NamedTextColor.AQUA))
        .append(Component.text(" set to ", NamedTextColor.GREEN))
        .append(Component.text(filterId, NamedTextColor.YELLOW))
    );
  }

  @CommandDescription("Save all radio channels to disk")
  @CommandMethod("radio save")
  @CommandPermission("dreamvoice.radio.save")
  private void saveRadios(final @NotNull CommandSender sender) {
    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    radioService.save();
    sender.sendMessage(Component.text("[RADIO] All radio channels successfully saved to disk!", NamedTextColor.GREEN));
  }

  @CommandDescription("Reload all radio channels from disk")
  @CommandMethod("radio reload")
  @CommandPermission("dreamvoice.radio.reload")
  private void reloadRadios(final @NotNull CommandSender sender) {
    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    radioService.load();
    sender.sendMessage(Component.text("[RADIO] Radio channels successfully reloaded from disk (" + radioService.getChannels().size() + " active)!", NamedTextColor.GREEN));
  }

}
