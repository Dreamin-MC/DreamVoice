package fr.dreamin.dreamvoice.core.speaker.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import fr.dreamin.dreamvoice.api.speaker.model.SpeakerMode;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
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

public final class SpeakerCmd {

  private final @Nullable VoiceSpeakerService speakerService =
    DreamVoice.getService(VoiceSpeakerService.class);

  private @Nullable VoiceSpeakerService requireSpeakerService(final @NotNull CommandSender sender) {
    if (this.speakerService == null) {
      sender.sendMessage(Component.text("[SVC] Speaker service unavailable.", NamedTextColor.RED));
      return null;
    }
    return this.speakerService;
  }

  // ------------------------------------------------------------
  // Suggestions
  // ------------------------------------------------------------

  @Suggestions("speakers")
  public List<String> suggSpeakers(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    if (this.speakerService == null)
      return List.of();

    return this.speakerService.getSpeakers().stream()
      .map(Speaker::getName)
      .filter(name -> name.toLowerCase().startsWith(in.toLowerCase()))
      .sorted()
      .collect(Collectors.toList());
  }

  @Suggestions("speaker_modes")
  public List<String> suggModes(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    return List.of("global", "restricted");
  }

  @Suggestions("recordings")
  public List<String> suggRecordings(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    final var recService = DreamVoice.getService(VoiceRecordingService.class);
    if (recService == null)
      return List.of();

    return recService.getVoiceRecordings().stream()
      .map(r -> r.getUuid().toString())
      .filter(id -> id.startsWith(in.toLowerCase()))
      .collect(Collectors.toList());
  }

  // ###############################################################
  // ----------------------- COMMANDS METHODS ----------------------
  // ###############################################################

