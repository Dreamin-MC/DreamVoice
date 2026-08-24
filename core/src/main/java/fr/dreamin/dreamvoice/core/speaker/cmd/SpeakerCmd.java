package fr.dreamin.dreamvoice.core.speaker.cmd;

import cloud.commandframework.annotations.CommandDescription;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import fr.dreamin.dreamapi.api.cmd.DreamCmd;
import fr.dreamin.dreaminvoice.api.speaker.model.Speaker;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@DreamCmd
public final class SpeakerCmd {

  // ###############################################################
  // ----------------------- COMMANDS METHODS ----------------------
  // ###############################################################

  @CommandDescription("Add Speaker")
  @CommandMethod("speaker add")
  @CommandPermission("dreamvoice.speaker.add")
  private void addSpeaker(CommandSender sender) {
    if (!(sender instanceof Player player)) return;

    Speaker.builder()
      .name("Test")
      .location(player.getLocation())
      .distance(15F)
      .build();

  }

}
