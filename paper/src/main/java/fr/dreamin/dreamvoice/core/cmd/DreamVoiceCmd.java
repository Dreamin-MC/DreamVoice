package fr.dreamin.dreamvoice.core.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.persistence.service.VoicePersistenceService;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

public final class DreamVoiceCmd {

  private final @Nullable VoicePersistenceService persistenceService =
    DreamVoice.getService(VoicePersistenceService.class);

  private @Nullable VoicePersistenceService requirePersistenceService(final @NotNull CommandSender sender) {
    if (this.persistenceService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return null;
    }
    return this.persistenceService;
  }

  // ------------------------------------------------------------
  // Suggestions
  // ------------------------------------------------------------

  @Suggestions("save_modules")
  public List<String> suggModules(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    return Stream.of("all", "config", "data", "speakers", "wiretaps", "projections", "radios", "transmitters", "broadcasts")
      .filter(m -> m.startsWith(in.toLowerCase()))
      .toList();
  }

  // ###############################################################
  // ----------------------- STATUS / INFO -------------------------
  // ###############################################################

  @CommandDescription("Show DreamVoice global status")
  @CommandMethod("dreamvoice status")
  @CommandPermission("dreamvoice.admin.status")
  private void status(final @NotNull CommandSender sender) {
    final var speakerService = DreamVoice.getService(VoiceSpeakerService.class);
    final var wiretapService = DreamVoice.getService(VoiceWiretapService.class);
    final var projectionService = DreamVoice.getService(VoiceProjectionService.class);
    final var radioService = DreamVoice.getService(VoiceRadioService.class);
    final var wallService = DreamVoice.getService(VoiceWallService.class);

    final var spkCount = speakerService != null ? speakerService.getSpeakers().size() : 0;
    final var wtCount = wiretapService != null ? wiretapService.getWiretaps().size() : 0;
    final var projCount = projectionService != null ? projectionService.getProjections().size() : 0;
    final var radioCount = radioService != null ? radioService.getChannels().size() : 0;
    final var wallStatus = wallService != null && wallService.isEnable() ? "ENABLED (" + wallService.getMode().name() + ")" : "DISABLED";

    sender.sendMessage(Component.translatable("dreamvoice.status_header"));
    sender.sendMessage(Component.translatable("dreamvoice.status_voicewall", Component.text(wallStatus)));
    sender.sendMessage(Component.translatable("dreamvoice.status_speakers", Component.text(spkCount)));
    sender.sendMessage(Component.translatable("dreamvoice.status_wiretaps", Component.text(wtCount)));
    sender.sendMessage(Component.translatable("dreamvoice.status_projections", Component.text(projCount)));
    sender.sendMessage(Component.translatable("dreamvoice.status_radios", Component.text(radioCount)));
  }

  // ###############################################################
  // ----------------------- SAVE COMMANDS -------------------------
  // ###############################################################

  @CommandDescription("Save DreamVoice persistent data")
  @CommandMethod("dreamvoice save [module]")
  @CommandPermission("dreamvoice.admin.save")
  private void saveData(
    final @NotNull CommandSender sender,
    @Argument(value = "module", suggestions = "save_modules") final @Nullable String module
  ) {
    final var service = requirePersistenceService(sender);
    if (service == null)
      return;

    final var target = module != null ? module.toLowerCase() : "all";

    switch (target) {
      case "speakers" -> {
        service.saveSpeakers();
        sender.sendMessage(Component.translatable("persistence.speakers_saved"));
      }
      case "wiretaps" -> {
        service.saveWiretaps();
        sender.sendMessage(Component.translatable("persistence.wiretaps_saved"));
      }
      case "projections" -> {
        service.saveProjections();
        sender.sendMessage(Component.translatable("persistence.projections_saved"));
      }
      case "radios" -> {
        service.saveRadios();
        sender.sendMessage(Component.translatable("persistence.radios_saved"));
      }
      case "transmitters" -> {
        service.saveTransmitters();
        sender.sendMessage(Component.translatable("persistence.transmitters_saved"));
      }
      case "broadcasts" -> {
        service.saveBroadcasts();
        sender.sendMessage(Component.translatable("persistence.broadcasts_saved"));
      }
      case "all", "data" -> {
        service.saveAll();
        sender.sendMessage(Component.translatable("persistence.all_saved"));
      }
      default -> sender.sendMessage(Component.translatable("persistence.unknown_module", Component.text(target)));
    }
  }

  // ###############################################################
  // ----------------------- LOAD COMMANDS -------------------------
  // ###############################################################

  @CommandDescription("Load DreamVoice persistent data")
  @CommandMethod("dreamvoice load [module]")
  @CommandPermission("dreamvoice.admin.load")
  private void loadData(
    final @NotNull CommandSender sender,
    @Argument(value = "module", suggestions = "save_modules") final @Nullable String module
  ) {
    final var service = requirePersistenceService(sender);
    if (service == null)
      return;

    final var target = module != null ? module.toLowerCase() : "all";

    switch (target) {
      case "speakers" -> {
        service.loadSpeakers();
        sender.sendMessage(Component.translatable("persistence.speakers_reloaded"));
      }
      case "wiretaps" -> {
        service.loadWiretaps();
        sender.sendMessage(Component.translatable("persistence.wiretaps_reloaded"));
      }
      case "projections" -> {
        service.loadProjections();
        sender.sendMessage(Component.translatable("persistence.projections_reloaded"));
      }
      case "radios" -> {
        service.loadRadios();
        sender.sendMessage(Component.translatable("persistence.radios_reloaded"));
      }
      case "transmitters" -> {
        service.loadTransmitters();
        sender.sendMessage(Component.translatable("persistence.transmitters_reloaded"));
      }
      case "broadcasts" -> {
        service.loadBroadcasts();
        sender.sendMessage(Component.translatable("persistence.broadcasts_reloaded"));
      }
      case "all", "data" -> {
        service.loadAll();
        sender.sendMessage(Component.translatable("persistence.all_reloaded"));
      }
      default -> sender.sendMessage(Component.translatable("persistence.unknown_module", Component.text(target)));
    }
  }

  // ###############################################################
  // ----------------------- RELOAD COMMANDS -----------------------
  // ###############################################################

  @CommandDescription("Reload DreamVoice configuration and/or persistent data")
  @CommandMethod("dreamvoice reload [module]")
  @CommandPermission("dreamvoice.admin.reload")
  private void reloadData(
    final @NotNull CommandSender sender,
    @Argument(value = "module", suggestions = "save_modules") final @Nullable String module
  ) {
    final var target = module != null ? module.toLowerCase() : "all";

    if (target.equals("config") || target.equals("all")) {
      final var codexService = DreamVoice.getService(CodexService.class);
      if (codexService != null) {
        try {
          codexService.load();
          sender.sendMessage(Component.translatable("persistence.config_reloaded"));
        } catch (Exception e) {
          sender.sendMessage(Component.translatable("record.failed", Component.text("Failed to reload config: " + e.getMessage())));
        }
      }
    }

    if (!target.equals("config"))
      loadData(sender, target);
  }

}
