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
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class VoiceWallCmd {

  private final @Nullable VoiceWallService wallService =
    DreamVoice.getService(VoiceWallService.class);

  private @Nullable VoiceWallService requireWallService(final @NotNull CommandSender sender) {
    if (this.wallService == null) {
      sender.sendMessage(Component.text("[VOICEWALL] Service VoiceWall indisponible.", NamedTextColor.RED));
      return null;
    }
    return this.wallService;
  }

  @Suggestions("wall_modes")
  public List<String> suggModes(final @NotNull CommandContext<CommandSender> ctx, final @NotNull String in) {
    return List.of("strict", "realistic", "off").stream()
      .filter(s -> s.startsWith(in.toLowerCase()))
      .toList();
  }

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
        sender.sendMessage(Component.text("[VOICEWALL] Mode inconnu. Choix: strict, realistic, off", NamedTextColor.RED));
        return;
      }
    }

    wallService.setMode(mode);
    sender.sendMessage(
      Component.text("[VOICEWALL] Mode d'occlusion des murs défini sur : ", NamedTextColor.GREEN)
        .append(Component.text(mode.name(), NamedTextColor.YELLOW))
        .append(Component.text(mode == VoiceWallMode.STRICT_BLOCK ? " (Murs 100% étanches / Son coupé)" : "", NamedTextColor.AQUA))
    );
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
    sender.sendMessage(
      Component.text("[VOICEWALL] Système d'occlusion des murs : ", NamedTextColor.GREEN)
        .append(Component.text(newEnable ? "ACTIVÉ (Mode " + wallService.getMode() + ")" : "DÉSACTIVÉ", newEnable ? NamedTextColor.YELLOW : NamedTextColor.RED))
    );
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
    sender.sendMessage(
      Component.text("[VOICEWALL] Amortissement de l'air : ", NamedTextColor.GREEN)
        .append(Component.text(enabled ? "ACTIVÉ" : "DÉSACTIVÉ", enabled ? NamedTextColor.YELLOW : NamedTextColor.RED))
    );
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
      sender.sendMessage(Component.text("[VOICEWALL] Précisez un joueur pour activer le debug visuel !", NamedTextColor.RED));
      return;
    }

    final var active = wallService.toggleDebugPlayer(player);
    sender.sendMessage(
      Component.text("[VOICEWALL] Debug visuel par particules pour ", NamedTextColor.GREEN)
        .append(Component.text(player.getName(), NamedTextColor.YELLOW))
        .append(Component.text(" : ", NamedTextColor.GREEN))
        .append(Component.text(active ? "ACTIVÉ 🟢 (Particules Vertes=Libre, Jaunes=Contourné, Rouges=Bloqué)" : "DÉSACTIVÉ ⚪", active ? NamedTextColor.GREEN : NamedTextColor.RED))
    );
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
      sender.sendMessage(Component.text("[VOICEWALL] Service de configuration indisponible.", NamedTextColor.RED));
      return;
    }

    final var codex = codexService.getConfig();
    final var diff = codex.getVoiceWall() != null ? codex.getVoiceWall().getDiffractionConfig() : null;

    sender.sendMessage(Component.text("==== [VOICEWALL SETTINGS] ====", NamedTextColor.GOLD));
    sender.sendMessage(Component.text("Actif: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(wallService.isEnable()), wallService.isEnable() ? NamedTextColor.GREEN : NamedTextColor.RED)));
    sender.sendMessage(Component.text("Mode actuel: ", NamedTextColor.GRAY).append(Component.text(wallService.getMode().name(), NamedTextColor.YELLOW)));
    sender.sendMessage(Component.text("Amortissement Air: ", NamedTextColor.GRAY).append(Component.text(String.valueOf(wallService.isAirDampingEnabled()), NamedTextColor.AQUA)));
    sender.sendMessage(Component.text("Diffraction / Contournement: ", NamedTextColor.GRAY).append(Component.text(diff != null && diff.enabled() ? "OUI (Bypass=" + diff.maxBypassWidth() + "m, Path=" + diff.maxPathDistance() + "m)" : "NON", NamedTextColor.YELLOW)));
  }

}
