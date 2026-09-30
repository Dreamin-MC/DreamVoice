package fr.dreamin.dreamvoice.fabric.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.fabric.command.FabricCommandUtils;
import fr.dreamin.dreamvoice.fabric.command.annotation.DreamCmd;
import fr.dreamin.dreamvoice.fabric.lang.LanguageServerManager;
import net.minecraft.commands.CommandSourceStack;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@DreamCmd
public final class FabricDreamVoiceCmd {

  private static final List<String> MODULES = List.of(
    "all", "config", "lang", "speakers", "wiretaps", "projections", "radios", "transmitters", "broadcasts"
  );

  @Suggestions("dreamvoice_modules")
  public List<String> suggestModules(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    return MODULES.stream()
      .filter(m -> m.toLowerCase().startsWith(input.toLowerCase()))
      .toList();
  }

  @Command("dreamvoice status")
  @CommandDescription("Show DreamVoice global status")
  @Permission("dreamvoice.admin.status")
  public void status(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();

    final var speakerCount = api.getSpeakerService() != null ? api.getSpeakerService().getSpeakers().size() : 0;
    final var wiretapCount = api.getWiretapService() != null ? api.getWiretapService().getWiretaps().size() : 0;
    final var projectionCount = api.getProjectionService() != null ? api.getProjectionService().getProjections().size() : 0;
    final var radioCount = api.getRadioService() != null ? api.getRadioService().getChannels().size() : 0;
    final var transmitterCount = api.getTransmitterService() != null ? api.getTransmitterService().getTransmitters().size() : 0;
    final var broadcastCount = api.getBroadcastService() != null ? api.getBroadcastService().getBroadcastPoints().size() : 0;

    FabricCommandUtils.sendSuccess(source, "dreamvoice.status_header");
    FabricCommandUtils.sendSuccess(source, "dreamvoice.status_speakers", speakerCount);
    FabricCommandUtils.sendSuccess(source, "dreamvoice.status_wiretaps", wiretapCount);
    FabricCommandUtils.sendSuccess(source, "dreamvoice.status_projections", projectionCount);
    FabricCommandUtils.sendSuccess(source, "dreamvoice.status_radios", radioCount);
    FabricCommandUtils.sendSuccess(source, "dreamvoice.status_transmitters", transmitterCount);
    FabricCommandUtils.sendSuccess(source, "dreamvoice.status_broadcasts", broadcastCount);
  }

  @Command("dreamvoice save [module]")
  @CommandDescription("Save all or specific DreamVoice data module")
  @Permission("dreamvoice.admin.save")
  public void save(
    final @NotNull CommandSourceStack source,
    @Argument(value = "module", suggestions = "dreamvoice_modules") @Default("all") final @NotNull String module
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getPersistenceService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }
    api.getPersistenceService().saveAll();
    FabricCommandUtils.sendSuccess(source, "persistence.all_saved");
  }

  @Command("dreamvoice load [module]")
  @CommandDescription("Load all or specific DreamVoice data module")
  @Permission("dreamvoice.admin.load")
  public void load(
    final @NotNull CommandSourceStack source,
    @Argument(value = "module", suggestions = "dreamvoice_modules") @Default("all") final @NotNull String module
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getPersistenceService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }
    if ("lang".equalsIgnoreCase(module)) {
      LanguageServerManager.reload();
      FabricCommandUtils.sendSuccess(source, "persistence.config_reloaded");
      return;
    }
    api.getPersistenceService().loadAll();
    FabricCommandUtils.sendSuccess(source, "persistence.all_reloaded");
  }

  @Command("dreamvoice reload [module]")
  @CommandDescription("Reload all or specific DreamVoice data module")
  @Permission("dreamvoice.admin.reload")
  public void reload(
    final @NotNull CommandSourceStack source,
    @Argument(value = "module", suggestions = "dreamvoice_modules") @Default("all") final @NotNull String module
  ) {
    LanguageServerManager.reload();
    final var api = DreamVoiceAPI.get();
    if (api == null || api.getPersistenceService() == null) {
      FabricCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }
    api.getPersistenceService().loadAll();
    FabricCommandUtils.sendSuccess(source, "persistence.all_reloaded");
  }
}
