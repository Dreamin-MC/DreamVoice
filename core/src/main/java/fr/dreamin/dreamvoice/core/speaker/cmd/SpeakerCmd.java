package fr.dreamin.dreamvoice.core.speaker.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import fr.dreamin.dreamvoice.api.speaker.model.SpeakerMode;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
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

public final class SpeakerCmd {

  private final @Nullable VoiceSpeakerService speakerService =
    DreamVoice.getService(VoiceSpeakerService.class);

  private @Nullable VoiceSpeakerService requireSpeakerService(final @NotNull CommandSender sender) {
    if (this.speakerService == null) {
      sender.sendMessage(Component.text("[SVC] Service haut-parleur indisponible.", NamedTextColor.RED));
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
      player.sendMessage(Component.text("[SVC] Un haut-parleur avec ce nom existe déjà !", NamedTextColor.RED));
      return;
    }

    final var distance = (range != null && range > 0) ? range : 15.0f;

    Speaker.builder()
      .name(name)
      .location(player.getLocation())
      .distance(distance)
      .build();

    player.sendMessage(
      Component.text("[SVC] Haut-parleur '", NamedTextColor.GREEN)
        .append(Component.text(name, NamedTextColor.AQUA))
        .append(Component.text("' créé à votre position (portée: " + distance + " blocs, mode: GLOBAL) !", NamedTextColor.GREEN))
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
      sender.sendMessage(Component.text("[SVC] Haut-parleur introuvable: " + speakerName, NamedTextColor.RED));
      return;
    }

    speakerService.unregister(speaker);
    sender.sendMessage(
      Component.text("[SVC] Haut-parleur '", NamedTextColor.YELLOW)
        .append(Component.text(speakerName, NamedTextColor.AQUA))
        .append(Component.text("' supprimé avec succès !", NamedTextColor.YELLOW))
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
      sender.sendMessage(Component.text("[SVC] Aucun haut-parleur enregistré.", NamedTextColor.GRAY));
      return;
    }

    sender.sendMessage(
      Component.text("[SVC] Haut-parleurs actifs (", NamedTextColor.GRAY)
        .append(Component.text(speakers.size(), NamedTextColor.YELLOW))
        .append(Component.text("):", NamedTextColor.GRAY))
    );

    for (final var speaker : speakers) {
      final var loc = speaker.getLocation();
      final var locStr = loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
      sender.sendMessage(
        Component.text(" - ", NamedTextColor.GRAY)
          .append(Component.text(speaker.getName(), NamedTextColor.AQUA))
          .append(Component.text(" | Portée: " + speaker.getDistance() + "b | Mode: ", NamedTextColor.GRAY))
          .append(Component.text(speaker.getMode().name(), speaker.getMode() == SpeakerMode.GLOBAL ? NamedTextColor.GREEN : NamedTextColor.GOLD))
          .append(Component.text(" | Pos: " + locStr, NamedTextColor.DARK_GRAY))
      );
    }
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
      sender.sendMessage(Component.text("[SVC] Haut-parleur introuvable: " + speakerName, NamedTextColor.RED));
      return;
    }

    final var mode = modeRaw.equalsIgnoreCase("restricted") ? SpeakerMode.RESTRICTED : SpeakerMode.GLOBAL;
    speaker.setMode(mode);

    sender.sendMessage(
      Component.text("[SVC] Mode du haut-parleur '", NamedTextColor.GREEN)
        .append(Component.text(speaker.getName(), NamedTextColor.AQUA))
        .append(Component.text("' défini sur ", NamedTextColor.GREEN))
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
      sender.sendMessage(Component.text("[SVC] Haut-parleur introuvable: " + speakerName, NamedTextColor.RED));
      return;
    }

