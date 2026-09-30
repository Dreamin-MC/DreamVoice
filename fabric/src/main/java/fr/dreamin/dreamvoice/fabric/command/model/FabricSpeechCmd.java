package fr.dreamin.dreamvoice.fabric.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.fabric.command.FabricCommandUtils;
import fr.dreamin.dreamvoice.fabric.command.annotation.DreamCmd;
import net.minecraft.commands.CommandSourceStack;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.NotNull;

@DreamCmd
public final class FabricSpeechCmd {

  @Command("speech|voicespeech status")
  @CommandDescription("Check speech-to-text service status")
  @Permission("dreamvoice.admin.speech.status")
  public void status(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeechService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var svc = api.getSpeechService();
    FabricCommandUtils.sendSuccess(source, "speech.status_header");
    FabricCommandUtils.sendSuccess(source, "speech.active_model", svc.getActiveModel());
    FabricCommandUtils.sendSuccess(source, "speech.installed_models", svc.getInstalledModels().size());
  }

  @Command("speech|voicespeech keywords")
  @CommandDescription("List registered speech recognition keywords")
  @Permission("dreamvoice.admin.speech.keywords")
  public void keywords(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getSpeechService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var keywords = api.getSpeechService().getRegisteredKeywords();
    FabricCommandUtils.sendSuccess(source, "speech.keywords_header", keywords.size());
    for (final var kw : keywords)
      FabricCommandUtils.sendSuccess(source, "speech.keywords_entry", kw.id(), String.join(", ", kw.words()));
  }
}