  @CommandDescription("Add a new speaker at your location")
  @CommandMethod("speaker add <name> [range]")
  @CommandPermission("dreamvoice.speaker.add")
  private void addSpeaker(
    final @NotNull CommandSender sender,
    @Argument("name") final @NotNull String name,
    @Argument("range") final @Nullable Float range
  ) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("Player only!", NamedTextColor.RED));
      return;
    }

    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    if (speakerService.getSpeaker(name) != null) {
      player.sendMessage(Component.text("[SVC] A speaker with this name already exists!", NamedTextColor.RED));
      return;
    }

    final var distance = (range != null && range > 0) ? range : 15.0f;

    Speaker.builder()
      .name(name)
      .location(player.getLocation())
      .distance(distance)
      .build();

    player.sendMessage(
      Component.text("[SVC] Speaker '", NamedTextColor.GREEN)
        .append(Component.text(name, NamedTextColor.AQUA))
        .append(Component.text("' created at your location (range: " + distance + " blocks, mode: GLOBAL)!", NamedTextColor.GREEN))
    );
  }

  @CommandDescription("Remove a speaker")
  @CommandMethod("speaker remove <speaker>")
  @CommandPermission("dreamvoice.speaker.remove")
  private void removeSpeaker(
    final @NotNull CommandSender sender,
    @Argument(value = "speaker", suggestions = "speakers") final @NotNull String speakerName
  ) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.text("[SVC] Speaker not found: " + speakerName, NamedTextColor.RED));
      return;
    }

    speakerService.unregister(speaker);
    sender.sendMessage(
      Component.text("[SVC] Speaker '", NamedTextColor.YELLOW)
        .append(Component.text(speakerName, NamedTextColor.AQUA))
        .append(Component.text("' successfully deleted!", NamedTextColor.YELLOW))
    );
  }

  @CommandDescription("List all speakers")
  @CommandMethod("speaker list")
  @CommandPermission("dreamvoice.speaker.list")
  private void listSpeakers(final @NotNull CommandSender sender) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    final var speakers = speakerService.getSpeakers();
    if (speakers.isEmpty()) {
      sender.sendMessage(Component.text("[SVC] No active speakers.", NamedTextColor.GRAY));
      return;
    }

    sender.sendMessage(
      Component.text("[SVC] Active speakers (", NamedTextColor.GRAY)
        .append(Component.text(speakers.size(), NamedTextColor.YELLOW))
        .append(Component.text("):", NamedTextColor.GRAY))
    );

    for (final var speaker : speakers) {
      final var loc = speaker.getLocation();
      final var locStr = loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
      sender.sendMessage(
        Component.text(" - ", NamedTextColor.GRAY)
          .append(Component.text(speaker.getName(), NamedTextColor.AQUA))
          .append(Component.text(" | Range: " + speaker.getDistance() + "m | Mode: ", NamedTextColor.GRAY))
          .append(Component.text(speaker.getMode().name(), speaker.getMode() == SpeakerMode.GLOBAL ? NamedTextColor.GREEN : NamedTextColor.GOLD))
          .append(Component.text(" | Pos: " + locStr, NamedTextColor.DARK_GRAY))
      );
    }
  }

  @CommandDescription("Show detailed info of a speaker")
  @CommandMethod("speaker info <speaker>")
  @CommandPermission("dreamvoice.speaker.list")
  private void infoSpeaker(
    final @NotNull CommandSender sender,
    @Argument(value = "speaker", suggestions = "speakers") final @NotNull String speakerName
  ) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.text("[SVC] Speaker not found: " + speakerName, NamedTextColor.RED));
      return;
    }

    final var loc = speaker.getLocation();
    final var attached = speaker.getTargetEntity() != null && speaker.getTargetEntity().isValid() ? speaker.getTargetEntity().getType().name() : "None";

    sender.sendMessage(Component.text("==== [SPEAKER: " + speaker.getName().toUpperCase() + "] ====", NamedTextColor.GOLD));
    sender.sendMessage(Component.text("Position: ", NamedTextColor.GRAY).append(Component.text(String.format("%.1f, %.1f, %.1f (%s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld() != null ? loc.getWorld().getName() : "?"), NamedTextColor.AQUA)));
    sender.sendMessage(Component.text("Attached Entity: ", NamedTextColor.GRAY).append(Component.text(attached, NamedTextColor.LIGHT_PURPLE)));
    sender.sendMessage(Component.text("Range: ", NamedTextColor.GRAY).append(Component.text((speaker.getDistance() != null ? speaker.getDistance() : 16.0f) + "m", NamedTextColor.YELLOW)));
    sender.sendMessage(Component.text("Mode: ", NamedTextColor.GRAY).append(Component.text(speaker.getMode().name(), speaker.getMode() == SpeakerMode.GLOBAL ? NamedTextColor.GREEN : NamedTextColor.GOLD)));
    sender.sendMessage(Component.text("Playing: ", NamedTextColor.GRAY).append(Component.text(speaker.isPlaying() ? "YES 🎵" : "NO", speaker.isPlaying() ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
    sender.sendMessage(Component.text("Allowed Players: ", NamedTextColor.GRAY).append(Component.text(speaker.getAllowedSpeakers().size() + " player(s)", NamedTextColor.AQUA)));
  }

  @CommandDescription("Change speaker mode (global / restricted)")
  @CommandMethod("speaker mode <speaker> <mode>")
  @CommandPermission("dreamvoice.speaker.mode")
  private void setMode(
    final @NotNull CommandSender sender,
    @Argument(value = "speaker", suggestions = "speakers") final @NotNull String speakerName,
    @Argument(value = "mode", suggestions = "speaker_modes") final @NotNull String modeRaw
  ) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.text("[SVC] Speaker not found: " + speakerName, NamedTextColor.RED));
      return;
    }

    final var mode = modeRaw.equalsIgnoreCase("restricted") ? SpeakerMode.RESTRICTED : SpeakerMode.GLOBAL;
    speaker.setMode(mode);

    sender.sendMessage(
      Component.text("[SVC] Mode of speaker '", NamedTextColor.GREEN)
        .append(Component.text(speaker.getName(), NamedTextColor.AQUA))
        .append(Component.text("' set to ", NamedTextColor.GREEN))
        .append(Component.text(mode.name(), mode == SpeakerMode.GLOBAL ? NamedTextColor.YELLOW : NamedTextColor.GOLD))
    );
  }

  @CommandDescription("Link a player to speak through a restricted speaker")
  @CommandMethod("speaker link <speaker> <player>")
  @CommandPermission("dreamvoice.speaker.modify")
  private void linkSpeaker(
    final @NotNull CommandSender sender,
    @Argument(value = "speaker", suggestions = "speakers") final @NotNull String speakerName,
    @Argument("player") final @NotNull Player target
  ) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.text("[SVC] Speaker not found: " + speakerName, NamedTextColor.RED));
      return;
    }

    speaker.linkSpeaker(target.getUniqueId());
    sender.sendMessage(
      Component.text("[SVC] Player ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
        .append(Component.text(" linked to speaker '", NamedTextColor.GREEN))
        .append(Component.text(speaker.getName(), NamedTextColor.YELLOW))
        .append(Component.text("'!", NamedTextColor.GREEN))
    );
  }

  @CommandDescription("Unlink a player from a speaker")
  @CommandMethod("speaker unlink <speaker> <player>")
  @CommandPermission("dreamvoice.speaker.modify")
  private void unlinkSpeaker(
    final @NotNull CommandSender sender,
    @Argument(value = "speaker", suggestions = "speakers") final @NotNull String speakerName,
    @Argument("player") final @NotNull Player target
  ) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.text("[SVC] Speaker not found: " + speakerName, NamedTextColor.RED));
      return;
    }

    speaker.unlinkSpeaker(target.getUniqueId());
    sender.sendMessage(
      Component.text("[SVC] Player ", NamedTextColor.YELLOW)
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
        .append(Component.text(" unlinked from speaker '", NamedTextColor.YELLOW))
        .append(Component.text(speaker.getName(), NamedTextColor.YELLOW))
        .append(Component.text("'!", NamedTextColor.YELLOW))
    );
  }

  @CommandDescription("Play a voice recording through a speaker")
  @CommandMethod("speaker play record <speaker> <recording>")
  @CommandPermission("dreamvoice.speaker.play")
  private void playRecord(
    final @NotNull CommandSender sender,
    @Argument(value = "speaker", suggestions = "speakers") final @NotNull String speakerName,
    @Argument(value = "recording", suggestions = "recordings") final @NotNull String recordingIdRaw
  ) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.text("[SVC] Speaker not found: " + speakerName, NamedTextColor.RED));
      return;
    }

    final var recService = DreamVoice.getService(VoiceRecordingService.class);
    if (recService == null) {
      sender.sendMessage(Component.text("[SVC] Recording service unavailable.", NamedTextColor.RED));
      return;
    }

    try {
      final var recUuid = UUID.fromString(recordingIdRaw);
      final var recording = recService.getVoiceRecording(recUuid);
      if (recording == null) {
        sender.sendMessage(Component.text("[SVC] Voice recording not found: " + recordingIdRaw, NamedTextColor.RED));
        return;
      }

      speakerService.playRecording(speaker, recording);
      sender.sendMessage(
        Component.text("[SVC] Playing recording on speaker '", NamedTextColor.GREEN)
          .append(Component.text(speaker.getName(), NamedTextColor.AQUA))
          .append(Component.text("'!", NamedTextColor.GREEN))
      );
    } catch (Exception e) {
      sender.sendMessage(Component.text("[SVC] Invalid UUID: " + recordingIdRaw, NamedTextColor.RED));
    }
  }

  @CommandDescription("Play an audio file on a speaker")
  @CommandMethod("speaker play file <speaker> <fileName> [loop]")
  @CommandPermission("dreamvoice.speaker.play")
  private void playFile(
    final @NotNull CommandSender sender,
    @Argument(value = "speaker", suggestions = "speakers") final @NotNull String speakerName,
    @Argument("fileName") final @NotNull String fileName,
    @Argument("loop") final @Nullable Boolean loop
  ) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.text("[SVC] Speaker not found: " + speakerName, NamedTextColor.RED));
      return;
    }

    final var isLoop = loop != null && loop;
    speakerService.playSoundFile(speaker, fileName, isLoop);
    sender.sendMessage(
      Component.text("[SVC] Playing audio file '", NamedTextColor.GREEN)
        .append(Component.text(fileName, NamedTextColor.YELLOW))
        .append(Component.text("' on speaker '", NamedTextColor.GREEN))
        .append(Component.text(speaker.getName(), NamedTextColor.AQUA))
        .append(Component.text("' " + (isLoop ? "(looping)" : "") + "!", NamedTextColor.GREEN))
    );
  }

  @CommandDescription("Play an audio stream URL on a speaker")
  @CommandMethod("speaker play url <speaker> <url> [loop]")
  @CommandPermission("dreamvoice.speaker.play")
  private void playUrl(
    final @NotNull CommandSender sender,
    @Argument(value = "speaker", suggestions = "speakers") final @NotNull String speakerName,
    @Argument("url") final @NotNull String url,
    @Argument("loop") final @Nullable Boolean loop
  ) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.text("[SVC] Speaker not found: " + speakerName, NamedTextColor.RED));
      return;
    }

    final var isLoop = loop != null && loop;
    sender.sendMessage(Component.text("[SVC] Loading audio stream...", NamedTextColor.GRAY));
    speakerService.playSoundUrl(speaker, url, isLoop);
    sender.sendMessage(
      Component.text("[SVC] Streaming audio URL on speaker '", NamedTextColor.GREEN)
        .append(Component.text(speaker.getName(), NamedTextColor.AQUA))
        .append(Component.text("' " + (isLoop ? "(looping)" : "") + "!", NamedTextColor.GREEN))
    );
  }

  @CommandDescription("Stop audio playing on a speaker")
  @CommandMethod("speaker stop <speaker>")
  @CommandPermission("dreamvoice.speaker.play")
  private void stopSpeaker(
    final @NotNull CommandSender sender,
    @Argument(value = "speaker", suggestions = "speakers") final @NotNull String speakerName
  ) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.text("[SVC] Speaker not found: " + speakerName, NamedTextColor.RED));
      return;
    }

    speakerService.stopSound(speaker);
    sender.sendMessage(
      Component.text("[SVC] Audio stopped on speaker '", NamedTextColor.YELLOW)
        .append(Component.text(speaker.getName(), NamedTextColor.AQUA))
        .append(Component.text("'!", NamedTextColor.YELLOW))
    );
  }

  @CommandDescription("Save all speakers to disk")
  @CommandMethod("speaker save")
  @CommandPermission("dreamvoice.speaker.save")
  private void saveSpeakers(final @NotNull CommandSender sender) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    speakerService.save();
    sender.sendMessage(Component.text("[SVC] All speakers successfully saved to disk!", NamedTextColor.GREEN));
  }

  @CommandDescription("Reload all speakers from disk")
  @CommandMethod("speaker reload")
  @CommandPermission("dreamvoice.speaker.reload")
  private void reloadSpeakers(final @NotNull CommandSender sender) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    speakerService.load();
    sender.sendMessage(Component.text("[SVC] Speakers successfully reloaded from disk (" + speakerService.getSpeakers().size() + " active)!", NamedTextColor.GREEN));
  }

}
