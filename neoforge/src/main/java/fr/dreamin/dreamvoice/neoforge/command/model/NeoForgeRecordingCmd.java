package fr.dreamin.dreamvoice.neoforge.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.neoforge.command.NeoForgeCommandUtils;
import fr.dreamin.dreamvoice.neoforge.command.annotation.DreamCmd;
import fr.dreamin.dreamvoice.neoforge.item.NeoForgeCassetteItem;
import net.minecraft.commands.CommandSourceStack;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@DreamCmd
public final class NeoForgeRecordingCmd {

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

  @Suggestions("export_formats")
  public List<String> suggestExportFormats(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    return List.of("mp3", "ogg", "wav").stream()
      .filter(f -> f.startsWith(input.toLowerCase()))
      .toList();
  }

  @Suggestions("radio_channels")
  public List<String> suggestRadioChannels(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    final var api = DreamVoiceAPI.get();
    if (api.getRadioService() == null)
      return Collections.emptyList();
    return api.getRadioService().getChannels().stream()
      .map(fr.dreamin.dreamvoice.api.radio.model.RadioChannel::getName)
      .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
      .toList();
  }

  @Suggestions("recording_players")
  public List<String> suggestPlayers(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    return NeoForgeCommandUtils.suggestPlayers(ctx.sender(), input);
  }

  @Command("voice record list")
  @Command("record|recording|voicerecording list")
  @CommandDescription("List active voice recordings")
  @Permission("dreamvoice.admin.recording.list")
  public void list(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var recordings = api.getRecordingService().getVoiceRecordings();
    if (recordings.isEmpty()) {
      NeoForgeCommandUtils.sendFailure(source, "record.list_empty");
      return;
    }

    NeoForgeCommandUtils.sendSuccess(source, "record.list_header", recordings.size());
    for (final var rec : recordings) {
      final var shortId = rec.getUuid().toString().substring(0, 8);
      final var authorName = resolveSpeakerName(source, rec.getSpeakerUUID());

      if (rec.isRecording())
        NeoForgeCommandUtils.sendSuccess(source, "record.list_item_live", shortId, authorName);
      else if (rec.isFinished()) {
        final var duration = String.format("%.1fs", rec.getDurationSeconds());
        NeoForgeCommandUtils.sendSuccess(source, "record.list_item_done", shortId, authorName, duration);
      } else
        NeoForgeCommandUtils.sendSuccess(source, "record.list_item_wait", shortId, authorName);
    }
  }

