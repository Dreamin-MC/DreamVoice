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
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DreamVoiceCmd {

  private final @Nullable VoicePersistenceService persistenceService =
    DreamVoice.getService(VoicePersistenceService.class);

  private @Nullable VoicePersistenceService requirePersistenceService(final @NotNull CommandSender sender) {
    if (this.persistenceService == null) {
      sender.sendMessage(Component.text("[DreamVoice] Persistence service unavailable.", NamedTextColor.RED));
      return null;
    }
    return this.persistenceService;
  }

  // ------------------------------------------------------------
  // Suggestions
  // ------------------------------------------------------------

  @Suggestions("save_modules")
  public List<String> suggModules(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    return List.of("all", "config", "data", "speakers", "wiretaps", "projections", "radios", "transmitters").stream()
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

    sender.sendMessage(Component.text("==== [ DREAMVOICE SYSTEM STATUS ] ====", NamedTextColor.GOLD));
    sender.sendMessage(Component.text(" - VoiceWall: ", NamedTextColor.GRAY).append(Component.text(wallStatus, wallService != null && wallService.isEnable() ? NamedTextColor.GREEN : NamedTextColor.RED)));
    sender.sendMessage(Component.text(" - Active speakers: ", NamedTextColor.GRAY).append(Component.text(spkCount, NamedTextColor.YELLOW)));
    sender.sendMessage(Component.text(" - Active wiretaps: ", NamedTextColor.GRAY).append(Component.text(wtCount, NamedTextColor.YELLOW)));
    sender.sendMessage(Component.text(" - Voice projections: ", NamedTextColor.GRAY).append(Component.text(projCount, NamedTextColor.YELLOW)));
    sender.sendMessage(Component.text(" - Active radio channels: ", NamedTextColor.GRAY).append(Component.text(radioCount, NamedTextColor.YELLOW)));
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
        sender.sendMessage(Component.text("[DreamVoice] Speakers successfully saved!", NamedTextColor.GREEN));
      }
      case "wiretaps" -> {
        service.saveWiretaps();
        sender.sendMessage(Component.text("[DreamVoice] Wiretaps successfully saved!", NamedTextColor.GREEN));
      }
      case "projections" -> {
        service.saveProjections();
        sender.sendMessage(Component.text("[DreamVoice] Projections successfully saved!", NamedTextColor.GREEN));
      }
      case "radios" -> {
        service.saveRadios();
        sender.sendMessage(Component.text("[DreamVoice] Radio channels successfully saved!", NamedTextColor.GREEN));
      }
      case "transmitters" -> {
        service.saveTransmitters();
        sender.sendMessage(Component.text("[DreamVoice] Transmitters successfully saved!", NamedTextColor.GREEN));
      }
      case "all", "data" -> {
        service.saveAll();
        sender.sendMessage(Component.text("[DreamVoice] All data successfully saved!", NamedTextColor.GREEN));
      }
      default -> sender.sendMessage(Component.text("[DreamVoice] Unknown module: " + target + " (all, speakers, wiretaps, projections, radios, transmitters)", NamedTextColor.RED));
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
        sender.sendMessage(Component.text("[DreamVoice] Speakers successfully reloaded!", NamedTextColor.GREEN));
      }
      case "wiretaps" -> {
        service.loadWiretaps();
        sender.sendMessage(Component.text("[DreamVoice] Wiretaps successfully reloaded!", NamedTextColor.GREEN));
      }
      case "projections" -> {
        service.loadProjections();
        sender.sendMessage(Component.text("[DreamVoice] Projections successfully reloaded!", NamedTextColor.GREEN));
      }
      case "radios" -> {
        service.loadRadios();
        sender.sendMessage(Component.text("[DreamVoice] Radio channels successfully reloaded!", NamedTextColor.GREEN));
      }
      case "transmitters" -> {
        service.loadTransmitters();
        sender.sendMessage(Component.text("[DreamVoice] Transmitters successfully reloaded!", NamedTextColor.GREEN));
      }
      case "all", "data" -> {
        service.loadAll();
        sender.sendMessage(Component.text("[DreamVoice] All data successfully reloaded!", NamedTextColor.GREEN));
      }
      default -> sender.sendMessage(Component.text("[DreamVoice] Unknown module: " + target + " (all, speakers, wiretaps, projections, radios, transmitters)", NamedTextColor.RED));
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
          sender.sendMessage(Component.text("[DreamVoice] Configuration file reloaded!", NamedTextColor.GREEN));
        } catch (Exception e) {
          sender.sendMessage(Component.text("[DreamVoice] Failed to reload config: " + e.getMessage(), NamedTextColor.RED));
        }
      }
    }

    if (!target.equals("config"))
      loadData(sender, target);
  }

}
