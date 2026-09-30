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
import fr.dreamin.dreamvoice.core.utils.LocationUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SpeakerCmd {

  private final @Nullable VoiceSpeakerService speakerService =
    DreamVoice.getService(VoiceSpeakerService.class);

  private @Nullable VoiceSpeakerService requireSpeakerService(final @NotNull CommandSender sender) {
    if (this.speakerService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
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

    final var list = new ArrayList<String>();
    if ("all".startsWith(in.toLowerCase()))
      list.add("all");

    this.speakerService.getSpeakers().stream()
      .map(Speaker::getName)
      .filter(name -> name.toLowerCase().startsWith(in.toLowerCase()))
      .sorted()
      .forEach(list::add);

    return list;
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
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    if (name.equalsIgnoreCase("all")) {
      player.sendMessage(Component.translatable("speaker.reserved_name"));
      return;
    }

    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    if (speakerService.getSpeaker(name) != null) {
      player.sendMessage(Component.translatable("speaker.already_exists", Component.text(name)));
      return;
    }

    final var distance = (range != null && range > 0) ? range : 15.0f;

    Speaker.builder()
      .name(name)
      .location(LocationUtils.toVoiceLocation(player.getLocation()))
      .distance(distance)
      .build();

    player.sendMessage(Component.translatable("speaker.created", Component.text(name), Component.text(distance)));
  }

  @CommandDescription("Set the broadcast distance of a speaker")
  @CommandMethod("speaker distance <speaker> <distance>")
  @CommandPermission("dreamvoice.speaker.modify")
  private void setDistance(
    final @NotNull CommandSender sender,
    @Argument(value = "speaker", suggestions = "speakers") final @NotNull String speakerName,
    @Argument("distance") final float distance
  ) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.translatable("speaker.not_found", Component.text(speakerName)));
      return;
    }

    speaker.updateDistance(distance);
    sender.sendMessage(Component.translatable("speaker.distance_set", Component.text(speakerName), Component.text(distance)));
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

    if (speakerName.equalsIgnoreCase("all")) {
      final var speakers = List.copyOf(speakerService.getSpeakers());
      if (speakers.isEmpty()) {
        sender.sendMessage(Component.translatable("speaker.list_empty"));
        return;
      }

      speakerService.unregisterAll();
      sender.sendMessage(Component.translatable("speaker.all_removed", Component.text(speakers.size())));
      return;
    }

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.translatable("speaker.not_found", Component.text(speakerName)));
      return;
    }

    speakerService.unregister(speaker);
    sender.sendMessage(Component.translatable("speaker.removed", Component.text(speakerName)));
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
      sender.sendMessage(Component.translatable("speaker.list_empty"));
      return;
    }

    sender.sendMessage(Component.translatable("speaker.list_header", Component.text(speakers.size())));

    for (final var speaker : speakers) {
      final var loc = speaker.getLocation();
      final var locStr = loc.world() + " (" + (int) loc.x() + ", " + (int) loc.y() + ", " + (int) loc.z() + ")";
      sender.sendMessage(Component.translatable("speaker.list_item",
        Component.text(speaker.getName()),
        Component.text(speaker.getDistance() != null ? speaker.getDistance() : 16.0f),
        Component.text(speaker.getMode().name()),
        Component.text(locStr)
      ));
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

    if (speakerName.equalsIgnoreCase("all")) {
      final var speakers = speakerService.getSpeakers();
      if (speakers.isEmpty()) {
        sender.sendMessage(Component.translatable("speaker.list_empty"));
        return;
      }

      for (final var speaker : speakers)
        sendSpeakerInfo(sender, speaker);
      return;
    }

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.translatable("speaker.not_found", Component.text(speakerName)));
      return;
    }

    sendSpeakerInfo(sender, speaker);
  }

  private void sendSpeakerInfo(final @NotNull CommandSender sender, final @NotNull Speaker speaker) {
    final var loc = speaker.getLocation();
    final var targetEntity = speaker.getTargetEntityUuid() != null ? Bukkit.getEntity(speaker.getTargetEntityUuid()) : null;
    final var attached = targetEntity != null && targetEntity.isValid() ? targetEntity.getType().name() : "None";

    sender.sendMessage(Component.translatable("speaker.info_header", Component.text(speaker.getName().toUpperCase())));
    sender.sendMessage(Component.translatable("speaker.info_position", Component.text(String.format("%.1f, %.1f, %.1f (%s)", loc.x(), loc.y(), loc.z(), loc.world()))));
    sender.sendMessage(Component.translatable("speaker.info_attached", Component.text(attached)));
    sender.sendMessage(Component.translatable("speaker.info_range", Component.text(speaker.getDistance() != null ? speaker.getDistance() : 16.0f)));
    sender.sendMessage(Component.translatable("speaker.info_mode", Component.text(speaker.getMode().name())));
    sender.sendMessage(Component.translatable("speaker.info_playing", Component.text(speaker.isPlaying() ? "YES" : "NO")));
    sender.sendMessage(Component.translatable("speaker.info_allowed", Component.text(speaker.getAllowedSpeakers().size())));
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

    final var mode = modeRaw.equalsIgnoreCase("restricted") ? SpeakerMode.RESTRICTED : SpeakerMode.GLOBAL;

    if (speakerName.equalsIgnoreCase("all")) {
      final var speakers = speakerService.getSpeakers();
      if (speakers.isEmpty()) {
        sender.sendMessage(Component.translatable("speaker.list_empty"));
        return;
      }

      for (final var speaker : speakers)
        speaker.setMode(mode);

      sender.sendMessage(Component.translatable("speaker.mode_set", Component.text("all"), Component.text(mode.name())));
      return;
    }

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.translatable("speaker.not_found", Component.text(speakerName)));
      return;
    }

    speaker.setMode(mode);
    sender.sendMessage(Component.translatable("speaker.mode_set", Component.text(speaker.getName()), Component.text(mode.name())));
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

    if (speakerName.equalsIgnoreCase("all")) {
      final var speakers = speakerService.getSpeakers();
      if (speakers.isEmpty()) {
        sender.sendMessage(Component.translatable("speaker.list_empty"));
        return;
      }

      for (final var speaker : speakers)
        speaker.linkSpeaker(target.getUniqueId());

      sender.sendMessage(Component.translatable("speaker.linked", Component.text("all"), Component.text(target.getName())));
      return;
    }

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.translatable("speaker.not_found", Component.text(speakerName)));
      return;
    }

    speaker.linkSpeaker(target.getUniqueId());
    sender.sendMessage(Component.translatable("speaker.linked", Component.text(speaker.getName()), Component.text(target.getName())));
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

    if (speakerName.equalsIgnoreCase("all")) {
      final var speakers = speakerService.getSpeakers();
      if (speakers.isEmpty()) {
        sender.sendMessage(Component.translatable("speaker.list_empty"));
        return;
      }

      for (final var speaker : speakers)
        speaker.unlinkSpeaker(target.getUniqueId());

      sender.sendMessage(Component.translatable("speaker.unlinked", Component.text("all")));
      return;
    }

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.translatable("speaker.not_found", Component.text(speakerName)));
      return;
    }

    speaker.unlinkSpeaker(target.getUniqueId());
    sender.sendMessage(Component.translatable("speaker.unlinked", Component.text(speaker.getName())));
  }

  @CommandDescription("Play a voice recording through a speaker")
  @CommandMethod("speaker play record <speaker> <recording> [loop]")
  @CommandPermission("dreamvoice.speaker.play")
  private void playRecord(
    final @NotNull CommandSender sender,
    @Argument(value = "speaker", suggestions = "speakers") final @NotNull String speakerName,
    @Argument(value = "recording", suggestions = "recordings") final @NotNull String recordingIdRaw,
    @Argument("loop") final @Nullable Boolean loop
  ) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    final var isLoop = loop != null && loop;

    final var recService = DreamVoice.getService(VoiceRecordingService.class);
    if (recService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return;
    }

    try {
      final var recUuid = UUID.fromString(recordingIdRaw);
      final var recording = recService.getVoiceRecording(recUuid);
      if (recording == null) {
        sender.sendMessage(Component.translatable("record.not_found", Component.text(recordingIdRaw)));
        return;
      }

      if (speakerName.equalsIgnoreCase("all")) {
        final var speakers = speakerService.getSpeakers();
        if (speakers.isEmpty()) {
          sender.sendMessage(Component.translatable("speaker.list_empty"));
          return;
        }

        speakerService.playRecording(speakers, recording, isLoop);
        sender.sendMessage(Component.translatable("speaker.playing", Component.text("record:" + recUuid.toString().substring(0, 8)), Component.text("all")));
        return;
      }

      final var speaker = speakerService.getSpeaker(speakerName);
      if (speaker == null) {
        sender.sendMessage(Component.translatable("speaker.not_found", Component.text(speakerName)));
        return;
      }

      speakerService.playRecording(speaker, recording, isLoop);
      sender.sendMessage(Component.translatable("speaker.playing", Component.text("record:" + recUuid.toString().substring(0, 8)), Component.text(speaker.getName())));
    } catch (Exception e) {
      sender.sendMessage(Component.translatable("record.not_found", Component.text(recordingIdRaw)));
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

    final var isLoop = loop != null && loop;

    if (speakerName.equalsIgnoreCase("all")) {
      final var speakers = speakerService.getSpeakers();
      if (speakers.isEmpty()) {
        sender.sendMessage(Component.translatable("speaker.list_empty"));
        return;
      }

      speakerService.playSoundFile(speakers, fileName, isLoop);
      sender.sendMessage(Component.translatable("speaker.playing", Component.text(fileName), Component.text("all")));
      return;
    }

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.translatable("speaker.not_found", Component.text(speakerName)));
      return;
    }

    speakerService.playSoundFile(speaker, fileName, isLoop);
    sender.sendMessage(Component.translatable("speaker.playing", Component.text(fileName), Component.text(speaker.getName())));
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

    final var isLoop = loop != null && loop;

    if (speakerName.equalsIgnoreCase("all")) {
      final var speakers = speakerService.getSpeakers();
      if (speakers.isEmpty()) {
        sender.sendMessage(Component.translatable("speaker.list_empty"));
        return;
      }

      sender.sendMessage(Component.translatable("speaker.loading_audio_all"));
      speakerService.playSoundUrl(speakers, url, isLoop);
      sender.sendMessage(Component.translatable("speaker.playing", Component.text(url), Component.text("all")));
      return;
    }

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.translatable("speaker.not_found", Component.text(speakerName)));
      return;
    }

    sender.sendMessage(Component.translatable("speaker.loading_audio"));
    speakerService.playSoundUrl(speaker, url, isLoop);
    sender.sendMessage(Component.translatable("speaker.playing", Component.text(url), Component.text(speaker.getName())));
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

    if (speakerName.equalsIgnoreCase("all")) {
      final var speakers = speakerService.getSpeakers();
      if (speakers.isEmpty()) {
        sender.sendMessage(Component.translatable("speaker.list_empty"));
        return;
      }

      for (final var speaker : speakers)
        speakerService.stopSound(speaker);

      sender.sendMessage(Component.translatable("speaker.stopped", Component.text("all")));
      return;
    }

    final var speaker = speakerService.getSpeaker(speakerName);
    if (speaker == null) {
      sender.sendMessage(Component.translatable("speaker.not_found", Component.text(speakerName)));
      return;
    }

    speakerService.stopSound(speaker);
    sender.sendMessage(Component.translatable("speaker.stopped", Component.text(speaker.getName())));
  }

  @CommandDescription("Save all speakers to disk")
  @CommandMethod("speaker save")
  @CommandPermission("dreamvoice.speaker.save")
  private void saveSpeakers(final @NotNull CommandSender sender) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    speakerService.save();
    sender.sendMessage(Component.translatable("persistence.speakers_saved"));
  }

  @CommandDescription("Reload all speakers from disk")
  @CommandMethod("speaker reload")
  @CommandPermission("dreamvoice.speaker.reload")
  private void reloadSpeakers(final @NotNull CommandSender sender) {
    final var speakerService = requireSpeakerService(sender);
    if (speakerService == null)
      return;

    speakerService.load();
    sender.sendMessage(Component.translatable("persistence.speakers_reloaded", Component.text(speakerService.getSpeakers().size())));
  }

}
