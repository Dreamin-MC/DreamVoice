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
import fr.dreamin.dreamvoice.common.speech.model.VoskModelRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import fr.dreamin.dreamvoice.core.speech.util.SpeechBookHelper;

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
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return;
    }

    final var modelInfo = VoskModelRegistry.findByLangOrId(lang);
    if (modelInfo == null) {
      sender.sendMessage(Component.translatable("speech.unknown_model", Component.text(lang)));
      return;
    }

    sender.sendMessage(Component.translatable("speech.downloading", Component.text(modelInfo.displayName()), Component.text(modelInfo.sizeMb())));

    this.speechService.downloadModel(lang, progress -> {
      final var pct = (int) (progress * 100);
      if (pct % 25 == 0)
        sender.sendMessage(Component.translatable("speech.download_progress", Component.text(pct)));

    }).thenAccept(folder -> Bukkit.getScheduler().runTask(DreamVoice.getInstance(), () -> sender.sendMessage(
      Component.translatable("speech.model_installed", Component.text(folder.getName()))
    ))).exceptionally(err -> {
      Bukkit.getScheduler().runTask(DreamVoice.getInstance(), () ->
        sender.sendMessage(Component.translatable("record.failed", Component.text(err.getMessage())))
      );
      return null;
    });
  }

  @CommandMethod("voice speech models")
  @CommandPermission("dreamvoice.speech.admin")
  @CommandDescription("List all installed speech models")
  private void listModels(final @NotNull CommandSender sender) {
    if (this.speechService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return;
    }

    final var installed = this.speechService.getInstalledModels();
    final var active = this.speechService.getActiveModel();

    sender.sendMessage(Component.translatable("speech.status_header"));
    sender.sendMessage(Component.translatable("speech.active_model", Component.text(active)));

    if (installed.isEmpty()) {
      sender.sendMessage(Component.translatable("speech.no_models"));
      return;
    }

    for (final var m : installed) {
      final var isCurrent = m.equalsIgnoreCase(active);
      sender.sendMessage(Component.translatable(isCurrent ? "speech.model_item_active" : "speech.model_item", Component.text(m)));
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
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return;
    }

    this.speechService.setActiveModel(modelName);
    sender.sendMessage(Component.translatable("speech.model_set", Component.text(modelName)));
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
      sender.sendMessage(Component.translatable("common.service_unavailable"));
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
      sender.sendMessage(Component.translatable("record.not_found", Component.text(id)));
      return;
    }

    if (rec == null) {
      sender.sendMessage(Component.translatable("record.not_found", Component.text(id)));
      return;
    }

    sender.sendMessage(Component.translatable("speech.transcribing", Component.text(id)));

    final var outputType = outputArg != null ? outputArg.toLowerCase() : "chat";

    this.speechService.transcribeRecording(rec).thenAccept(result -> Bukkit.getScheduler().runTask(DreamVoice.getInstance(), () -> {
      if ("book".equals(outputType) && sender instanceof Player player) {
        final var book = SpeechBookHelper.createReportBook(player, result);
        player.getInventory().addItem(book);
        player.sendMessage(Component.translatable("speech.transcription_book_generated"));
      } else {
        sender.sendMessage(Component.translatable("speech.report_header", Component.text(id)));
        if (result.segments().isEmpty()) {
          sender.sendMessage(Component.translatable("speech.no_words_detected"));
        } else {
          for (final var seg : result.segments()) {
            sender.sendMessage(Component.translatable("speech.segment_item",
              Component.text(String.format("%.1f", seg.offsetSeconds())),
              Component.text(seg.speakerName()),
              Component.text(seg.text())
            ));
          }
        }
      }
    })).exceptionally(err -> {
      Bukkit.getScheduler().runTask(DreamVoice.getInstance(), () -> {
        sender.sendMessage(Component.translatable("record.failed", Component.text(err.getMessage())));
      });
      return null;
    });
  }

  @CommandMethod("voice speech reload")
  @CommandPermission("dreamvoice.speech.admin")
  @CommandDescription("Reload speech configuration and keyword definitions")
  private void reloadSpeech(final @NotNull CommandSender sender) {
    if (this.speechService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return;
    }

    this.speechService.reload();
    sender.sendMessage(Component.translatable("speech.reloaded"));
  }

  @CommandMethod("voice speech debug")
  @CommandPermission("dreamvoice.speech.admin")
  @CommandDescription("Toggle speech recognition & keyword detection debug mode for yourself")
  private void toggleDebug(final @NotNull CommandSender sender) {
    if (this.speechService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return;
    }

    if (sender instanceof Player player) {
      final var enabled = this.speechService.toggleDebug(player.getUniqueId());
      if (enabled)
        player.sendMessage(Component.translatable("speech.debug_activated"));
      else
        player.sendMessage(Component.translatable("speech.debug_deactivated"));
    } else {
      final var enabled = this.speechService.toggleGlobalDebug();
      sender.sendMessage(Component.translatable(enabled ? "speech.debug_activated" : "speech.debug_deactivated"));
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
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return;
    }

    final var enabled = this.speechService.toggleDebug(target.getUniqueId());
    sender.sendMessage(Component.translatable(enabled ? "speech.debug_activated" : "speech.debug_deactivated"));
    if (enabled)
      target.sendMessage(Component.translatable("speech.debug_activated"));
    else
      target.sendMessage(Component.translatable("speech.debug_deactivated"));
  }

  @CommandMethod("voice speech keywords")
  @CommandPermission("dreamvoice.speech.admin")
  @CommandDescription("List all active keyword spotting definitions")
  private void listKeywords(final @NotNull CommandSender sender) {
    if (this.speechService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return;
    }

    final var keywords = this.speechService.getRegisteredKeywords();
    sender.sendMessage(Component.translatable("speech.keywords_header", Component.text(keywords.size())));
    if (keywords.isEmpty()) {
      sender.sendMessage(Component.translatable("speech.no_keywords"));
      return;
    }

    for (final var k : keywords) {
      sender.sendMessage(Component.translatable("speech.keywords_entry",
        Component.text(k.id()),
        Component.text(String.join(", ", k.words()))
      ));
    }
  }

}
