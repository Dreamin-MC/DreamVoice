package fr.dreamin.dreamvoice.core.config.cmd;

import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.config.model.ConfigTarget;
import fr.dreamin.dreamvoice.core.config.tool.VoiceConfigTool;
import fr.dreamin.dreamvoice.core.config.ui.VoiceConfigMainGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class VoiceConfigCommand {

  @CommandDescription("Open DreamVoice configuration menu or receive configuration tool")
  @CommandMethod("voice config")
  @CommandPermission("dreamvoice.admin")
  private void openConfig(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var target = ConfigTarget.resolve(player);
    VoiceConfigTool.give(player);
    new VoiceConfigMainGUI(target).open(player);
  }

  @CommandDescription("Get the DreamVoice configuration tool")
  @CommandMethod("voice tool")
  @CommandPermission("dreamvoice.admin")
  private void giveTool(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    VoiceConfigTool.give(player);
  }

  @CommandDescription("Toggle visual particle spheres and device ranges")
  @CommandMethod("voice visualizer")
  @CommandPermission("dreamvoice.admin")
  private void toggleVisualizer(final @NotNull CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.translatable("common.player_only"));
      return;
    }

    final var vis = DreamVoice.getInstance().getVisualizerService();
    if (vis != null) {
      final var active = vis.toggle(player.getUniqueId());
      player.sendMessage(Component.translatable(active ? "config.visualizer.enabled" : "config.visualizer.disabled"));
    }
  }

}
