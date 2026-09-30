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
public final class FabricDebugCmd {

  @Command("debug|voicedebug|debugvoice status")
  @CommandDescription("Display general debug information for DreamVoice")
  @Permission("dreamvoice.admin.debug")
  public void status(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();

    FabricCommandUtils.sendSuccess(source, "debug.status_header");
    FabricCommandUtils.sendSuccess(source, "debug.platform", "Fabric");
    FabricCommandUtils.sendSuccess(source, "debug.players_online", source.getServer().getPlayerCount());
  }

  @Command("debug|voicedebug|debugvoice raycast")
  @CommandDescription("Test acoustic raycast from your current position")
  @Permission("dreamvoice.admin.debug")
  public void raycast(final @NotNull CommandSourceStack source) {
    if (!source.isPlayer()) {
      FabricCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var p = source.getPlayer();
    if (p == null) {
      FabricCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var pos = p.blockPosition();
    FabricCommandUtils.sendSuccess(source, "debug.raycast_origin", pos.getX(), pos.getY(), pos.getZ());
  }
}
