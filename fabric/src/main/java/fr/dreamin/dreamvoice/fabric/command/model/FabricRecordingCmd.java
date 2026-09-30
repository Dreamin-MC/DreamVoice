package fr.dreamin.dreamvoice.fabric.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.fabric.command.FabricCommandUtils;
import fr.dreamin.dreamvoice.fabric.command.annotation.DreamCmd;
import fr.dreamin.dreamvoice.fabric.item.FabricCassetteItem;
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
public final class FabricRecordingCmd {

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

  @Suggestions("recording_players")
  public List<String> suggestPlayers(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    return FabricCommandUtils.suggestPlayers(ctx.sender(), input);
  }

  @Command("record|recording|voicerecording list")
  @CommandDescription("List active voice recordings")
  @Permission("dreamvoice.admin.recording.list")
  public void list(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var recordings = api.getRecordingService().getVoiceRecordings();
    if (recordings.isEmpty()) {
      FabricCommandUtils.sendFailure(source, "record.list_empty");
      return;
    }

    FabricCommandUtils.sendSuccess(source, "record.list_header", recordings.size());
    for (final var rec : recordings) {
      final var shortId = rec.getUuid().toString().substring(0, 8);
      final var authorName = resolveSpeakerName(source, rec.getSpeakerUUID());

      if (rec.isRecording())
        FabricCommandUtils.sendSuccess(source, "record.list_item_live", shortId, authorName);
      else if (rec.isFinished()) {
        final var duration = String.format("%.1fs", rec.getDurationSeconds());
        FabricCommandUtils.sendSuccess(source, "record.list_item_done", shortId, authorName, duration);
      } else
        FabricCommandUtils.sendSuccess(source, "record.list_item_wait", shortId, authorName);
    }
  }

  @Command("record|recording|voicerecording start")
  @CommandDescription("Start voice recording")
  @Permission("dreamvoice.admin.recording.start")
  public void start(final @NotNull CommandSourceStack source) {
    if (!source.isPlayer()) {
      FabricCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = api.getRecordingService().startRecording(Objects.requireNonNull(source.getPlayer()).getUUID());
    if (rec != null)
      FabricCommandUtils.sendSuccess(source, "record.started", rec.getUuid().toString().substring(0, 8));
  }

  @Command("record|recording|voicerecording stop [id]")
  @CommandDescription("Stop voice recording")
  @Permission("dreamvoice.admin.recording.stop")
  public void stop(
    final @NotNull CommandSourceStack source,
    @Argument(value = "id", suggestions = "recordings") final @Nullable String id
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    if (id != null && !id.isBlank()) {
      final var rec = findRecording(api, id);
      if (rec == null) {
        FabricCommandUtils.sendFailure(source, "record.not_found", id);
        return;
      }
      api.getRecordingService().stopRecording(rec.getUuid());
      FabricCommandUtils.sendSuccess(source, "record.stopped", rec.getUuid().toString().substring(0, 8));
      return;
    }

    if (!source.isPlayer()) {
      FabricCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    api.getRecordingService().stopRecording(Objects.requireNonNull(source.getPlayer()).getUUID());
    FabricCommandUtils.sendSuccess(source, "record.stopped", source.getPlayer().getName().getString());
  }

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
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = findRecording(api, id);
    if (rec == null) {
      FabricCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    if (!rec.isFinished() && !rec.isRecording()) {
      FabricCommandUtils.sendFailure(source, "record.not_finished");
      return;
    }

    final var targetPlayer = FabricCommandUtils.resolvePlayer(source, targetName);
    if (targetPlayer == null) {
      FabricCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    final var svcApi = api.getAPI();
    if (svcApi == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var conn = svcApi.getConnectionOf(targetPlayer.getUUID());
    if (conn == null) {
      FabricCommandUtils.sendFailure(source, "record.player_not_connected", targetPlayer.getName().getString());
      return;
    }

    api.getRecordingService().playRecordingTo(conn, rec);
    FabricCommandUtils.sendSuccess(source, "record.playing", id, targetPlayer.getName().getString());
  }

  @Command("record|recording|voicerecording delete <id>")
  @CommandDescription("Delete a recording")
  @Permission("dreamvoice.admin.recording.delete")
  public void delete(
    final @NotNull CommandSourceStack source,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getRecordingService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = findRecording(api, id);
    if (rec == null) {
      FabricCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    api.getRecordingService().unregister(rec.getUuid());
    FabricCommandUtils.sendSuccess(source, "record.deleted", id);
  }

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
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var rec = findRecording(api, id);
    if (rec == null) {
      FabricCommandUtils.sendFailure(source, "record.not_found", id);
      return;
    }

    final var targetPlayer = FabricCommandUtils.resolvePlayer(source, targetName);
    if (targetPlayer == null) {
      FabricCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    final var authorName = resolveSpeakerName(source, rec.getSpeakerUUID());
    final var item = FabricCassetteItem.create(rec, authorName, targetPlayer);
    targetPlayer.getInventory().add(item);
    FabricCommandUtils.sendSuccess(source, "record.cassette_given", rec.getUuid().toString().substring(0, 8), targetPlayer.getName().getString());
  }

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
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var targetPlayer = FabricCommandUtils.resolvePlayer(source, targetName);
    if (targetPlayer == null) {
      FabricCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    api.getRecordingService().createRecordingFromFile(fileName)
      .thenAccept(rec -> {
        source.getServer().execute(() -> {
          final var item = FabricCassetteItem.create(rec, fileName, targetPlayer);
          targetPlayer.getInventory().add(item);
          FabricCommandUtils.sendSuccess(source, "record.cassette_given", rec.getUuid().toString().substring(0, 8), targetPlayer.getName().getString());
        });
      })
      .exceptionally(ex -> {
        FabricCommandUtils.sendFailure(source, "record.failed", ex.getMessage() != null ? ex.getMessage() : "Unknown error");
        return null;
      });
  }

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
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var targetPlayer = FabricCommandUtils.resolvePlayer(source, targetName);
    if (targetPlayer == null) {
      FabricCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    api.getRecordingService().createRecordingFromUrl(url, null)
      .thenAccept(rec -> {
        source.getServer().execute(() -> {
          final var item = FabricCassetteItem.create(rec, url, targetPlayer);
          targetPlayer.getInventory().add(item);
          FabricCommandUtils.sendSuccess(source, "record.cassette_given", rec.getUuid().toString().substring(0, 8), targetPlayer.getName().getString());
        });
      })
      .exceptionally(ex -> {
        FabricCommandUtils.sendFailure(source, "record.failed", ex.getMessage() != null ? ex.getMessage() : "Unknown error");
        return null;
      });
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
