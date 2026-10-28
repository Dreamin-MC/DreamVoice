package fr.dreamin.dreamvoice.neoforge.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.neoforge.command.NeoForgeCommandUtils;
import fr.dreamin.dreamvoice.neoforge.command.annotation.DreamCmd;
import net.minecraft.commands.CommandSourceStack;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.NotNull;

@DreamCmd
public final class NeoForgeSpeechCmd {

  @Command("voice speech status")
  @Command("speech|voicespeech status")
  @CommandDescription("Check speech-to-text service status")
  @Permission("dreamvoice.admin.speech.status")
  public void status(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeechService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var svc = api.getSpeechService();
    NeoForgeCommandUtils.sendSuccess(source, "speech.status_header");
    NeoForgeCommandUtils.sendSuccess(source, "speech.active_model", svc.getActiveModel());
    NeoForgeCommandUtils.sendSuccess(source, "speech.installed_models", svc.getInstalledModels().size());
  }

  @Command("voice speech keywords")
  @Command("speech|voicespeech keywords")
  @CommandDescription("List registered speech recognition keywords")
  @Permission("dreamvoice.admin.speech.keywords")
  public void keywords(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeechService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var keywords = api.getSpeechService().getRegisteredKeywords();
    NeoForgeCommandUtils.sendSuccess(source, "speech.keywords_header", keywords.size());
    for (final var kw : keywords)
      NeoForgeCommandUtils.sendSuccess(source, "speech.keywords_entry", kw.id(), String.join(", ", kw.words()));
  }
}

