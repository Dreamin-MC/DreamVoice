package fr.dreamin.dreamvoice.core.cmd;

import cloud.commandframework.annotations.*;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreaminvoice.api.player.model.PlayerState;
import fr.dreamin.dreaminvoice.api.player.service.PlayerService;
import fr.dreamin.dreaminvoice.api.voice.model.VoiceSoundBuilder;
import fr.dreamin.dreaminvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.utils.RawUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.*;

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
  public List<String> suggState(CommandContext<CommandSender> ctx, String in) {
    return List.of("alive", "dead", "spec");
  }

  // ------------------------------------------------------------
  // Utils
  // ------------------------------------------------------------

  private Runnable stopped(CommandSender sender, String label) {
    return () -> sender.sendMessage(
      Component.text("[SVC] fini: ", NamedTextColor.GRAY)
        .append(Component.text(label, NamedTextColor.YELLOW))
    );
  }

  private PlayerState parseState(String raw) {
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
    CommandSender sender,
    @Argument("freq") int freq,
    @Argument("ms") Integer ms
  ) {
    if (!(sender instanceof Player)) return;

    int duration = ms == null ? 2000 : ms;
    var raw = RawUtils.generateBeep(freq, duration);

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
    CommandSender sender,
    @Argument("freq") int freq,
    @Argument("ms") Integer ms,
    @Argument("distance") Float distance
  ) {
    if (!(sender instanceof Player player)) return;

    int duration = ms == null ? 2000 : ms;
    float dist = distance == null ? 16f : distance;

    var raw = RawUtils.generateBeep(freq, duration);
    var loc = player.getLocation().clone().add(0, 1.6, 0);

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
    CommandSender sender,
    @Argument("url") String url
  ) {
    if (!(sender instanceof Player)) return;

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
    CommandSender sender,
    @Argument("url") String url,
    @Argument("distance") Float distance
  ) {
    if (!(sender instanceof Player player)) return;

    float dist = distance == null ? 16f : distance;
    var loc = player.getLocation().clone().add(0, 1.6, 0);

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
    CommandSender sender,
    @Argument(value = "state", suggestions = "player_state") String raw
  ) {
    if (!(sender instanceof Player player)) return;

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
    CommandSender sender,
    @Argument("player") Player target,
    @Argument(value = "state", suggestions = "player_state") String raw
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
    CommandSender sender,
    @Argument("player") Player target
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
    CommandSender sender,
    @Argument("player") Player target
  ) {
    final var state =
      this.playerService.isState(target.getUniqueId(), PlayerState.DEAD)
        ? PlayerState.DEAD
        : PlayerState.ALIVE;

    sender.sendMessage(
      Component.text("[SVC] Etat vocal de ", NamedTextColor.GRAY)
        .append(Component.text(target.getName(), NamedTextColor.YELLOW))
        .append(Component.text(" : ", NamedTextColor.GRAY))
        .append(Component.text(state.name(), NamedTextColor.AQUA))
    );
  }
}
