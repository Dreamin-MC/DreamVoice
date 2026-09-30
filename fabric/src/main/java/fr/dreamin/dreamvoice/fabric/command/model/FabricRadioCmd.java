package fr.dreamin.dreamvoice.fabric.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.radio.model.RadioChannel;
import fr.dreamin.dreamvoice.fabric.command.FabricCommandUtils;
import fr.dreamin.dreamvoice.fabric.command.annotation.DreamCmd;
import net.minecraft.commands.CommandSourceStack;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@DreamCmd
public final class FabricRadioCmd {

  @Suggestions("radio_channels")
  public List<String> suggestChannels(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    final var api = DreamVoiceAPI.get();
    if (api.getRadioService() == null)
      return Collections.emptyList();
    return api.getRadioService().getChannels().stream()
      .map(RadioChannel::getName)
      .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
      .toList();
  }

  @Suggestions("voice_filters")
  public List<String> suggestFilters(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    final var api = DreamVoiceAPI.get();
    if (api.getFilterService() == null)
      return Collections.emptyList();
    return api.getFilterService().getAvailableFilters().stream()
      .map(VoiceFilter::getId)
      .filter(id -> id.toLowerCase().startsWith(input.toLowerCase()))
      .toList();
  }

  @Command("radio|voiceradio list")
  @CommandDescription("List all active radio channels")
  @Permission("dreamvoice.radio.list")
  public void list(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getRadioService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var channels = api.getRadioService().getChannels();
    if (channels.isEmpty()) {
      FabricCommandUtils.sendSuccess(source, "radio.list_empty");
      return;
    }

    FabricCommandUtils.sendSuccess(source, "radio.list_header", channels.size());
    for (final var ch : channels)
      FabricCommandUtils.sendSuccess(source, "radio.list_item", ch.getName(), ch.getMembers().size(), ch.isRogerBeep(), ch.getFilterId() != null ? ch.getFilterId() : "none");
  }

  @Command("radio|voiceradio create <channel> [filter] [rogerBeep]")
  @Command("radio|voiceradio add <channel> [filter] [rogerBeep]")
  @CommandDescription("Create a new radio channel")
  @Permission("dreamvoice.admin.radio.create")
  public void create(
    final @NotNull CommandSourceStack source,
    @Argument("channel") final @NotNull String channelName,
    @Argument(value = "filter", suggestions = "voice_filters") final @Nullable String filterName,
    @Argument(value = "rogerBeep") @Default("true") final boolean rogerBeep
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRadioService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var ch = api.getRadioService().getOrCreateChannel(channelName);
    if (filterName != null && !filterName.isBlank())
      ch.setFilterId(filterName);
    ch.setRogerBeep(rogerBeep);

    FabricCommandUtils.sendSuccess(source, "radio.created", channelName);
  }

  @Command("radio|voiceradio delete <channel>")
  @Command("radio|voiceradio remove <channel>")
  @CommandDescription("Delete an existing radio channel")
  @Permission("dreamvoice.admin.radio.delete")
  public void delete(
    final @NotNull CommandSourceStack source,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRadioService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var ch = api.getRadioService().getChannel(channelName);
    if (ch == null) {
      FabricCommandUtils.sendFailure(source, "radio.not_found", channelName);
      return;
    }

    api.getRadioService().unregister(channelName);
    FabricCommandUtils.sendSuccess(source, "radio.removed", channelName);
  }

  @Command("radio|voiceradio join <channel>")
  @CommandDescription("Join a radio channel")
  @Permission("dreamvoice.radio.join")
  public void join(
    final @NotNull CommandSourceStack source,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName
  ) {
    if (!source.isPlayer()) {
      FabricCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var api = DreamVoiceAPI.get();
    if (api.getRadioService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var channel = api.getRadioService().getChannel(channelName);
    if (channel == null) {
      FabricCommandUtils.sendFailure(source, "radio.not_found", channelName);
      return;
    }

    api.getRadioService().joinChannel(Objects.requireNonNull(source.getPlayer()).getUUID(), channelName);
    FabricCommandUtils.sendSuccess(source, "radio.connected", channelName);
  }

  @Command("radio|voiceradio leave")
  @CommandDescription("Leave your current radio channel")
  @Permission("dreamvoice.radio.leave")
  public void leave(final @NotNull CommandSourceStack source) {
    if (!source.isPlayer()) {
      FabricCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var api = DreamVoiceAPI.get();
    if (api.getRadioService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    api.getRadioService().leaveChannel(Objects.requireNonNull(source.getPlayer()).getUUID());
    FabricCommandUtils.sendSuccess(source, "radio.disconnected");
  }
}