  @Command("voice record start")
  @Command("record|recording|voicerecording start")
  @CommandDescription("Start voice recording")
  @Permission("dreamvoice.admin.recording.start")
  public void start(final @NotNull CommandSourceStack source) {
    if (!source.isPlayer()) {
      NeoForgeCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = api.getRecordingService().startRecording(Objects.requireNonNull(source.getPlayer()).getUUID());
    if (rec != null)
      NeoForgeCommandUtils.sendSuccess(source, "record.started", rec.getUuid().toString().substring(0, 8));
  }

  @Command("voice record stop [id]")
  @Command("record|recording|voicerecording stop [id]")
  @CommandDescription("Stop voice recording")
  @Permission("dreamvoice.admin.recording.stop")
  public void stop(
    final @NotNull CommandSourceStack source,
    @Argument(value = "id", suggestions = "recordings") final @Nullable String id
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    if (id != null && !id.isBlank()) {
      final var rec = findRecording(api, id);
      if (rec == null) {
        NeoForgeCommandUtils.sendFailure(source, "record.not_found", id);
        return;
      }
      api.getRecordingService().stopRecording(rec.getUuid());
      NeoForgeCommandUtils.sendSuccess(source, "record.stopped", rec.getUuid().toString().substring(0, 8));
      return;
    }

    if (!source.isPlayer()) {
      NeoForgeCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    api.getRecordingService().stopRecording(Objects.requireNonNull(source.getPlayer()).getUUID());
    NeoForgeCommandUtils.sendSuccess(source, "record.stopped", source.getPlayer().getName().getString());
  }

  @Command("voice record play <id> [target]")
  @Command("record|recording|voicerecording play <id> [target]")
  @CommandDescription("Play a recording to a player")
  @Permission("dreamvoice.admin.recording.play")
  public void play(
    final @NotNull CommandSourceStack source,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id,
    @Argument(value = "target", suggestions = "recording_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = findRecording(api, id);
    if (rec == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    if (!rec.isFinished() && !rec.isRecording()) {
      NeoForgeCommandUtils.sendFailure(source, "record.not_finished");
      return;
    }

    final var targetPlayer = NeoForgeCommandUtils.resolvePlayer(source, targetName);
    if (targetPlayer == null) {
      NeoForgeCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    final var svcApi = api.getAPI();
    if (svcApi == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var conn = svcApi.getConnectionOf(targetPlayer.getUUID());
    if (conn == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.player_not_connected", targetPlayer.getName().getString());
      return;
    }

    api.getRecordingService().playRecordingTo(conn, rec);
    NeoForgeCommandUtils.sendSuccess(source, "record.playing", id, targetPlayer.getName().getString());
  }

  @Command("voice record delete <id>")
  @Command("record|recording|voicerecording delete <id>")
  @CommandDescription("Delete a recording")
  @Permission("dreamvoice.admin.recording.delete")
  public void delete(
    final @NotNull CommandSourceStack source,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = findRecording(api, id);
    if (rec == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    api.getRecordingService().unregister(rec.getUuid());
    NeoForgeCommandUtils.sendSuccess(source, "record.deleted", id);
  }

  @Command("voice record cassette <id> [target]")
  @Command("record|recording|voicerecording cassette <id> [target]")
  @CommandDescription("Give a physical cassette item of a recording to a player")
  @Permission("dreamvoice.admin.recording.cassette")
  public void giveCassette(
    final @NotNull CommandSourceStack source,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id,
    @Argument(value = "target", suggestions = "recording_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = findRecording(api, id);
    if (rec == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    final var targetPlayer = NeoForgeCommandUtils.resolvePlayer(source, targetName);
    if (targetPlayer == null) {
      NeoForgeCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    final var authorName = resolveSpeakerName(source, rec.getSpeakerUUID());
    final var item = NeoForgeCassetteItem.create(rec, authorName, targetPlayer);
    targetPlayer.getInventory().add(item);
    NeoForgeCommandUtils.sendSuccess(source, "record.cassette_given", rec.getUuid().toString().substring(0, 8), targetPlayer.getName().getString());
  }

  @Command("voice record cassette file <fileName> [target]")
  @Command("record|recording|voicerecording cassette file <fileName> [target]")
  @CommandDescription("Create and give a cassette linked to a local audio file")
  @Permission("dreamvoice.admin.recording.cassette")
  public void giveCassetteFile(
    final @NotNull CommandSourceStack source,
    @Argument("fileName") final @NotNull String fileName,
    @Argument(value = "target", suggestions = "recording_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var targetPlayer = NeoForgeCommandUtils.resolvePlayer(source, targetName);
    if (targetPlayer == null) {
      NeoForgeCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    api.getRecordingService().createRecordingFromFile(fileName)
      .thenAccept(rec -> {
        source.getServer().execute(() -> {
          final var item = NeoForgeCassetteItem.create(rec, fileName, targetPlayer);
          targetPlayer.getInventory().add(item);
          NeoForgeCommandUtils.sendSuccess(source, "record.cassette_given", rec.getUuid().toString().substring(0, 8), targetPlayer.getName().getString());
        });
      })
      .exceptionally(ex -> {
        NeoForgeCommandUtils.sendFailure(source, "record.failed", ex.getMessage() != null ? ex.getMessage() : "Unknown error");
        return null;
      });
  }

  @Command("voice record cassette url <url> [target]")
  @Command("record|recording|voicerecording cassette url <url> [target]")
  @CommandDescription("Create and give a cassette linked to a web audio URL")
  @Permission("dreamvoice.admin.recording.cassette")
  public void giveCassetteUrl(
    final @NotNull CommandSourceStack source,
    @Argument("url") final @NotNull String url,
    @Argument(value = "target", suggestions = "recording_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var targetPlayer = NeoForgeCommandUtils.resolvePlayer(source, targetName);
    if (targetPlayer == null) {
      NeoForgeCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    api.getRecordingService().createRecordingFromUrl(url, null)
      .thenAccept(rec -> {
        source.getServer().execute(() -> {
          final var item = NeoForgeCassetteItem.create(rec, url, targetPlayer);
          targetPlayer.getInventory().add(item);
          NeoForgeCommandUtils.sendSuccess(source, "record.cassette_given", rec.getUuid().toString().substring(0, 8), targetPlayer.getName().getString());
        });
      })
      .exceptionally(ex -> {
        NeoForgeCommandUtils.sendFailure(source, "record.failed", ex.getMessage() != null ? ex.getMessage() : "Unknown error");
        return null;
      });
  }

  @Command("voice record export <id> <format> [fileName]")
  @Command("record|recording|voicerecording export <id> <format> [fileName]")
  @CommandDescription("Export a recording to an audio file (mp3, ogg, wav)")
  @Permission("dreamvoice.admin.recording.export")
  public void export(
    final @NotNull CommandSourceStack source,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id,
    @Argument(value = "format", suggestions = "export_formats") final @NotNull String formatStr,
    @Argument(value = "fileName") final @Nullable String customFileName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = findRecording(api, id);
    if (rec == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    final fr.dreamin.dreamvoice.api.recording.model.AudioExportFormat format;
    try {
      format = fr.dreamin.dreamvoice.api.recording.model.AudioExportFormat.fromString(formatStr);
    } catch (IllegalArgumentException e) {
      NeoForgeCommandUtils.sendFailure(source, "record.invalid_format", formatStr);
      return;
    }

    NeoForgeCommandUtils.sendSuccess(source, "record.exporting", rec.getUuid().toString().substring(0, 8), format.name());
    api.getRecordingService().exportRecording(rec, format, null, customFileName).thenAccept(exportedFile -> {
      NeoForgeCommandUtils.sendSuccess(source, "record.exported", exportedFile.getName(), String.format("%.1fs", rec.getDurationSeconds()));
    }).exceptionally(err -> {
      NeoForgeCommandUtils.sendFailure(source, "record.failed", err.getMessage() != null ? err.getMessage() : "Unknown error");
      return null;
    });
  }

  @Command("voice record export-speaker <id> <speaker> <format> [fileName]")
  @Command("record|recording|voicerecording export-speaker <id> <speaker> <format> [fileName]")
  @CommandDescription("Export an isolated speaker track from a recording")
  @Permission("dreamvoice.admin.recording.export")
  public void exportSpeaker(
    final @NotNull CommandSourceStack source,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id,
    @Argument(value = "speaker", suggestions = "recording_players") final @NotNull String speakerName,
    @Argument(value = "format", suggestions = "export_formats") final @NotNull String formatStr,
    @Argument(value = "fileName") final @Nullable String customFileName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = findRecording(api, id);
    if (rec == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    final var speakerUuid = resolveSpeakerUuid(source, speakerName);
    if (speakerUuid == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.player_not_found", speakerName);
      return;
    }

    final fr.dreamin.dreamvoice.api.recording.model.AudioExportFormat format;
    try {
      format = fr.dreamin.dreamvoice.api.recording.model.AudioExportFormat.fromString(formatStr);
    } catch (IllegalArgumentException e) {
      NeoForgeCommandUtils.sendFailure(source, "record.invalid_format", formatStr);
      return;
    }

    NeoForgeCommandUtils.sendSuccess(source, "record.exporting", rec.getUuid().toString().substring(0, 8) + " (" + speakerName + ")", format.name());
    api.getRecordingService().exportRecording(rec, format, speakerUuid, customFileName).thenAccept(exportedFile -> {
      NeoForgeCommandUtils.sendSuccess(source, "record.exported", exportedFile.getName(), String.format("%.1fs", rec.getDurationSeconds()));
    }).exceptionally(err -> {
      NeoForgeCommandUtils.sendFailure(source, "record.failed", err.getMessage() != null ? err.getMessage() : "Unknown error");
      return null;
    });
  }

  @Command("voice record extract <id> <speaker> [target]")
  @Command("record|recording|voicerecording extract <id> <speaker> [target]")
  @CommandDescription("Extract a single speaker track into a new recording and optionally give cassette")
  @Permission("dreamvoice.admin.recording.extract")
  public void extract(
    final @NotNull CommandSourceStack source,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id,
    @Argument(value = "speaker", suggestions = "recording_players") final @NotNull String speakerName,
    @Argument(value = "target", suggestions = "recording_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = findRecording(api, id);
    if (rec == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    final var speakerUuid = resolveSpeakerUuid(source, speakerName);
    if (speakerUuid == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.player_not_found", speakerName);
      return;
    }

    final var extracted = api.getRecordingService().extractSpeakerRecording(rec.getUuid(), speakerUuid);
    if (extracted == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.failed", "No frames found for speaker " + speakerName);
      return;
    }

    NeoForgeCommandUtils.sendSuccess(source, "record.segment_sliced", extracted.getUuid().toString().substring(0, 8), String.format("%.1f", extracted.getDurationSeconds()));

    final var targetPlayer = NeoForgeCommandUtils.resolvePlayer(source, targetName);
    if (targetPlayer != null) {
      final var item = NeoForgeCassetteItem.create(extracted, speakerName, targetPlayer);
      targetPlayer.getInventory().add(item);
      NeoForgeCommandUtils.sendSuccess(source, "record.segment_cassette_given");
    }
  }

  @Command("voice record slice <id> <startMs> <durationMs> [target]")
  @Command("record|recording|voicerecording slice <id> <startMs> <durationMs> [target]")
  @CommandDescription("Slice a segment from an existing recording")
  @Permission("dreamvoice.admin.recording.slice")
  public void slice(
    final @NotNull CommandSourceStack source,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id,
    @Argument("startMs") final long startMs,
    @Argument("durationMs") final long durationMs,
    @Argument(value = "target", suggestions = "recording_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = findRecording(api, id);
    if (rec == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    final var sliced = api.getRecordingService().sliceRecording(rec.getUuid(), startMs, durationMs);
    if (sliced == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    NeoForgeCommandUtils.sendSuccess(source, "record.segment_sliced", sliced.getUuid().toString().substring(0, 8), String.format("%.1f", sliced.getDurationSeconds()));

    final var targetPlayer = NeoForgeCommandUtils.resolvePlayer(source, targetName);
    if (targetPlayer != null) {
      final var author = resolveSpeakerName(source, sliced.getSpeakerUUID());
      final var item = NeoForgeCassetteItem.create(sliced, author, targetPlayer);
      targetPlayer.getInventory().add(item);
      NeoForgeCommandUtils.sendSuccess(source, "record.segment_cassette_given");
    }
  }

  @Command("voice record slice-last <id> <durationMs> [target]")
  @Command("record|recording|voicerecording slice-last <id> <durationMs> [target]")
  @CommandDescription("Extract the last X milliseconds from an existing recording")
  @Permission("dreamvoice.admin.recording.slice")
  public void sliceLast(
    final @NotNull CommandSourceStack source,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id,
    @Argument("durationMs") final long durationMs,
    @Argument(value = "target", suggestions = "recording_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = findRecording(api, id);
    if (rec == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    final var sliced = api.getRecordingService().sliceLastRecording(rec.getUuid(), durationMs);
    if (sliced == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    NeoForgeCommandUtils.sendSuccess(source, "record.segment_sliced", sliced.getUuid().toString().substring(0, 8), String.format("%.1f", sliced.getDurationSeconds()));

    final var targetPlayer = NeoForgeCommandUtils.resolvePlayer(source, targetName);
    if (targetPlayer != null) {
      final var author = resolveSpeakerName(source, sliced.getSpeakerUUID());
      final var item = NeoForgeCassetteItem.create(sliced, author, targetPlayer);
      targetPlayer.getInventory().add(item);
      NeoForgeCommandUtils.sendSuccess(source, "record.segment_cassette_given");
    }
  }

  @Command("voice record radio start <channel>")
  @Command("record|recording|voicerecording radio start <channel>")
  @CommandDescription("Start recording transmissions on a radio channel")
  @Permission("dreamvoice.admin.recording.radio")
  public void radioStart(
    final @NotNull CommandSourceStack source,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRadioService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = api.getRadioService().startRecording(channelName);
    if (rec != null)
      NeoForgeCommandUtils.sendSuccess(source, "record.started", rec.getUuid().toString().substring(0, 8) + " (" + channelName + ")");
    else
      NeoForgeCommandUtils.sendFailure(source, "record.failed", "Could not start recording channel " + channelName);
  }

  @Command("voice record radio stop <channel> [target]")
  @Command("record|recording|voicerecording radio stop <channel> [target]")
  @CommandDescription("Stop recording transmissions on a radio channel and optionally give cassette")
  @Permission("dreamvoice.admin.recording.radio")
  public void radioStop(
    final @NotNull CommandSourceStack source,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName,
    @Argument(value = "target", suggestions = "recording_players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRadioService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = api.getRadioService().stopRecording(channelName);
    if (rec == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.none_active");
      return;
    }

    NeoForgeCommandUtils.sendSuccess(source, "record.stopped", rec.getUuid().toString().substring(0, 8) + " (" + channelName + ")");

    final var targetPlayer = NeoForgeCommandUtils.resolvePlayer(source, targetName);
    if (targetPlayer != null) {
      final var item = NeoForgeCassetteItem.create(rec, "Radio " + channelName, targetPlayer);
      targetPlayer.getInventory().add(item);
      NeoForgeCommandUtils.sendSuccess(source, "record.cassette_given", rec.getUuid().toString().substring(0, 8), targetPlayer.getName().getString());
    }
  }

  @Command("voice record radio play <channel> <id>")
  @Command("record|recording|voicerecording radio play <channel> <id>")
  @CommandDescription("Broadcast a recording across a radio channel")
  @Permission("dreamvoice.admin.recording.radio")
  public void radioPlay(
    final @NotNull CommandSourceStack source,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRadioService() == null || api.getRecordingService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = findRecording(api, id);
    if (rec == null) {
      NeoForgeCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    if (!rec.isFinished() && !rec.isRecording()) {
      NeoForgeCommandUtils.sendFailure(source, "record.not_finished");
      return;
    }

    api.getRadioService().playRecordingToRadio(channelName, rec);
    NeoForgeCommandUtils.sendSuccess(source, "record.playing", id, channelName);
  }

  @Command("voice record radio stop-play <channel>")
  @Command("record|recording|voicerecording radio stop-play <channel>")
  @CommandDescription("Stop broadcasting on a radio channel")
  @Permission("dreamvoice.admin.recording.radio")
  public void radioStopPlay(
    final @NotNull CommandSourceStack source,
    @Argument(value = "channel", suggestions = "radio_channels") final @NotNull String channelName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRadioService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    api.getRadioService().stopRadioPlayback(channelName);
    NeoForgeCommandUtils.sendSuccess(source, "record.stopped", channelName);
  }

  private static UUID resolveSpeakerUuid(final @NotNull CommandSourceStack source, final @NotNull String input) {
    try {
      return UUID.fromString(input);
    } catch (final IllegalArgumentException ignored) {}

    final var server = source.getServer();
    final var online = server.getPlayerList().getPlayerByName(input);
    if (online != null)
      return online.getUUID();

    try {
      final var cached = server.services().profileResolver().fetchByName(input);
      if (cached.isPresent())
        return cached.get().id();
    } catch (final Exception ignored) {}

    return null;
  }

  private static String resolveSpeakerName(final @NotNull CommandSourceStack source, final @Nullable UUID uuid) {
    if (uuid == null)
      return "Unknown";
    final var server = source.getServer();
    final var online = server.getPlayerList().getPlayer(uuid);
    if (online != null)
      return online.getName().getString();
    try {
      final var cached = server.services().profileResolver().fetchById(uuid);
      if (cached.isPresent())
        return cached.get().name();
    } catch (final Exception ignored) {}
    return uuid.toString().substring(0, 8);
  }

  private static VoiceRecording findRecording(final @NotNull DreamVoiceAPI api, final @NotNull String id) {
    try {
      final var uuid = UUID.fromString(id);
      final var byUuid = Objects.requireNonNull(api.getRecordingService()).getVoiceRecording(uuid);
      if (byUuid != null)
        return byUuid;
    } catch (final IllegalArgumentException ignored) {}

    return Objects.requireNonNull(api.getRecordingService()).getVoiceRecordings().stream()
      .filter(r -> r.getUuid().toString().toLowerCase().startsWith(id.toLowerCase()))
      .findFirst()
      .orElse(null);
  }
}

