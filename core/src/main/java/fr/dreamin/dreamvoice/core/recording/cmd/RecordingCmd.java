package fr.dreamin.dreamvoice.core.recording.cmd;

import cloud.commandframework.annotations.*;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreaminvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreaminvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class RecordingCmd {

  private final @NotNull VoiceRecordingService recordingService = DreamVoice.getService(VoiceRecordingService.class);

  // ------------------------------------------------------------
  // Suggestions
  // ------------------------------------------------------------

  @Suggestions("recordings")
  public @NotNull List<String> suggestRecordings(CommandContext<CommandSender> ctx, String input) {
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
  private void startRecording(
    CommandSender sender
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    final var rec = this.recordingService.startRecording(player.getUniqueId());
    rec.start();

    sender.sendMessage(
      Component.text("Recording started: ", NamedTextColor.GREEN)
        .append(Component.text(rec.getUuid().toString().substring(0, 8), NamedTextColor.YELLOW))
    );
  }

  @CommandMethod("record stop [id]")
  @CommandPermission("dreamvoice.record.stop")
  @CommandDescription("Stop recording (current or by ID)")
  private void stopRecording(
    CommandSender sender,
    @Argument("id") @Nullable String id
  ) {
    if (id != null) {
      stopById(sender, id);
      return;
    }
    if (sender instanceof Player player) {
      stopCurrent(sender, player);
    } else {
      sender.sendMessage(Component.text("Player only or provide ID!", NamedTextColor.RED));
    }
  }

  private void stopById(CommandSender sender, String id) {
    try {
      final var uuid = parseRecordingId(id);
      this.recordingService.stopRecording(uuid);
      sender.sendMessage(
        Component.text("Stopped: ", NamedTextColor.GREEN)
          .append(Component.text(id, NamedTextColor.YELLOW))
      );
    } catch (IllegalArgumentException e) {
      sender.sendMessage(Component.text("Invalid ID!", NamedTextColor.RED));
    }
  }

  private void stopCurrent(CommandSender sender, Player player) {
    this.recordingService.getVoiceRecordings().stream()
      .filter(rec -> rec.getSpeakerUUID().equals(player.getUniqueId()))
      .filter(VoiceRecording::isRecording)
      .findFirst()
      .ifPresentOrElse(
        rec -> {
          this.recordingService.stopRecording(rec.getUuid());
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
  private void listRecordings(CommandSender sender) {
    final var recordings = this.recordingService.getVoiceRecordings();

//    .stream()
//      .sorted(Comparator.comparing(VoiceRecording::getEndTime, Comparator.nullsLast(Comparator.reverseOrder())))
//      .limit(10)
//      .toList();

    if (recordings.isEmpty()) {
      sender.sendMessage(Component.text("No recordings found", NamedTextColor.GRAY));
      return;
    }

    sender.sendMessage(
      Component.text("Recordings (", NamedTextColor.GRAY)
        .append(Component.text(recordings.size(), NamedTextColor.YELLOW))
        .append(Component.text(")", NamedTextColor.GRAY))
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
    CommandSender sender,
    @Argument(value = "id", suggestions = "recordings") String id,
    @Argument("player") @Nullable Player target
  ) {
    if (!(sender instanceof Player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    try {
      final var uuid = parseRecordingId(id);
      final var rec = this.recordingService.getVoiceRecordings().stream()
        .filter(r -> r.getUuid().equals(uuid))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Recording not found"));

      if (!rec.isFinished()) {
        sender.sendMessage(Component.text("Recording not finished!", NamedTextColor.RED));
        return;
      }

      final var player = target != null ? target : (Player) sender;
      final var conn = this.recordingService.getAPI().getConnectionOf(player.getUniqueId());

      if (conn == null) {
        sender.sendMessage(Component.text("Player not in voice chat!", NamedTextColor.RED));
        return;
      }

      this.recordingService.playRecordingTo(conn, rec);

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

  @CommandMethod("record delete <id>")
  @CommandPermission("dreamvoice.record.delete")
  @CommandDescription("Delete recording")
  private void deleteRecording(
    CommandSender sender,
    @Argument(value = "id", suggestions = "recordings") String id
  ) {
    try {
      final var uuid = parseRecordingId(id);
      this.recordingService.unregister(uuid);
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

  private @NotNull UUID parseRecordingId(String id) {
    // Simple heuristic: if length is 8, try to find matching UUID in existing recordings
    if (id.length() == 8) {
      return this.recordingService.getVoiceRecordings().stream()
        .map(VoiceRecording::getUuid)
        .filter(uuid -> uuid.toString().startsWith(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Recording not found"));
    }
    return UUID.fromString(id);
  }

  private @NotNull Component getStatusComponent(VoiceRecording rec) {
    if (rec.isRecording()) {
      return Component.text("[LIVE] ", NamedTextColor.RED);
    } else if (rec.isFinished()) {
      return Component.text("[DONE] ", NamedTextColor.GREEN)
        .append(Component.text(String.format("%.1fs ", rec.getDurationSeconds()), NamedTextColor.AQUA));
    }
    return Component.text("[WAIT] ", NamedTextColor.GRAY);
  }
}
