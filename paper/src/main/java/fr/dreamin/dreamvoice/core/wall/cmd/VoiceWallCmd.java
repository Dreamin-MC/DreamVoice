package fr.dreamin.dreamvoice.core.wall.cmd;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

public final class VoiceWallCmd {

  private final @Nullable VoiceWallService wallService =
    DreamVoice.getService(VoiceWallService.class);

  // ###############################################################
  // ----------------------- COMMANDS METHODS ----------------------
  // ###############################################################

  @CommandDescription("Change VoiceWall occlusion mode")
  @CommandMethod("voicewall mode <mode>")
  @CommandPermission("dreamvoice.wall.manage")
  private void setMode(
    final @NotNull CommandSender sender,
    @Argument(value = "mode", suggestions = "wall_modes") final @NotNull String modeStr
  ) {
    final var wallService = requireWallService(sender);
    if (wallService == null)
      return;

    final VoiceWallMode mode;
    switch (modeStr.toLowerCase()) {
      case "strict", "strict_block" -> mode = VoiceWallMode.STRICT_BLOCK;
      case "realistic" -> mode = VoiceWallMode.REALISTIC;
      case "off", "disabled" -> mode = VoiceWallMode.OFF;
      default -> {
        sender.sendMessage(Component.translatable("wall.unknown_mode"));
        return;
      }
    }

    wallService.setMode(mode);
    sender.sendMessage(Component.translatable("wall.mode_set", Component.text(mode.name())));
  }

  @CommandDescription("Toggle VoiceWall system on/off")
  @CommandMethod("voicewall toggle")
  @CommandPermission("dreamvoice.wall.manage")
  private void toggle(final @NotNull CommandSender sender) {
    final var wallService = requireWallService(sender);
    if (wallService == null)
      return;

    final var newEnable = !wallService.isEnable();
    wallService.setEnable(newEnable);
    sender.sendMessage(Component.translatable("wall.toggle", Component.text(newEnable ? "ENABLED (Mode " + wallService.getMode() + ")" : "DISABLED")));
  }

  @CommandDescription("Toggle air damping high-frequency loss over distance")
  @CommandMethod("voicewall airdamping <enabled>")
  @CommandPermission("dreamvoice.wall.manage")
  private void setAirDamping(
    final @NotNull CommandSender sender,
    @Argument("enabled") final boolean enabled
  ) {
    final var wallService = requireWallService(sender);
    if (wallService == null)
      return;

    wallService.setAirDampingEnabled(enabled);
    sender.sendMessage(Component.translatable("wall.airdamping", Component.text(enabled ? "ENABLED" : "DISABLED")));
  }

  @CommandDescription("Toggle visual particle debugging & Action Bar for a player")
  @CommandMethod("voicewall debug [player]")
  @CommandPermission("dreamvoice.wall.manage")
  private void toggleVisualDebug(
    final @NotNull CommandSender sender,
    @Argument("player") final @Nullable Player targetPlayer
  ) {
    final var wallService = requireWallService(sender);
    if (wallService == null)
      return;

    final var player = (targetPlayer != null) ? targetPlayer : (sender instanceof Player p ? p : null);
    if (player == null) {
      sender.sendMessage(Component.translatable("wall.specify_player"));
      return;
    }

    final var active = wallService.toggleDebugPlayer(player.getUniqueId());
    sender.sendMessage(Component.translatable("wall.debug", Component.text(player.getName()), Component.text(active ? "ENABLED" : "DISABLED")));
  }

  @CommandDescription("Show VoiceWall status and settings")
  @CommandMethod("voicewall info")
  @CommandPermission("dreamvoice.wall.manage")
  private void showInfo(final @NotNull CommandSender sender) {
    final var wallService = requireWallService(sender);
    if (wallService == null)
      return;

    final var codexService = DreamVoice.getService(CodexService.class);
    if (codexService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return;
    }

    final var codex = codexService.getConfig();

    sender.sendMessage(Component.translatable("wall.info_header"));
    sender.sendMessage(Component.translatable("wall.info_active", Component.text(String.valueOf(wallService.isEnable()))));
    sender.sendMessage(Component.translatable("wall.info_mode", Component.text(wallService.getMode().name())));
    sender.sendMessage(Component.translatable("wall.info_distance", Component.text(codex.getEffectiveDistance())));
    sender.sendMessage(Component.translatable("wall.info_airdamping", Component.text(String.valueOf(wallService.isAirDampingEnabled()))));
  }

  @Suggestions("wall_modes")
  public List<String> suggModes(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    return Stream.of("strict", "realistic", "off")
      .filter(s -> s.startsWith(in.toLowerCase()))
      .toList();
  }

  // ###############################################################
  // ----------------------- PRIVATE METHODS -----------------------
  // ###############################################################

  private @Nullable VoiceWallService requireWallService(final @NotNull CommandSender sender) {
    if (this.wallService == null) {
      sender.sendMessage(Component.translatable("common.service_unavailable"));
      return null;
    }
    return this.wallService;
  }


}
