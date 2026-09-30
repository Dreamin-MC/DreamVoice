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
      sender.sendMessage(Component.translatable("common.service_unavailable"));
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

    sender.sendMessage(Component.translatable("radio.created", Component.text(channel.getName())));
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
      sender.sendMessage(Component.translatable("radio.not_found", Component.text(channelName)));
      return;
    }

    radioService.removeChannel(channelName);
    sender.sendMessage(Component.translatable("radio.removed", Component.text(channelName)));
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
      sender.sendMessage(Component.translatable("radio.not_found", Component.text(channelName)));
      return;
    }

    sender.sendMessage(Component.translatable("radio.info_header", Component.text(ch.getName().toUpperCase())));
    sender.sendMessage(Component.translatable("radio.info_filter", Component.text(ch.getFilterId() != null ? ch.getFilterId() : "none")));
    sender.sendMessage(Component.translatable("radio.info_rogerbeep", Component.text(ch.isRogerBeep() ? "ENABLED" : "DISABLED")));
    sender.sendMessage(Component.translatable("radio.info_members", Component.text(ch.getMembers().size())));
    for (final var uuid : ch.getMembers()) {
      final var p = Bukkit.getPlayer(uuid);
      final var name = p != null ? p.getName() : uuid.toString().substring(0, 8);
      sender.sendMessage(Component.translatable("radio.info_member_item", Component.text(name)));
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
      sender.sendMessage(Component.translatable("radio.not_found", Component.text(channelName)));
      return;
    }

    if (!ch.hasMember(target.getUniqueId())) {
      sender.sendMessage(Component.translatable("radio.target_not_connected"));
      return;
    }

    radioService.leaveChannel(target.getUniqueId());
    sender.sendMessage(Component.translatable("radio.kicked", Component.text(target.getName()), Component.text(channelName.toUpperCase())));
    target.sendMessage(Component.translatable("radio.target_kicked", Component.text(channelName.toUpperCase())));
  }

  @CommandDescription("Join or tune into a radio frequency channel")
  @CommandMethod("radio join <channel>")
  @CommandPermission("dreamvoice.radio.use")
  private void joinRadio(
    final @NotNull CommandSender sender,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    radioService.joinChannel(player.getUniqueId(), channelName);
    player.sendMessage(Component.translatable("radio.connected", Component.text(channelName.toUpperCase())));
  }


  @CommandDescription("Leave your current radio channel")
  @CommandMethod("radio leave")
  @CommandPermission("dreamvoice.radio.use")
  private void leaveRadio(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    final var current = radioService.getChannelOfPlayer(player.getUniqueId());
    if (current == null) {
      player.sendMessage(Component.translatable("radio.not_connected"));
      return;
    }

    radioService.leaveChannel(player.getUniqueId());
    player.sendMessage(Component.translatable("radio.disconnected", Component.text(current.getName().toUpperCase())));
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
      sender.sendMessage(Component.translatable("radio.list_empty"));
      return;
    }

    sender.sendMessage(Component.translatable("radio.list_header", Component.text(channels.size())));

    for (final var ch : channels)
      sender.sendMessage(Component.translatable("radio.list_item",
        Component.text(ch.getName().toUpperCase()),
        Component.text(ch.getMembers().size()),
        Component.text(ch.isRogerBeep() ? "ON" : "OFF"),
        Component.text(ch.getFilterId() != null ? ch.getFilterId() : "none")
      ));
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
      sender.sendMessage(Component.translatable("radio.not_found", Component.text(channelName)));
      return;
    }

    ch.setRogerBeep(enabled);
    sender.sendMessage(Component.translatable("radio.rogerbeep_set", Component.text(channelName.toUpperCase()), Component.text(enabled ? "ENABLED" : "DISABLED")));
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
      sender.sendMessage(Component.translatable("radio.not_found", Component.text(channelName)));
      return;
    }

    ch.setFilterId(filterId.toLowerCase());
    sender.sendMessage(Component.translatable("radio.filter_set", Component.text(channelName.toUpperCase()), Component.text(filterId)));
  }

  @CommandDescription("Save all radio channels to disk")
  @CommandMethod("radio save")
  @CommandPermission("dreamvoice.radio.save")
  private void saveRadios(final @NotNull CommandSender sender) {
    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    radioService.save();
    sender.sendMessage(Component.translatable("radio.saved"));
  }

  @CommandDescription("Reload all radio channels from disk")
  @CommandMethod("radio reload")
  @CommandPermission("dreamvoice.radio.reload")
  private void reloadRadios(final @NotNull CommandSender sender) {
    final var radioService = requireRadioService(sender);
    if (radioService == null)
      return;

    radioService.load();
    sender.sendMessage(Component.translatable("radio.reloaded", Component.text(radioService.getChannels().size())));
  }

}
