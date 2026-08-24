package fr.dreamin.dreamvoice.core.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.player.model.PlayerState;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.api.voice.model.VoiceSoundBuilder;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
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

public final class DebugCmd {

  private final @NotNull VoiceService voiceService =
    DreamVoice.getService(VoiceService.class);
  private final @NotNull PlayerService playerService =
    DreamVoice.getService(PlayerService.class);

  private static final Executor AUDIO_EXEC =
    Executors.newFixedThreadPool(2);

  // ------------------------------------------------------------
  // Suggestions
  // ------------------------------------------------------------

  @Suggestions("player_state")
  public List<String> suggState(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    return List.of("alive", "dead", "spec");
  }

  // ------------------------------------------------------------
  // Utils
  // ------------------------------------------------------------

  private Runnable stopped(final @NotNull CommandSender sender, final @NotNull String label) {
    return () -> sender.sendMessage(
      Component.text("[SVC] fini: ", NamedTextColor.GRAY)
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

    final var duration = ms == null ? 2000 : ms;
    final var raw = RawUtils.generateBeep(freq, duration);

    this.voiceService.playSound(
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

    final var duration = ms == null ? 2000 : ms;
    final var dist = distance == null ? 16f : distance;

    final var raw = RawUtils.generateBeep(freq, duration);
    final var loc = player.getLocation().clone().add(0, 1.6, 0);

    this.voiceService.playSound(
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

    sender.sendMessage(
      Component.text("[SVC] Download + convert…", NamedTextColor.GRAY)
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
          () -> this.voiceService.playSound(
            VoiceSoundBuilder.builder()
              .rawAudioData(raw)
              .onStopped(stopped(sender, "url-global"))
              .build()
          )
        )
      )
      .exceptionally(ex -> {
        sender.sendMessage(
          Component.text("[SVC] Erreur: ", NamedTextColor.RED)
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

    final var dist = distance == null ? 16f : distance;
    final var loc = player.getLocation().clone().add(0, 1.6, 0);

    sender.sendMessage(
      Component.text("[SVC] Download + convert…", NamedTextColor.GRAY)
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
          () -> this.voiceService.playSound(
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
          Component.text("[SVC] Erreur: ", NamedTextColor.RED)
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

    final var state = parseState(raw);
    if (state == null) {
      sender.sendMessage(
        Component.text("[SVC] Etat invalide", NamedTextColor.RED)
      );
      return;
    }

    this.playerService.setState(state, player.getUniqueId());
    sender.sendMessage(
      Component.text("[SVC] Etat vocal défini sur ", NamedTextColor.GREEN)
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
    final var state = parseState(raw);
    if (state == null) {
      sender.sendMessage(
        Component.text("[SVC] Etat invalide", NamedTextColor.RED)
      );
      return;
    }

    this.playerService.setState(state, target.getUniqueId());
    sender.sendMessage(
      Component.text("[SVC] Etat de ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(" défini sur ", NamedTextColor.GREEN))
        .append(Component.text(state.name(), NamedTextColor.YELLOW))
    );
  }

  @CommandMethod("voice state reset <player>")
  @CommandPermission("dreamvoice.cmd.debug")
  private void resetState(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target
  ) {
    this.playerService.setState(PlayerState.ALIVE, target.getUniqueId());
    sender.sendMessage(
      Component.text("[SVC] Etat vocal reset pour ", NamedTextColor.GREEN)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
    );
  }

  @CommandMethod("voice state get <player>")
  @CommandPermission("dreamvoice.cmd.debug")
  private void getState(
    final @NotNull CommandSender sender,
    @Argument("player") final @NotNull Player target
  ) {
    final var vPlayer = this.playerService.getPlayer(target);
    final var state = vPlayer != null ? vPlayer.getState() : PlayerState.ALIVE;

    sender.sendMessage(
      Component.text("[SVC] Etat vocal de ", NamedTextColor.GRAY)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(" : ", NamedTextColor.GRAY))
        .append(Component.text(state.name(), NamedTextColor.AQUA))
    );
  }

  // ###############################################################
  // ----------------------- RELOAD COMMAND ------------------------
  // ###############################################################

  @CommandMethod("voice reload")
  @CommandPermission("dreamvoice.cmd.debug")
  private void reloadConfig(final @NotNull CommandSender sender) {
    try {
      DreamVoice.getService(CodexService.class).load();
      sender.sendMessage(
        Component.text("[SVC] Configuration rechargée avec succès !", NamedTextColor.GREEN)
      );
    } catch (Exception e) {
      sender.sendMessage(
        Component.text("[SVC] Erreur lors du rechargement de la config: ", NamedTextColor.RED)
          .append(Component.text(e.getMessage() != null ? e.getMessage() : "Inconnue", NamedTextColor.GRAY))
      );
    }
  }


}


