package fr.dreamin.dreamvoice.neoforge.command.model;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.neoforge.command.NeoForgeCommandUtils;
import fr.dreamin.dreamvoice.neoforge.command.annotation.DreamCmd;
import net.minecraft.commands.CommandSourceStack;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.NotNull;

@DreamCmd
public final class NeoForgeDebugCmd {

  @Command("voice debug status")
  @Command("debug|voicedebug|debugvoice status")
  @CommandDescription("Display general debug information for DreamVoice")
  @Permission("dreamvoice.admin.debug")
  public void status(final @NotNull CommandSourceStack source) {
    final var api = DreamVoiceAPI.get();

    NeoForgeCommandUtils.sendSuccess(source, "debug.status_header");
    NeoForgeCommandUtils.sendSuccess(source, "debug.platform", "Fabric");
    NeoForgeCommandUtils.sendSuccess(source, "debug.players_online", source.getServer().getPlayerCount());
  }

  @Command("voice debug raycast")
  @Command("debug|voicedebug|debugvoice raycast")
  @CommandDescription("Test acoustic raycast from your current position")
  @Permission("dreamvoice.admin.debug")
  public void raycast(final @NotNull CommandSourceStack source) {
    if (!source.isPlayer()) {
      NeoForgeCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var p = source.getPlayer();
    if (p == null) {
      NeoForgeCommandUtils.sendFailure(source, "common.player_only");
      return;
    }

    final var pos = p.blockPosition();
    NeoForgeCommandUtils.sendSuccess(source, "debug.raycast_origin", pos.getX(), pos.getY(), pos.getZ());
  }
}

