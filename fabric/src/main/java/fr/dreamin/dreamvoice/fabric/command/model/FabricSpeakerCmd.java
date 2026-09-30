package fr.dreamin.dreamvoice.fabric.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import fr.dreamin.dreamvoice.api.speaker.model.SpeakerMode;
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

@DreamCmd
public final class FabricSpeakerCmd {

  @Suggestions("speakers")
  public List<String> suggestSpeakers(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeakerService() == null)
      return Collections.emptyList();
    return api.getSpeakerService().getSpeakers().stream()
      .map(Speaker::getName)
      .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
      .toList();
  }

  @Suggestions("speaker_players")
  public List<String> suggestPlayers(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    return FabricCommandUtils.suggestPlayers(ctx.sender(), input);
  }

  @Suggestions("recordings")
  public List<String> suggestRecordings(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null)
      return Collections.emptyList();
    return api.getRecordingService().getVoiceRecordings().stream()
      .map(r -> r.getUuid().toString())
      .filter(id -> id.toLowerCase().startsWith(input.toLowerCase()))
      .toList();
  }

  @Command("speaker|voicespeaker list")
  @CommandDescription("List all active speakers")
  @Permission("dreamvoice.admin.speaker.list")
  public void list(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeakerService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var speakers = api.getSpeakerService().getSpeakers();
    FabricCommandUtils.sendSuccess(source, "speaker.list_header", speakers.size());
    for (final var spk : speakers) {
      final var loc = spk.getLocation();
      final var locStr = loc.world() + " (" + (int) loc.x() + ", " + (int) loc.y() + ", " + (int) loc.z() + ")";
      FabricCommandUtils.sendSuccess(source, "speaker.list_item",
        spk.getName(),
        (spk.getDistance() != null ? spk.getDistance() : 16.0f),
        spk.getMode().name(),
        locStr
      );
    }
  }

  @Command("speaker|voicespeaker create <name> [range]")
  @Command("speaker|voicespeaker add <name> [range]")
  @CommandDescription("Create a new speaker at current position")
  @Permission("dreamvoice.admin.speaker.create")
  public void create(
    final @NotNull CommandSourceStack source,
    @Argument("name") final @NotNull String name,
    @Argument("range") @Default("16.0") final float range
  ) {
    if (!source.isPlayer()) {
      FabricCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var api = DreamVoiceAPI.get();
    if (api.getSpeakerService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    if (api.getSpeakerService().getSpeaker(name) != null) {
      FabricCommandUtils.sendFailure(source, "speaker.already_exists", name);
      return;
    }

    final var p = source.getPlayer();
    if (p == null) {
      FabricCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var loc = FabricCommandUtils.toVoiceLocation(p);

    Speaker.builder()
      .name(name)
      .location(loc)
      .distance(range)
      .build();

    FabricCommandUtils.sendSuccess(source, "speaker.created", name, range);
  }

  @Command("speaker|voicespeaker delete <name>")
  @Command("speaker|voicespeaker remove <name>")
  @CommandDescription("Delete a speaker by name")
  @Permission("dreamvoice.admin.speaker.delete")
  public void delete(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "speakers") final @NotNull String name
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeakerService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var spk = api.getSpeakerService().getSpeaker(name);
    if (spk == null) {
      FabricCommandUtils.sendFailure(source, "speaker.not_found", name);
      return;
    }

    api.getSpeakerService().unregister(name);
    FabricCommandUtils.sendSuccess(source, "speaker.removed", name);
  }

  @Command("speaker|voicespeaker distance <name> <distance>")
  @CommandDescription("Set the broadcast distance of a speaker")
  @Permission("dreamvoice.admin.speaker.distance")
  public void setDistance(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "speakers") final @NotNull String name,
    @Argument("distance") final float distance
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeakerService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var spk = api.getSpeakerService().getSpeaker(name);
    if (spk == null) {
      FabricCommandUtils.sendFailure(source, "speaker.not_found", name);
      return;
    }

    spk.updateDistance(distance);
    FabricCommandUtils.sendSuccess(source, "speaker.distance_set", name, distance);
  }

  @Command("speaker|voicespeaker mode <name> <mode>")
  @CommandDescription("Set speaker access mode (GLOBAL or RESTRICTED)")
  @Permission("dreamvoice.admin.speaker.mode")
  public void mode(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "speakers") final @NotNull String name,
    @Argument("mode") final @NotNull SpeakerMode mode
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeakerService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var spk = api.getSpeakerService().getSpeaker(name);
    if (spk == null) {
      FabricCommandUtils.sendFailure(source, "speaker.not_found", name);
      return;
    }

    spk.setMode(mode);
    FabricCommandUtils.sendSuccess(source, "speaker.mode_set", name, mode.name());
  }

  @Command("speaker|voicespeaker link <name> [target]")
  @CommandDescription("Link a speaker to a player's audio stream")
  @Permission("dreamvoice.admin.speaker.link")
  public void link(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "speakers") final @NotNull String name,
    @Argument(value = "target", suggestions = "speaker_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeakerService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var spk = api.getSpeakerService().getSpeaker(name);
    if (spk == null) {
      FabricCommandUtils.sendFailure(source, "speaker.not_found", name);
      return;
    }

    final var target = FabricCommandUtils.resolvePlayer(source, targetName);
    if (target == null) {
      FabricCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    spk.setMode(SpeakerMode.RESTRICTED);
    spk.linkSpeaker(target.getUUID());
    FabricCommandUtils.sendSuccess(source, "speaker.linked", name, target.getName().getString());
  }

  @Command("speaker|voicespeaker unlink <name> [target]")
  @CommandDescription("Unlink a speaker from a player")
  @Permission("dreamvoice.admin.speaker.unlink")
  public void unlink(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "speakers") final @NotNull String name,
    @Argument(value = "target", suggestions = "speaker_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeakerService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var spk = api.getSpeakerService().getSpeaker(name);
    if (spk == null) {
      FabricCommandUtils.sendFailure(source, "speaker.not_found", name);
      return;
    }

    final var target = FabricCommandUtils.resolvePlayer(source, targetName);
    if (target != null)
      spk.unlinkSpeaker(target.getUUID());
    else
      spk.clearAllowedSpeakers();

    FabricCommandUtils.sendSuccess(source, "speaker.unlinked", name);
  }

  @Command("speaker|voicespeaker play record <name> <recording> [loop]")
  @CommandDescription("Play a voice recording through a speaker")
  @Permission("dreamvoice.admin.speaker.play")
  public void playRecord(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "speakers") final @NotNull String name,
    @Argument(value = "recording", suggestions = "recordings") final @NotNull String recordingIdRaw,
    @Argument("loop") @Default("false") final boolean loop
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeakerService() == null || api.getRecordingService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    var rec = api.getRecordingService().getVoiceRecordings().stream()
      .filter(r -> r.getUuid().toString().equalsIgnoreCase(recordingIdRaw) || r.getUuid().toString().toLowerCase().startsWith(recordingIdRaw.toLowerCase()))
      .findFirst()
      .orElse(null);

    if (rec == null) {
      FabricCommandUtils.sendFailure(source, "record.not_found", recordingIdRaw);
      return;
    }

    final var s1 = "record:" + rec.getUuid().toString().substring(0, 8);
    if ("all".equalsIgnoreCase(name)) {
      final var all = api.getSpeakerService().getSpeakers();
      for (final var s : all)
        api.getSpeakerService().playRecording(s, rec, loop);
      FabricCommandUtils.sendSuccess(source, "speaker.playing", s1, "all");
      return;
    }

    final var spk = api.getSpeakerService().getSpeaker(name);
    if (spk == null) {
      FabricCommandUtils.sendFailure(source, "speaker.not_found", name);
      return;
    }

    api.getSpeakerService().playRecording(spk, rec, loop);
    FabricCommandUtils.sendSuccess(source, "speaker.playing", s1, name);
  }

  @Command("speaker|voicespeaker play file <name> <fileName> [loop]")
  @CommandDescription("Play an audio file through a speaker")
  @Permission("dreamvoice.admin.speaker.play")
  public void playFile(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "speakers") final @NotNull String name,
    @Argument("fileName") final @NotNull String fileName,
    @Argument("loop") @Default("false") final boolean loop
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeakerService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    if ("all".equalsIgnoreCase(name)) {
      final var all = api.getSpeakerService().getSpeakers();
      api.getSpeakerService().playSoundFile(all, fileName, loop);
      FabricCommandUtils.sendSuccess(source, "speaker.playing", fileName, "all");
      return;
    }

    final var spk = api.getSpeakerService().getSpeaker(name);
    if (spk == null) {
      FabricCommandUtils.sendFailure(source, "speaker.not_found", name);
      return;
    }

    api.getSpeakerService().playSoundFile(spk, fileName, loop);
    FabricCommandUtils.sendSuccess(source, "speaker.playing", fileName, name);
  }

  @Command("speaker|voicespeaker play url <name> <url> [loop]")
  @CommandDescription("Stream audio from a URL through a speaker")
  @Permission("dreamvoice.admin.speaker.play")
  public void playUrl(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "speakers") final @NotNull String name,
    @Argument("url") final @NotNull String url,
    @Argument("loop") @Default("false") final boolean loop
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeakerService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    if ("all".equalsIgnoreCase(name)) {
      final var all = api.getSpeakerService().getSpeakers();
      api.getSpeakerService().playSoundUrl(all, url, loop);
      FabricCommandUtils.sendSuccess(source, "speaker.playing", url, "all");
      return;
    }

    final var spk = api.getSpeakerService().getSpeaker(name);
    if (spk == null) {
      FabricCommandUtils.sendFailure(source, "speaker.not_found", name);
      return;
    }

    api.getSpeakerService().playSoundUrl(spk, url, loop);
    FabricCommandUtils.sendSuccess(source, "speaker.playing", url, name);
  }

  @Command("speaker|voicespeaker stop <name>")
  @CommandDescription("Stop sound on a speaker")
  @Permission("dreamvoice.admin.speaker.stop")
  public void stop(
    final @NotNull CommandSourceStack source,
    @Argument(value = "name", suggestions = "speakers") final @NotNull String name
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeakerService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    if ("all".equalsIgnoreCase(name)) {
      for (final var s : api.getSpeakerService().getSpeakers())
        api.getSpeakerService().stopSound(s);
      FabricCommandUtils.sendSuccess(source, "speaker.stopped", "all");
      return;
    }

    final var spk = api.getSpeakerService().getSpeaker(name);
    if (spk == null) {
      FabricCommandUtils.sendFailure(source, "speaker.not_found", name);
      return;
    }

    api.getSpeakerService().stopSound(spk);
    FabricCommandUtils.sendSuccess(source, "speaker.stopped", name);
  }
}
