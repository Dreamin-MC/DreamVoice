package fr.dreamin.dreamvoice.neoforge.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode;
import fr.dreamin.dreamvoice.neoforge.command.NeoForgeCommandUtils;
import fr.dreamin.dreamvoice.neoforge.command.annotation.DreamCmd;
import net.minecraft.commands.CommandSourceStack;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.context.CommandContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@DreamCmd
public final class NeoForgeWallCmd {

  private static final List<String> WALL_MODES = List.of("STRICT_BLOCK", "REALISTIC", "OFF");

  @Suggestions("wall_modes")
  public List<String> suggestWallModes(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    return WALL_MODES.stream()
      .filter(m -> m.toLowerCase().startsWith(input.toLowerCase()))
      .toList();
  }

  @Suggestions("players")
  public List<String> suggestPlayers(final @NotNull CommandContext<CommandSourceStack> ctx, final @NotNull String input) {
    return NeoForgeCommandUtils.suggestPlayers(ctx.sender(), input);
  }

  @Command("voice wall info")
  @Command("wall|voicewall info")
  @CommandDescription("Show voice wall occlusion configuration")
  @Permission("dreamvoice.admin.wall.info")
  public void info(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getWallService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var svc = api.getWallService();
    NeoForgeCommandUtils.sendSuccess(source, "wall.info_header");
    NeoForgeCommandUtils.sendSuccess(source, "wall.info_active", svc.isEnable());
    NeoForgeCommandUtils.sendSuccess(source, "wall.info_mode", svc.getMode().name());
    NeoForgeCommandUtils.sendSuccess(source, "wall.info_distance", 50);
    NeoForgeCommandUtils.sendSuccess(source, "wall.info_airdamping", svc.isAirDampingEnabled());
  }

  @Command("voice wall toggle")
  @Command("wall|voicewall toggle")
  @CommandDescription("Toggle voice wall occlusion calculation")
  @Permission("dreamvoice.admin.wall.toggle")
  public void toggle(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();
    if (api.getWallService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var wallService = api.getWallService();
    final var newState = !wallService.isEnable();
    wallService.setEnable(newState);
    NeoForgeCommandUtils.sendSuccess(source, "wall.toggle", newState ? "ENABLED (Mode " + wallService.getMode() + ")" : "DISABLED");
  }

  @Command("voice wall mode <mode>")
  @Command("wall|voicewall mode <mode>")
  @CommandDescription("Set voice wall occlusion mode")
  @Permission("dreamvoice.admin.wall.mode")
  public void setMode(
    final @NotNull CommandSourceStack source,
    @Argument(value = "mode", suggestions = "wall_modes") final @NotNull String modeStr
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getWallService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    try {
      final var mode = VoiceWallMode.valueOf(modeStr.toUpperCase());
      api.getWallService().setMode(mode);
      NeoForgeCommandUtils.sendSuccess(source, "wall.mode_set", mode.name());
    } catch (final IllegalArgumentException e) {
      NeoForgeCommandUtils.sendFailure(source, "wall.unknown_mode");
    }
  }

  @Command("voice wall airdamping <airDamping>")
  @Command("wall|voicewall airdamping <airDamping>")
  @CommandDescription("Toggle or set air damping for wall occlusion")
  @Permission("dreamvoice.admin.wall.airdamping")
  public void setAirDamping(
    final @NotNull CommandSourceStack source,
    @Argument("airDamping") final boolean airDamping
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getWallService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    api.getWallService().setAirDampingEnabled(airDamping);
    NeoForgeCommandUtils.sendSuccess(source, "wall.airdamping", airDamping ? "ENABLED" : "DISABLED");
  }

  @Command("voice wall debug [target]")
  @Command("wall|voicewall debug [target]")
  @CommandDescription("Toggle wall occlusion raycast debugging for a player")
  @Permission("dreamvoice.admin.wall.debug")
  public void debug(
    final @NotNull CommandSourceStack source,
    @Argument(value = "target", suggestions = "players") final @Nullable String targetName
  ) {
    final var api = DreamVoiceAPI.get();
    if (api.getWallService() == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.service_unavailable");
      return;
    }

    final var player = NeoForgeCommandUtils.resolvePlayer(source, targetName);
    if (player == null) {
      NeoForgeCommandUtils.sendFailure(source, "wall.specify_player");
      return;
    }

    final var wallService = api.getWallService();
    final var debugEnabled = wallService.toggleDebugPlayer(player.getUUID());
    NeoForgeCommandUtils.sendSuccess(source, "wall.debug", player.getName().getString(), debugEnabled ? "ENABLED" : "DISABLED");
  }
}

