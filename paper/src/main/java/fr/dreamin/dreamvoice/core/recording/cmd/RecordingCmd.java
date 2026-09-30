package fr.dreamin.dreamvoice.core.recording.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.specifier.Greedy;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.recording.model.AudioExportFormat;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.recording.item.CassetteItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class RecordingCmd {

  private final @Nullable VoiceRecordingService recordingService = DreamVoice.getService(VoiceRecordingService.class);

  private @Nullable VoiceRecordingService requireRecordingService(final @NotNull CommandSender sender) {
    if (this.recordingService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return null;
    }
    return this.recordingService;
  }

  // ###############################################################
  // --------------------- SUGGESTION METHODE ----------------------
  // ###############################################################

  @Suggestions("recordings")
  public @NotNull List<String> suggestRecordings(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String input) {
    if (this.recordingService == null)
      return List.of();

    return this.recordingService.getVoiceRecordings().stream()
      .map(rec -> rec.getUuid().toString().substring(0, 8))
      .filter(id -> id.startsWith(input.toLowerCase()))
      .sorted()
      .collect(Collectors.toList());
  }

  @Suggestions("export_formats")
  public @NotNull List<String> suggestExportFormats(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String input) {
    return Stream.of("mp3", "ogg", "wav")
      .filter(f -> f.startsWith(input.toLowerCase()))
      .toList();
  }

  // ###############################################################
  // ----------------------- RECORD COMMANDS -----------------------
  // ###############################################################

  @CommandMethod("record start")
  @CommandPermission("dreamvoice.record.start")
  @CommandDescription("Start voice recording")
  private void startRecording(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    final var rec = recordingService.startRecording(player.getUniqueId());
    sender.sendMessage(Component.translatable("record.started", Component.text(rec.getUuid().toString().substring(0, 8))));
  }

  @CommandMethod("record stop [id]")
  @CommandPermission("dreamvoice.record.stop")
  @CommandDescription("Stop recording (current or by ID)")
  private void stopRecording(
    final @NotNull CommandSender sender,
    @Argument("id") final @Nullable String id
  ) {
    if (id != null) {
      stopById(sender, id);
      return;
    }
    if (sender instanceof Player player)
      stopCurrent(sender, player);
    else
      sender.sendMessage(Component.translatable("common.player_only"));
  }

  private void stopById(final @NotNull CommandSender sender, final @NotNull String id) {
    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    try {
      final var uuid = parseRecordingId(id);
      recordingService.stopRecording(uuid);
      sender.sendMessage(Component.translatable("record.stopped", Component.text(id)));
    } catch (IllegalArgumentException e) {
      sender.sendMessage(Component.translatable("record.not_found", Component.text(id)));
    }
  }

  private void stopCurrent(final @NotNull CommandSender sender, final @NotNull Player player) {
    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    recordingService.getVoiceRecordings().stream()
      .filter(rec -> rec.getSpeakerUUID().equals(player.getUniqueId()))
      .filter(VoiceRecording::isRecording)
      .findFirst()
      .ifPresentOrElse(
        rec -> {
          recordingService.stopRecording(rec.getUuid());
          sender.sendMessage(Component.translatable("record.stopped", Component.text(rec.getUuid().toString().substring(0, 8))));
        },
        () -> sender.sendMessage(Component.translatable("record.none_active"))
      );
  }

  @CommandMethod("record list")
  @CommandPermission("dreamvoice.record.list")
  @CommandDescription("List recordings")
  private void listRecordings(final @NotNull CommandSender sender) {
    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    final var recordings = recordingService.getVoiceRecordings();

    if (recordings.isEmpty()) {
      sender.sendMessage(Component.translatable("record.list_empty"));
      return;
    }

    sender.sendMessage(Component.translatable("record.list_header", Component.text(recordings.size())));

    for (final var rec : recordings) {
      final var shortId = rec.getUuid().toString().substring(0, 8);
      final var speaker = Bukkit.getOfflinePlayer(rec.getSpeakerUUID());
      final var authorName = speaker.getName() != null ? speaker.getName() : rec.getSpeakerUUID().toString().substring(0, 8);

      if (rec.isRecording())
        sender.sendMessage(Component.translatable("record.list_item_live", Component.text(shortId), Component.text(authorName)));
      else if (rec.isFinished()) {
        final var duration = String.format("%.1fs", rec.getDurationSeconds());
        sender.sendMessage(Component.translatable("record.list_item_done", Component.text(shortId), Component.text(authorName), Component.text(duration)));
      } else
        sender.sendMessage(Component.translatable("record.list_item_wait", Component.text(shortId), Component.text(authorName)));
    }
  }

  @CommandMethod("record play <id> [player]")
  @CommandPermission("dreamvoice.record.play")
  @CommandDescription("Play recording")
  private void playRecording(
    final @NotNull CommandSender sender,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id,
    @Argument("player") final @Nullable Player target
  ) {
    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    try {
      final var uuid = parseRecordingId(id);
      final var rec = recordingService.getVoiceRecordings().stream()
        .filter(r -> r.getUuid().equals(uuid))
        .findFirst()
        .orElse(null);

      if (rec == null) {
        sender.sendMessage(Component.translatable("record.not_found", Component.text(id)));
        return;
      }

      if (!rec.isFinished()) {
        sender.sendMessage(Component.translatable("record.not_finished"));
        return;
      }

      final var player = resolvePlayer(sender, target);
      if (player == null)
        return;

      final var conn = recordingService.getAPI().getConnectionOf(player.getUniqueId());

      if (conn == null) {
        sender.sendMessage(Component.translatable("record.player_not_connected", Component.text(player.getName())));
        return;
      }

      recordingService.playRecordingTo(conn, rec);

      sender.sendMessage(Component.translatable("record.playing", Component.text(id), Component.text(player.getName())));
    } catch (Exception e) {
      sender.sendMessage(Component.translatable("record.failed", Component.text(e.getMessage() != null ? e.getMessage() : "Unknown error")));
    }
  }

  @CommandMethod("record cassette <id> [player]")
  @CommandPermission("dreamvoice.record.cassette")
  @CommandDescription("Give a physical cassette item of a recording to a player")
  private void giveCassette(
    final @NotNull CommandSender sender,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id,
    @Argument("player") final @Nullable Player targetArg
  ) {
    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    final var target = resolvePlayer(sender, targetArg);
    if (target == null)
      return;

    try {
      final var uuid = parseRecordingId(id);
      final var rec = recordingService.getVoiceRecordings().stream()
        .filter(r -> r.getUuid().equals(uuid))
        .findFirst()
        .orElse(null);

      if (rec == null) {
        sender.sendMessage(Component.translatable("record.not_found", Component.text(id)));
        return;
      }

      final var item = CassetteItem.create(rec, target);
      target.getInventory().addItem(item);

      sender.sendMessage(Component.translatable("record.cassette_given", Component.text(rec.getUuid().toString().substring(0, 8)), Component.text(target.getName())));
    } catch (Exception e) {
      sender.sendMessage(Component.translatable("record.not_found", Component.text(id)));
    }
  }

  @CommandMethod("record cassette file <fileName> [player]")
  @CommandPermission("dreamvoice.record.cassette")
  @CommandDescription("Create and give a cassette linked to a local audio file")
  private void giveCassetteFile(
    final @NotNull CommandSender sender,
    @Argument("fileName") final @NotNull String fileName,
    @Argument("player") final @Nullable Player targetArg
  ) {
    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    final var target = resolvePlayer(sender, targetArg);
    if (target == null)
      return;

    sender.sendMessage(Component.translatable("record.converting_file", Component.text(fileName)));
    recordingService.createRecordingFromFile(fileName)
      .thenAccept(rec -> Bukkit.getScheduler().runTask(DreamVoice.getInstance(), () -> {
        final var item = CassetteItem.create(rec, fileName, target);
        target.getInventory().addItem(item);
        sender.sendMessage(Component.translatable("record.cassette_given", Component.text(fileName), Component.text(target.getName())));
      }))
      .exceptionally(ex -> {
        sender.sendMessage(Component.translatable("record.failed", Component.text(ex.getMessage() != null ? ex.getMessage() : "Unknown error")));
        return null;
      });
  }

  @CommandMethod("record cassette url <url> [player]")
  @CommandPermission("dreamvoice.record.cassette")
  @CommandDescription("Create and give a cassette linked to a web audio URL")
  private void giveCassetteUrl(
    final @NotNull CommandSender sender,
    @Argument("url") final @NotNull String url,
    @Argument("player") final @Nullable Player targetArg
  ) {
    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    final var target = resolvePlayer(sender, targetArg);
    if (target == null)
      return;

    sender.sendMessage(Component.translatable("record.converting_url"));
    recordingService.createRecordingFromUrl(url, null)
      .thenAccept(rec -> Bukkit.getScheduler().runTask(DreamVoice.getInstance(), () -> {
        final var item = CassetteItem.create(rec, url, target);
        target.getInventory().addItem(item);
        sender.sendMessage(Component.translatable("record.cassette_given", Component.text(url), Component.text(target.getName())));
      }))
      .exceptionally(ex -> {
        sender.sendMessage(Component.translatable("record.failed", Component.text(ex.getMessage() != null ? ex.getMessage() : "Unknown error")));
        return null;
      });
  }


  @CommandMethod("record slice <id> <startMs> <durationMs> [give]")
  @CommandPermission("dreamvoice.record.slice")
  @CommandDescription("Slice a segment from an existing recording")
  private void sliceRecord(
    final @NotNull CommandSender sender,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id,
    @Argument("startMs") final long startMs,
    @Argument("durationMs") final long durationMs,
    @Argument("give") final @Nullable Boolean give
  ) {
    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    try {
      final var uuid = parseRecordingId(id);
      final var sliced = recordingService.sliceRecording(uuid, startMs, durationMs);
      if (sliced == null) {
        sender.sendMessage(Component.translatable("record.not_found", Component.text(id)));
        return;
      }

      sender.sendMessage(Component.translatable("record.segment_sliced",
        Component.text(sliced.getUuid().toString().substring(0, 8)),
        Component.text(String.format("%.1f", sliced.getDurationSeconds()))
      ));

      if (give != null && give && sender instanceof Player player) {
        final var item = CassetteItem.create(sliced, player);
        player.getInventory().addItem(item);
        sender.sendMessage(Component.translatable("record.segment_cassette_given"));
      }
    } catch (Exception e) {
      sender.sendMessage(Component.translatable("record.failed", Component.text(e.getMessage() != null ? e.getMessage() : "Unknown error")));
    }
  }

  @CommandMethod("record slice-last <id> <durationMs> [give]")
  @CommandPermission("dreamvoice.record.slice")
  @CommandDescription("Extract the last X milliseconds from an existing recording")
  private void sliceLastRecord(
    final @NotNull CommandSender sender,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id,
    @Argument("durationMs") final long durationMs,
    @Argument("give") final @Nullable Boolean give
  ) {
    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    try {
      final var uuid = parseRecordingId(id);
      final var sliced = recordingService.sliceLastRecording(uuid, durationMs);
      if (sliced == null) {
        sender.sendMessage(Component.translatable("record.not_found", Component.text(id)));
        return;
      }

      sender.sendMessage(Component.translatable("record.segment_sliced",
        Component.text(sliced.getUuid().toString().substring(0, 8)),
        Component.text(String.format("%.1f", sliced.getDurationSeconds()))
      ));

      if (give != null && give && sender instanceof Player player) {
        final var item = CassetteItem.create(sliced, player);
        player.getInventory().addItem(item);
        sender.sendMessage(Component.translatable("record.segment_cassette_given"));
      }
    } catch (Exception e) {
      sender.sendMessage(Component.translatable("record.failed", Component.text(e.getMessage() != null ? e.getMessage() : "Unknown error")));
    }
  }

  @CommandMethod("record delete <id>")
  @CommandPermission("dreamvoice.record.delete")
  @CommandDescription("Delete recording")
  private void deleteRecording(
    final @NotNull CommandSender sender,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id
  ) {
    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    try {
      final var uuid = parseRecordingId(id);
      recordingService.unregister(uuid);
      sender.sendMessage(Component.translatable("record.deleted", Component.text(id)));
    } catch (IllegalArgumentException e) {
      sender.sendMessage(Component.translatable("record.not_found", Component.text(id)));
    }
  }

  @CommandMethod("record export <id> <format> [fileName]")
  @CommandPermission("dreamvoice.record.export")
  @CommandDescription("Export a recording to an audio file (mp3, ogg, wav) with optional custom name")
  private void exportRecording(
    final @NotNull CommandSender sender,
    @Argument(value = "id", suggestions = "recordings") final @NotNull String id,
    @Argument(value = "format", suggestions = "export_formats") final @NotNull String formatStr,
    @Argument("fileName") @Greedy final @Nullable String customFileName
  ) {
    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    try {
      final var uuid = parseRecordingId(id);
      final var rec = recordingService.getVoiceRecording(uuid);
      if (rec == null) {
        sender.sendMessage(Component.translatable("record.not_found", Component.text(id)));
        return;
      }

      final AudioExportFormat format;
      try {
        format = AudioExportFormat.fromString(formatStr);
      } catch (IllegalArgumentException e) {
        sender.sendMessage(Component.translatable("record.invalid_format", Component.text(formatStr)));
        return;
      }

      sender.sendMessage(Component.translatable("record.exporting", Component.text(id), Component.text(format.name())));

      recordingService.exportRecording(rec, format, customFileName).thenAccept(exportedFile -> {
        sender.sendMessage(Component.translatable("record.exported",
          Component.text(exportedFile.getName()),
          Component.text(String.format("%.1fs", rec.getDurationSeconds()))
        ));
      }).exceptionally(err -> {
        sender.sendMessage(Component.translatable("record.failed", Component.text(err.getMessage() != null ? err.getMessage() : "Unknown error")));
        return null;
      });

    } catch (Exception e) {
      sender.sendMessage(Component.translatable("record.failed", Component.text(e.getMessage() != null ? e.getMessage() : "Unknown error")));
    }
  }



  // ------------------------------------------------------------
  // Utils
  // ------------------------------------------------------------

  private @NotNull UUID parseRecordingId(final @NotNull String id) {
    final var recordingService = this.recordingService;
    if (recordingService == null)
      throw new IllegalStateException("VoiceRecordingService is unavailable");

    if (id.length() == 8) {
      return recordingService.getVoiceRecordings().stream()
        .map(VoiceRecording::getUuid)
        .filter(uuid -> uuid.toString().startsWith(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Recording not found"));
    }
    return UUID.fromString(id);
  }

  private @Nullable Player resolvePlayer(final @NotNull CommandSender sender, final @Nullable Player targetArg) {
    if (targetArg != null)
      return targetArg;
    if (sender instanceof Player p)
      return p;
    sender.sendMessage(Component.translatable("common.specify_player"));
    return null;
  }
}
