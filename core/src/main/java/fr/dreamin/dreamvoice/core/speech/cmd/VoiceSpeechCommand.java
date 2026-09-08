package fr.dreamin.dreamvoice.core.speech.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.speech.model.SpeechModelInfo;
import fr.dreamin.dreamvoice.api.speech.service.VoiceSpeechService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.speech.model.VoskModelRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class VoiceSpeechCommand {

  private final @Nullable VoiceSpeechService speechService = DreamVoice.getService(VoiceSpeechService.class);
  private final @Nullable VoiceRecordingService recordingService = DreamVoice.getService(VoiceRecordingService.class);

  // ###############################################################
  // ------------------------- SUGGESTIONS -------------------------
  // ###############################################################

  @Suggestions("speech_languages")
  public @NotNull List<String> suggestLanguages(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String input) {
    return VoskModelRegistry.getAllModels().stream()
      .map(SpeechModelInfo::language)
      .filter(l -> l.startsWith(input.toLowerCase()))
      .toList();
  }

  @Suggestions("speech_installed_models")
  public @NotNull List<String> suggestInstalled(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String input) {
    if (this.speechService == null)
      return List.of();
    return this.speechService.getInstalledModels().stream()
      .filter(m -> m.toLowerCase().startsWith(input.toLowerCase()))
      .toList();
  }

  // ###############################################################
  // ----------------------- COMMANDS METHODS ----------------------
  // ###############################################################

  @CommandMethod("voice speech download <lang>")
  @CommandPermission("dreamvoice.speech.admin")
  @CommandDescription("Download an official speech recognition model by language code (e.g. fr, en, de)")
  private void downloadModel(
    final @NotNull CommandSender sender,
    @Argument(value = "lang", suggestions = "speech_languages") final @NotNull String lang
  ) {
    if (this.speechService == null) {
      sender.sendMessage(Component.text("[Speech] VoiceSpeechService unavailable.", NamedTextColor.RED));
      return;
    }

    final var modelInfo = VoskModelRegistry.findByLangOrId(lang);
    if (modelInfo == null) {
      sender.sendMessage(Component.text("Unknown model or language code '" + lang + "'. Use tab-completion or check '/voice speech models'.", NamedTextColor.RED));
      return;
    }

    sender.sendMessage(
      Component.text("Downloading speech model ", NamedTextColor.GRAY)
        .append(Component.text(modelInfo.displayName(), NamedTextColor.YELLOW))
        .append(Component.text(" (~" + modelInfo.sizeMb() + "MB)...", NamedTextColor.GRAY))
    );

    this.speechService.downloadModel(lang, progress -> {
      final var pct = (int) (progress * 100);
      if (pct % 25 == 0)
        sender.sendMessage(Component.text("Download progress: " + pct + "%", NamedTextColor.DARK_AQUA));

    }).thenAccept(folder -> Bukkit.getScheduler().runTask(DreamVoice.getInstance(), () -> sender.sendMessage(
      Component.text("Model successfully installed: ", NamedTextColor.GREEN)
        .append(Component.text(folder.getName(), NamedTextColor.YELLOW))
    ))).exceptionally(err -> {
      Bukkit.getScheduler().runTask(DreamVoice.getInstance(), () ->
        sender.sendMessage(Component.text("Download failed: " + err.getMessage(), NamedTextColor.RED))
      );
      return null;
    });
  }

  @CommandMethod("voice speech models")
  @CommandPermission("dreamvoice.speech.admin")
  @CommandDescription("List all installed speech models")
  private void listModels(final @NotNull CommandSender sender) {
    if (this.speechService == null) {
      sender.sendMessage(Component.text("[Speech] VoiceSpeechService unavailable.", NamedTextColor.RED));
      return;
    }

    final var installed = this.speechService.getInstalledModels();
    final var active = this.speechService.getActiveModel();

    sender.sendMessage(Component.text("--- Installed Speech Models ---", NamedTextColor.GOLD));
    sender.sendMessage(Component.text("Active Model: ", NamedTextColor.GRAY).append(Component.text(active, NamedTextColor.YELLOW)));

    if (installed.isEmpty()) {
      sender.sendMessage(Component.text("No models installed in plugins/DreamVoice/modules/speech/models/.", NamedTextColor.GRAY));
      sender.sendMessage(Component.text("Tip: Use '/voice speech download <lang>' to download one.", NamedTextColor.DARK_GRAY));
      return;
    }

    for (final var m : installed) {
      final var isCurrent = m.equalsIgnoreCase(active);
      sender.sendMessage(
        Component.text(isCurrent ? " * " : " - ", isCurrent ? NamedTextColor.GREEN : NamedTextColor.GRAY)
          .append(Component.text(m, isCurrent ? NamedTextColor.GREEN : NamedTextColor.WHITE))
      );
    }
  }

  @CommandMethod("voice speech setmodel <modelName>")
  @CommandPermission("dreamvoice.speech.admin")
  @CommandDescription("Set the active speech recognition model")
  private void setModel(
    final @NotNull CommandSender sender,
    @Argument(value = "modelName", suggestions = "speech_installed_models") final @NotNull String modelName
  ) {
    if (this.speechService == null) {
      sender.sendMessage(Component.text("[Speech] VoiceSpeechService unavailable.", NamedTextColor.RED));
      return;
    }

    this.speechService.setActiveModel(modelName);
    sender.sendMessage(
      Component.text("Active model set to: ", NamedTextColor.GREEN)
        .append(Component.text(modelName, NamedTextColor.YELLOW))
    );
  }

  @CommandMethod("voice speech transcribe <id> [output]")
  @CommandPermission("dreamvoice.speech.admin")
  @CommandDescription("Transcribe a voice recording session into a book or chat output")
  private void transcribeRecording(
    final @NotNull CommandSender sender,
    @Argument("id") final @NotNull String id,
    @Argument("output") final @Nullable String outputArg
  ) {
    if (this.speechService == null || this.recordingService == null) {
      sender.sendMessage(Component.text("[Speech] Speech or Recording service unavailable.", NamedTextColor.RED));
      return;
    }

    final VoiceRecording rec;
    try {
      final var targetUuid = id.length() == 8
        ? this.recordingService.getVoiceRecordings().stream()
            .map(VoiceRecording::getUuid)
            .filter(u -> u.toString().startsWith(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Recording not found"))
        : UUID.fromString(id);
      rec = this.recordingService.getVoiceRecording(targetUuid);
    } catch (Exception e) {
      sender.sendMessage(Component.text("Recording not found: " + id, NamedTextColor.RED));
      return;
    }

    if (rec == null) {
      sender.sendMessage(Component.text("Recording not found: " + id, NamedTextColor.RED));
      return;
    }

    sender.sendMessage(Component.text("Transcribing recording " + id + "...", NamedTextColor.GRAY));

    final var outputType = outputArg != null ? outputArg.toLowerCase() : "chat";

    this.speechService.transcribeRecording(rec).thenAccept(result -> Bukkit.getScheduler().runTask(DreamVoice.getInstance(), () -> {
      if ("book".equals(outputType) && sender instanceof Player player) {
        final var book = this.speechService.createReportBook(player, result);
        player.getInventory().addItem(book);
        player.sendMessage(Component.text("Transcription book generated and added to inventory!", NamedTextColor.GREEN));
      } else {
        sender.sendMessage(Component.text("=== Transcription Report: " + id + " ===", NamedTextColor.GOLD));
        if (result.segments().isEmpty()) {
          sender.sendMessage(Component.text("(No words detected)", NamedTextColor.GRAY));
        } else {
          for (final var seg : result.segments()) {
            sender.sendMessage(
              Component.text(String.format("[%.1fs] ", seg.offsetSeconds()), NamedTextColor.DARK_GRAY)
                .append(Component.text(seg.speakerName() + ": ", NamedTextColor.YELLOW))
                .append(Component.text(seg.text(), NamedTextColor.WHITE))
            );
          }
        }
      }
    })).exceptionally(err -> {
      Bukkit.getScheduler().runTask(DreamVoice.getInstance(), () -> {
        sender.sendMessage(Component.text("Transcription failed: " + err.getMessage(), NamedTextColor.RED));
      });
      return null;
    });
  }

  @CommandMethod("voice speech reload")
  @CommandPermission("dreamvoice.speech.admin")
  @CommandDescription("Reload speech configuration and keyword definitions")
  private void reloadSpeech(final @NotNull CommandSender sender) {
    if (this.speechService == null) {
      sender.sendMessage(Component.text("[Speech] VoiceSpeechService unavailable.", NamedTextColor.RED));
      return;
    }

    this.speechService.reload();
    sender.sendMessage(Component.text("Speech configuration and keywords reloaded.", NamedTextColor.GREEN));
  }

  @CommandMethod("voice speech debug")
  @CommandPermission("dreamvoice.speech.admin")
  @CommandDescription("Toggle speech recognition & keyword detection debug mode for yourself")
  private void toggleDebug(final @NotNull CommandSender sender) {
    if (this.speechService == null) {
      sender.sendMessage(Component.text("[Speech] VoiceSpeechService unavailable.", NamedTextColor.RED));
      return;
    }

    if (sender instanceof Player player) {
      final var enabled = this.speechService.toggleDebug(player.getUniqueId());
      if (enabled) {
        player.sendMessage(Component.text("[Speech Debug] Mode activated! You will receive notifications when keywords are spoken.", NamedTextColor.GREEN));
      } else {
        player.sendMessage(Component.text("[Speech Debug] Mode deactivated.", NamedTextColor.YELLOW));
      }
    } else {
      final var enabled = this.speechService.toggleGlobalDebug();
      sender.sendMessage(Component.text("[Speech Debug] Global console debug mode " + (enabled ? "enabled" : "disabled") + ".", enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
    }
  }

  @CommandMethod("voice speech debug <player>")
  @CommandPermission("dreamvoice.speech.admin")
  @CommandDescription("Toggle speech recognition & keyword detection debug mode for a target player")
  private void togglePlayerDebug(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target
  ) {
    if (this.speechService == null) {
      sender.sendMessage(Component.text("[Speech] VoiceSpeechService unavailable.", NamedTextColor.RED));
      return;
    }

    final var enabled = this.speechService.toggleDebug(target.getUniqueId());
    sender.sendMessage(
      Component.text("[Speech Debug] Debug mode ", NamedTextColor.GRAY)
        .append(Component.text(enabled ? "enabled" : "disabled", enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
        .append(Component.text(" for ", NamedTextColor.GRAY))
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
    );
    if (enabled) {
      target.sendMessage(Component.text("[Speech Debug] Mode activated by " + sender.getName() + "! You will receive notifications when keywords are spoken.", NamedTextColor.GREEN));
    } else {
      target.sendMessage(Component.text("[Speech Debug] Mode deactivated.", NamedTextColor.YELLOW));
    }
  }

  @CommandMethod("voice speech keywords")
  @CommandPermission("dreamvoice.speech.admin")
  @CommandDescription("List all active keyword spotting definitions")
  private void listKeywords(final @NotNull CommandSender sender) {
    if (this.speechService == null) {
      sender.sendMessage(Component.text("[Speech] VoiceSpeechService unavailable.", NamedTextColor.RED));
      return;
    }

    final var keywords = this.speechService.getRegisteredKeywords();
    sender.sendMessage(Component.text("--- Registered Voice Keywords (" + keywords.size() + ") ---", NamedTextColor.GOLD));
    if (keywords.isEmpty()) {
      sender.sendMessage(Component.text("No keywords registered in modules/speech/keywords.json.", NamedTextColor.GRAY));
      return;
    }

    for (final var k : keywords) {
      sender.sendMessage(
        Component.text(" - ", NamedTextColor.GRAY)
          .append(Component.text(k.id(), NamedTextColor.YELLOW))
          .append(Component.text(": ", NamedTextColor.GRAY))
          .append(Component.text(String.join(", ", k.words()), NamedTextColor.WHITE))
          .append(k.permission() != null && !k.permission().isBlank() ? Component.text(" [perm: " + k.permission() + "]", NamedTextColor.DARK_GRAY) : Component.empty())
      );
    }
  }

}