    speaker.linkSpeaker(target.getUniqueId());
    sender.sendMessage(
      Component.text("[SVC] Joueur ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
        .append(Component.text(" relié au haut-parleur '", NamedTextColor.GREEN))
        .append(Component.text(speaker.getName(), NamedTextColor.YELLOW))
        .append(Component.text("' !", NamedTextColor.GREEN))
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
      sender.sendMessage(Component.text("[SVC] Haut-parleur introuvable: " + speakerName, NamedTextColor.RED));
      return;
    }

    speaker.unlinkSpeaker(target.getUniqueId());
    sender.sendMessage(
      Component.text("[SVC] Joueur ", NamedTextColor.YELLOW)
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
        .append(Component.text(" retiré du haut-parleur '", NamedTextColor.YELLOW))
        .append(Component.text(speaker.getName(), NamedTextColor.YELLOW))
        .append(Component.text("' !", NamedTextColor.YELLOW))
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
      sender.sendMessage(Component.text("[SVC] Haut-parleur introuvable: " + speakerName, NamedTextColor.RED));
      return;
    }

    final var recService = DreamVoice.getService(VoiceRecordingService.class);
    if (recService == null) {
      sender.sendMessage(Component.text("[SVC] Service d'enregistrement indisponible.", NamedTextColor.RED));
      return;
    }

    try {
      final var recUuid = UUID.fromString(recordingIdRaw);
      final var recording = recService.getVoiceRecording(recUuid);
      if (recording == null) {
        sender.sendMessage(Component.text("[SVC] Enregistrement vocal introuvable: " + recordingIdRaw, NamedTextColor.RED));
        return;
      }

      speakerService.playRecording(speaker, recording);
      sender.sendMessage(
        Component.text("[SVC] Lecture de l'enregistrement sur le haut-parleur '", NamedTextColor.GREEN)
          .append(Component.text(speaker.getName(), NamedTextColor.AQUA))
          .append(Component.text("' !", NamedTextColor.GREEN))
      );
    } catch (Exception e) {
      sender.sendMessage(Component.text("[SVC] UUID invalide: " + recordingIdRaw, NamedTextColor.RED));
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
      sender.sendMessage(Component.text("[SVC] Haut-parleur introuvable: " + speakerName, NamedTextColor.RED));
      return;
    }

    final var isLoop = loop != null && loop;
    speakerService.playSoundFile(speaker, fileName, isLoop);
    sender.sendMessage(
      Component.text("[SVC] Lecture du fichier audio '", NamedTextColor.GREEN)
        .append(Component.text(fileName, NamedTextColor.YELLOW))
        .append(Component.text("' sur le haut-parleur '", NamedTextColor.GREEN))
        .append(Component.text(speaker.getName(), NamedTextColor.AQUA))
        .append(Component.text("' " + (isLoop ? "(en boucle)" : "") + " !", NamedTextColor.GREEN))
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
      sender.sendMessage(Component.text("[SVC] Haut-parleur introuvable: " + speakerName, NamedTextColor.RED));
      return;
    }

    final var isLoop = loop != null && loop;
    sender.sendMessage(Component.text("[SVC] Chargement du flux audio...", NamedTextColor.GRAY));
    speakerService.playSoundUrl(speaker, url, isLoop);
    sender.sendMessage(
      Component.text("[SVC] Diffusion de l'URL sur le haut-parleur '", NamedTextColor.GREEN)
        .append(Component.text(speaker.getName(), NamedTextColor.AQUA))
        .append(Component.text("' " + (isLoop ? "(en boucle)" : "") + " !", NamedTextColor.GREEN))
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
      sender.sendMessage(Component.text("[SVC] Haut-parleur introuvable: " + speakerName, NamedTextColor.RED));
      return;
    }

    speakerService.stopSound(speaker);
    sender.sendMessage(
      Component.text("[SVC] Lecture audio arrêtée sur le haut-parleur '", NamedTextColor.YELLOW)
        .append(Component.text(speaker.getName(), NamedTextColor.AQUA))
        .append(Component.text("' !", NamedTextColor.YELLOW))
    );
  }

}


