package fr.dreamin.dreamvoice.core.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.player.model.PlayerState;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.api.voice.model.VoiceSoundBuilder;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.utils.RawUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public final class DebugCmd {

  private final @Nullable VoiceService voiceService =
    DreamVoice.getService(VoiceService.class);
  private final @Nullable PlayerService playerService =
    DreamVoice.getService(PlayerService.class);

  private static final Executor AUDIO_EXEC =
    Executors.newFixedThreadPool(2);

  private @Nullable VoiceService requireVoiceService(final @NotNull CommandSender sender) {
    if (this.voiceService == null) {
      sender.sendMessage(Component.text("[SVC] Voice service unavailable.", NamedTextColor.RED));
      return null;
    }
    return this.voiceService;
  }

  private @Nullable PlayerService requirePlayerService(final @NotNull CommandSender sender) {
    if (this.playerService == null) {
      sender.sendMessage(Component.text("[SVC] Player service unavailable.", NamedTextColor.RED));
      return null;
    }
    return this.playerService;
  }

  // ------------------------------------------------------------
  // Suggestions
  // ------------------------------------------------------------

  @Suggestions("player_state")
  public List<String> suggState(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    return List.of("alive", "dead", "spec");
  }

  @Suggestions("voice_filters")
  public List<String> suggFilters(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    if (filterService == null)
      return List.of();

    return filterService.getAvailableFilters().stream()
      .map(VoiceFilter::getId)
      .filter(id -> id.startsWith(in.toLowerCase()))
      .sorted()
      .collect(Collectors.toList());
  }


  // ------------------------------------------------------------
  // Utils
  // ------------------------------------------------------------

  private Runnable stopped(final @NotNull CommandSender sender, final @NotNull String label) {
    return () -> sender.sendMessage(
      Component.text("[SVC] Finished: ", NamedTextColor.GRAY)
        .append(Component.text(label, NamedTextColor.YELLOW))
    );
  }

  private PlayerState parseState(final @NotNull String raw) {
    return switch (raw.toLowerCase()) {
      case "alive" -> PlayerState.ALIVE;
      case "dead" -> PlayerState.DEAD;
      case "spec", "spectate", "spectator" -> PlayerState.SPECTATE;
      default -> null;
    };
  }

  // ###############################################################
  // ----------------------- PLAY SOUND ----------------------------
  // ###############################################################

  @CommandMethod("voice playsound beep-global <freq> [ms]")
  @CommandPermission("dreamvoice.cmd.debug")
  private void beepGlobal(
    final @NotNull CommandSender sender,
    @Argument("freq") final int freq,
    @Argument("ms") final @Nullable Integer ms
  ) {
    if (!(sender instanceof Player))
      return;

    final var voiceService = requireVoiceService(sender);
    if (voiceService == null)
      return;

    final var duration = ms == null ? 2000 : ms;
    final var raw = RawUtils.generateBeep(freq, duration);

    voiceService.playSound(
      VoiceSoundBuilder.builder()
        .rawAudioData(raw)
        .onStopped(stopped(sender, "beep-global"))
        .build()
    );
  }

  @CommandMethod("voice playsound beep-loc <freq> [ms] [distance]")
  @CommandPermission("dreamvoice.cmd.debug")
  private void beepLoc(
    final @NotNull CommandSender sender,
    @Argument("freq") final int freq,
    @Argument("ms") final @Nullable Integer ms,
    @Argument("distance") final @Nullable Float distance
  ) {
    if (!(sender instanceof Player player))
      return;

    final var voiceService = requireVoiceService(sender);
    if (voiceService == null)
      return;

    final var duration = ms == null ? 2000 : ms;
    final var dist = distance == null ? 16f : distance;

    final var raw = RawUtils.generateBeep(freq, duration);
    final var loc = player.getLocation().clone().add(0, 1.6, 0);

    voiceService.playSound(
      VoiceSoundBuilder.builder()
        .rawAudioData(raw)
        .location(loc)
        .distance(dist)
        .onStopped(stopped(sender, "beep-loc"))
        .build()
    );
  }

  // ------------------------------------------------------------
  // URL → GLOBAL
  // ------------------------------------------------------------

  @CommandMethod("voice playsound url-global <url>")
  @CommandPermission("dreamvoice.cmd.debug")
  private void urlGlobal(
    final @NotNull CommandSender sender,
    @Argument("url") final @NotNull String url
  ) {
    if (!(sender instanceof Player))
      return;

    final var voiceService = requireVoiceService(sender);
    if (voiceService == null)
      return;

    sender.sendMessage(
      Component.text("[SVC] Downloading and converting audio...", NamedTextColor.GRAY)
    );

    CompletableFuture
      .supplyAsync(() -> {
        try {
          return RawUtils.urlToPcm48Hz(url);
        } catch (Exception e) {
          throw new CompletionException(e);
        }
      }, AUDIO_EXEC)
      .thenAccept(raw ->
        Bukkit.getScheduler().runTask(
          DreamVoice.getInstance(),
          () -> voiceService.playSound(
            VoiceSoundBuilder.builder()
              .rawAudioData(raw)
              .onStopped(stopped(sender, "url-global"))
              .build()
          )
        )
      )
      .exceptionally(ex -> {
        sender.sendMessage(
          Component.text("[SVC] Error: ", NamedTextColor.RED)
            .append(Component.text(ex.getCause().getMessage(), NamedTextColor.GRAY))
        );
        return null;
      });
  }

  // ------------------------------------------------------------
  // URL → LOC
  // ------------------------------------------------------------

  @CommandMethod("voice playsound url-loc <url> [distance]")
  @CommandPermission("dreamvoice.cmd.debug")
  private void urlLoc(
    final @NotNull CommandSender sender,
    @Argument("url") final @NotNull String url,
    @Argument("distance") final @Nullable Float distance
  ) {
    if (!(sender instanceof Player player))
      return;

    final var voiceService = requireVoiceService(sender);
    if (voiceService == null)
      return;

    final var dist = distance == null ? 16f : distance;
    final var loc = player.getLocation().clone().add(0, 1.6, 0);

    sender.sendMessage(
      Component.text("[SVC] Downloading and converting audio...", NamedTextColor.GRAY)
    );

    CompletableFuture
      .supplyAsync(() -> {
        try {
          return RawUtils.urlToPcm48Hz(url);
        } catch (Exception e) {
          throw new CompletionException(e);
        }
      }, AUDIO_EXEC)
      .thenAccept(raw ->
        Bukkit.getScheduler().runTask(
          DreamVoice.getInstance(),
          () -> voiceService.playSound(
            VoiceSoundBuilder.builder()
              .rawAudioData(raw)
              .location(loc)
              .distance(dist)
              .onStopped(stopped(sender, "url-loc"))
              .build()
          )
        )
      )
      .exceptionally(ex -> {
        sender.sendMessage(
          Component.text("[SVC] Error: ", NamedTextColor.RED)
            .append(Component.text(ex.getCause().getMessage(), NamedTextColor.GRAY))
        );
        return null;
      });
  }

  // ###############################################################
  // ----------------------- STATE COMMANDS ------------------------
  // ###############################################################

  @CommandMethod("voice state self <state>")
  @CommandPermission("dreamvoice.cmd.debug")
  private void setSelfState(
    final @NotNull CommandSender sender,
    @Argument(value = "state", suggestions = "player_state") final @NotNull String raw
  ) {
    if (!(sender instanceof Player player))
      return;

    final var playerService = requirePlayerService(sender);
    if (playerService == null)
      return;

    final var state = parseState(raw);
    if (state == null) {
      sender.sendMessage(
        Component.text("[SVC] Invalid state.", NamedTextColor.RED)
      );
      return;
    }

    playerService.setState(state, player.getUniqueId());
    sender.sendMessage(
      Component.text("[SVC] Voice state set to ", NamedTextColor.GREEN)
        .append(Component.text(state.name(), NamedTextColor.YELLOW))
    );
  }

  @CommandMethod("voice state player <player> <state>")
  @CommandPermission("dreamvoice.cmd.debug")
  private void setPlayerState(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target,
    @Argument(value = "state", suggestions = "player_state") final @NotNull String raw
  ) {
    final var playerService = requirePlayerService(sender);
    if (playerService == null)
      return;

    final var state = parseState(raw);
    if (state == null) {
      sender.sendMessage(
        Component.text("[SVC] Invalid state.", NamedTextColor.RED)
      );
      return;
    }

    playerService.setState(state, target.getUniqueId());
    sender.sendMessage(
      Component.text("[SVC] Voice state of ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(" set to ", NamedTextColor.GREEN))
        .append(Component.text(state.name(), NamedTextColor.YELLOW))
    );
  }

  @CommandMethod("voice state reset <player>")
  @CommandPermission("dreamvoice.cmd.debug")
  private void resetState(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target
  ) {
    final var playerService = requirePlayerService(sender);
    if (playerService == null)
      return;

    playerService.setState(PlayerState.ALIVE, target.getUniqueId());
    sender.sendMessage(
      Component.text("[SVC] Voice state reset for ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
    );
  }

  @CommandMethod("voice state get <player>")
  @CommandPermission("dreamvoice.cmd.debug")
  private void getState(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target
  ) {
    final var playerService = requirePlayerService(sender);
    if (playerService == null)
      return;

    final var vPlayer = playerService.getPlayer(target);
    final var state = vPlayer != null ? vPlayer.getState() : PlayerState.ALIVE;

    sender.sendMessage(
      Component.text("[SVC] Voice state of ", NamedTextColor.GRAY)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(": ", NamedTextColor.GRAY))
        .append(Component.text(state.name(), NamedTextColor.AQUA))
    );
  }

  // ###############################################################
  // ----------------------- RELOAD COMMAND ------------------------
  // ###############################################################

  @CommandMethod("voice reload")
  @CommandPermission("dreamvoice.cmd.debug")
  private void reloadConfig(final @NotNull CommandSender sender) {
    final var codexService = DreamVoice.getService(CodexService.class);
    if (codexService == null) {
      sender.sendMessage(Component.text("[SVC] Configuration service unavailable.", NamedTextColor.RED));
      return;
    }

    try {
      codexService.load();
      sender.sendMessage(
        Component.text("[SVC] Configuration successfully reloaded!", NamedTextColor.GREEN)
      );
    } catch (Exception e) {
      sender.sendMessage(
        Component.text("[SVC] Error while reloading config: ", NamedTextColor.RED)
          .append(Component.text(e.getMessage() != null ? e.getMessage() : "Unknown", NamedTextColor.GRAY))
      );
    }
  }

  // ###############################################################
  // ----------------------- FILTER COMMANDS -----------------------
  // ###############################################################

  @CommandMethod("voice filter list")
  @CommandPermission("dreamvoice.cmd.debug")
  private void listFilters(final @NotNull CommandSender sender) {
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    if (filterService == null) {
      sender.sendMessage(Component.text("[SVC] Filter service unavailable.", NamedTextColor.RED));
      return;
    }

    final var filters = filterService.getAvailableFilters();
    sender.sendMessage(
      Component.text("[SVC] Available voice filters (", NamedTextColor.GRAY)
        .append(Component.text(filters.size(), NamedTextColor.YELLOW))
        .append(Component.text("):", NamedTextColor.GRAY))
    );

    for (final var filter : filters) {
      sender.sendMessage(
        Component.text(" - ", NamedTextColor.GRAY)
          .append(Component.text(filter.getId(), NamedTextColor.AQUA))
          .append(Component.text(" (" + filter.getName() + ", prio=" + filter.getPriority() + ")", NamedTextColor.GRAY))
      );
    }
  }

  @CommandMethod("voice filter set <player> <filter>")
  @CommandPermission("dreamvoice.cmd.debug")
  private void setFilter(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target,
    @Argument(value = "filter", suggestions = "voice_filters") final @NotNull String filterId
  ) {
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    if (filterService == null) {
      sender.sendMessage(Component.text("[SVC] Filter service unavailable.", NamedTextColor.RED));
      return;
    }

    if (filterService.getFilter(filterId) == null) {
      sender.sendMessage(Component.text("[SVC] Unknown filter: " + filterId, NamedTextColor.RED));
      return;
    }

    filterService.addFilter(target.getUniqueId(), filterId);
    sender.sendMessage(
      Component.text("[SVC] Filter ", NamedTextColor.GREEN)
        .append(Component.text(filterId, NamedTextColor.YELLOW))
        .append(Component.text(" applied to ", NamedTextColor.GREEN))
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
    );
  }

  @CommandMethod("voice filter remove <player> <filter>")
  @CommandPermission("dreamvoice.cmd.debug")
  private void removeFilter(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target,
    @Argument(value = "filter", suggestions = "voice_filters") final @NotNull String filterId
  ) {
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    if (filterService == null) {
      sender.sendMessage(Component.text("[SVC] Filter service unavailable.", NamedTextColor.RED));
      return;
    }

    filterService.removeFilter(target.getUniqueId(), filterId);
    sender.sendMessage(
      Component.text("[SVC] Filter ", NamedTextColor.YELLOW)
        .append(Component.text(filterId, NamedTextColor.YELLOW))
        .append(Component.text(" removed from ", NamedTextColor.GREEN))
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
    );
  }

  @CommandMethod("voice filter clear <player>")
  @CommandPermission("dreamvoice.cmd.debug")
  private void clearFilters(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target
  ) {
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    if (filterService == null) {
      sender.sendMessage(Component.text("[SVC] Filter service unavailable.", NamedTextColor.RED));
      return;
    }

    filterService.clearFilters(target.getUniqueId());
    sender.sendMessage(
      Component.text("[SVC] All voice filters cleared for ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
    );
  }

  @CommandMethod("voice filter auto <player> <enabled>")
  @CommandPermission("dreamvoice.cmd.debug")
  private void setAutoEnvironment(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target,
    @Argument("enabled") final boolean enabled
  ) {
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    if (filterService == null) {
      sender.sendMessage(Component.text("[SVC] Filter service unavailable.", NamedTextColor.RED));
      return;
    }

    filterService.setAutoEnvironmentEnabled(target.getUniqueId(), enabled);
    sender.sendMessage(
      Component.text("[SVC] Automatic environment filters ", NamedTextColor.GREEN)
        .append(Component.text(enabled ? "enabled" : "disabled", enabled ? NamedTextColor.YELLOW : NamedTextColor.RED))
        .append(Component.text(" for ", NamedTextColor.GREEN))
        .append(Component.text(target.getName(), NamedTextColor.AQUA))
    );
  }

  // ###############################################################
  // ----------------------- AIR DAMPING ---------------------------
  // ###############################################################

  @CommandMethod("voice airdamping <enabled>")
  @CommandPermission("dreamvoice.cmd.debug")
  private void setAirDamping(
    final @NotNull CommandSender sender,
    @Argument("enabled") final boolean enabled
  ) {
    final var wallService = DreamVoice.getService(VoiceWallService.class);
    if (wallService == null) {
      sender.sendMessage(Component.text("[SVC] VoiceWall service unavailable.", NamedTextColor.RED));
      return;
    }

    wallService.setAirDampingEnabled(enabled);
    sender.sendMessage(
      Component.text("[SVC] Air damping over distance: ", NamedTextColor.GREEN)
        .append(Component.text(enabled ? "ENABLED" : "DISABLED", enabled ? NamedTextColor.YELLOW : NamedTextColor.RED))
    );
  }

}
