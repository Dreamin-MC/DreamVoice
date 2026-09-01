package fr.dreamin.dreamvoice.core.recording.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class RecordingCmd {

  private final @Nullable VoiceRecordingService recordingService = DreamVoice.getService(VoiceRecordingService.class);

  private @Nullable VoiceRecordingService requireRecordingService(final @NotNull CommandSender sender) {
    if (this.recordingService == null) {
      sender.sendMessage(Component.text("[SVC] Voice recording service unavailable.", NamedTextColor.RED));
      return null;
    }
    return this.recordingService;
  }

  // ------------------------------------------------------------
  // Suggestions
  // ------------------------------------------------------------

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

  // ###############################################################
  // ----------------------- RECORD COMMANDS -----------------------
  // ###############################################################

  @CommandMethod("record start")
  @CommandPermission("dreamvoice.record.start")
  @CommandDescription("Start voice recording")
  private void startRecording(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    final var rec = recordingService.startRecording(player.getUniqueId());

    sender.sendMessage(
      Component.text("Recording started: ", NamedTextColor.GREEN)
        .append(Component.text(rec.getUuid().toString().substring(0, 8), NamedTextColor.YELLOW))
    );
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
      sender.sendMessage(Component.text("Player only or provide ID!", NamedTextColor.RED));
  }

  private void stopById(final @NotNull CommandSender sender, final @NotNull String id) {
    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    try {
      final var uuid = parseRecordingId(id);
      recordingService.stopRecording(uuid);
      sender.sendMessage(
        Component.text("Stopped: ", NamedTextColor.GREEN)
          .append(Component.text(id, NamedTextColor.YELLOW))
      );
    } catch (IllegalArgumentException e) {
      sender.sendMessage(Component.text("Invalid ID!", NamedTextColor.RED));
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
          sender.sendMessage(
            Component.text("Stopped current: ", NamedTextColor.GREEN)
              .append(Component.text(rec.getUuid().toString().substring(0, 8), NamedTextColor.YELLOW))
              .append(Component.text(" (", NamedTextColor.GRAY))
              .append(Component.text(String.format("%.1fs", rec.getDurationSeconds()), NamedTextColor.AQUA))
              .append(Component.text(")", NamedTextColor.GRAY))
          );
        },
        () -> sender.sendMessage(Component.text("No active recording!", NamedTextColor.RED))
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
      sender.sendMessage(Component.text("No recordings found.", NamedTextColor.GRAY));
      return;
    }

    sender.sendMessage(
      Component.text("Recordings (", NamedTextColor.GRAY)
        .append(Component.text(recordings.size(), NamedTextColor.YELLOW))
        .append(Component.text("):", NamedTextColor.GRAY))
    );

    for (final var rec : recordings) {
      sender.sendMessage(
        getStatusComponent(rec)
          .append(Component.text(rec.getUuid().toString().substring(0, 8), NamedTextColor.YELLOW))
          .append(Component.text(" (", NamedTextColor.GRAY))
          .append(Component.text(rec.getSpeakerUUID().toString().substring(0, 8), NamedTextColor.GRAY))
          .append(Component.text(")", NamedTextColor.GRAY))
      );
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
    if (!(sender instanceof Player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    final var recordingService = requireRecordingService(sender);
    if (recordingService == null)
      return;

    try {
      final var uuid = parseRecordingId(id);
      final var rec = recordingService.getVoiceRecordings().stream()
        .filter(r -> r.getUuid().equals(uuid))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Recording not found"));

      if (!rec.isFinished()) {
        sender.sendMessage(Component.text("Recording not finished!", NamedTextColor.RED));
        return;
      }

      final var player = target != null ? target : (Player) sender;
      final var conn = recordingService.getAPI().getConnectionOf(player.getUniqueId());

      if (conn == null) {
        sender.sendMessage(Component.text("Player not in voice chat!", NamedTextColor.RED));
        return;
      }

      recordingService.playRecordingTo(conn, rec);

      sender.sendMessage(
        Component.text("Playing: ", NamedTextColor.GREEN)
          .append(Component.text(id, NamedTextColor.YELLOW))
          .append(Component.text(" -> ", NamedTextColor.GRAY))
          .append(Component.text(player.getName(), NamedTextColor.AQUA))
          .append(Component.text(" (", NamedTextColor.GRAY))
          .append(Component.text(String.format("%.1fs", rec.getDurationSeconds()), NamedTextColor.GRAY))
          .append(Component.text(")", NamedTextColor.GRAY))
      );
    } catch (Exception e) {
      sender.sendMessage(
        Component.text("Error: ", NamedTextColor.RED)
          .append(Component.text(e.getMessage() != null ? e.getMessage() : "Unknown error", NamedTextColor.GRAY))
      );
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
        .orElseThrow(() -> new IllegalArgumentException("Recording not found"));

      final var item = recordingService.createCassette(rec);
      target.getInventory().addItem(item);

      sender.sendMessage(
        Component.text("Voice cassette given to ", NamedTextColor.GREEN)
          .append(Component.text(target.getName(), NamedTextColor.YELLOW))
          .append(Component.text("!", NamedTextColor.GREEN))
      );
    } catch (Exception e) {
      sender.sendMessage(Component.text("Recording not found: " + id, NamedTextColor.RED));
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

    sender.sendMessage(Component.text("Converting audio file...", NamedTextColor.GRAY));
    recordingService.createRecordingFromFile(fileName)
      .thenAccept(rec -> Bukkit.getScheduler().runTask(DreamVoice.getInstance(), () -> {
        final var item = recordingService.createCassette(rec);
        target.getInventory().addItem(item);
        sender.sendMessage(
          Component.text("Cassette of audio file '", NamedTextColor.GREEN)
            .append(Component.text(fileName, NamedTextColor.YELLOW))
            .append(Component.text("' given to ", NamedTextColor.GREEN))
            .append(Component.text(target.getName(), NamedTextColor.AQUA))
            .append(Component.text("!", NamedTextColor.GREEN))
        );
      }))
      .exceptionally(ex -> {
        sender.sendMessage(Component.text("Error loading audio file: " + ex.getMessage(), NamedTextColor.RED));
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

    sender.sendMessage(Component.text("Downloading and converting audio from URL...", NamedTextColor.GRAY));
    recordingService.createRecordingFromUrl(url, null)
      .thenAccept(rec -> Bukkit.getScheduler().runTask(DreamVoice.getInstance(), () -> {
        final var item = recordingService.createCassette(rec);
        target.getInventory().addItem(item);
        sender.sendMessage(
          Component.text("Cassette of audio URL given to ", NamedTextColor.GREEN)
            .append(Component.text(target.getName(), NamedTextColor.AQUA))
            .append(Component.text("!", NamedTextColor.GREEN))
        );
      }))
      .exceptionally(ex -> {
        sender.sendMessage(Component.text("Error downloading URL: " + ex.getMessage(), NamedTextColor.RED));
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
        sender.sendMessage(Component.text("Recording not found!", NamedTextColor.RED));
        return;
      }

      sender.sendMessage(
        Component.text("Segment sliced successfully: ", NamedTextColor.GREEN)
          .append(Component.text(sliced.getUuid().toString().substring(0, 8), NamedTextColor.YELLOW))
          .append(Component.text(" (" + String.format("%.1fs", sliced.getDurationSeconds()) + ")", NamedTextColor.AQUA))
      );

      if (give != null && give && sender instanceof Player player) {
        final var item = recordingService.createCassette(sliced);
        player.getInventory().addItem(item);
        sender.sendMessage(Component.text("Segment cassette added to your inventory!", NamedTextColor.GREEN));
      }
    } catch (Exception e) {
      sender.sendMessage(Component.text("Error while slicing recording: " + e.getMessage(), NamedTextColor.RED));
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
        sender.sendMessage(Component.text("Recording not found!", NamedTextColor.RED));
        return;
      }

      sender.sendMessage(
        Component.text("Last " + (durationMs / 1000.0) + "s extracted successfully: ", NamedTextColor.GREEN)
          .append(Component.text(sliced.getUuid().toString().substring(0, 8), NamedTextColor.YELLOW))
          .append(Component.text(" (" + String.format("%.1fs", sliced.getDurationSeconds()) + ")", NamedTextColor.AQUA))
      );

      if (give != null && give && sender instanceof Player player) {
        final var item = recordingService.createCassette(sliced);
        player.getInventory().addItem(item);
        sender.sendMessage(Component.text("Segment cassette added to your inventory!", NamedTextColor.GREEN));
      }
    } catch (Exception e) {
      sender.sendMessage(Component.text("Error while slicing recording: " + e.getMessage(), NamedTextColor.RED));
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
      sender.sendMessage(
        Component.text("Deleted: ", NamedTextColor.GREEN)
          .append(Component.text(id, NamedTextColor.YELLOW))
      );
    } catch (IllegalArgumentException e) {
      sender.sendMessage(Component.text("Recording not found!", NamedTextColor.RED));
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

  private @NotNull Component getStatusComponent(final @NotNull VoiceRecording rec) {
    if (rec.isRecording())
      return Component.text("[LIVE] ", NamedTextColor.RED);
    else if (rec.isFinished())
      return Component.text("[DONE] ", NamedTextColor.GREEN)
        .append(Component.text(String.format("%.1fs ", rec.getDurationSeconds()), NamedTextColor.AQUA));
    return Component.text("[WAIT] ", NamedTextColor.GRAY);
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
