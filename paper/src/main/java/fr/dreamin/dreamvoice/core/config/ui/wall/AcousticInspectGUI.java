package fr.dreamin.dreamvoice.core.config.ui.wall;

import fr.dreamin.dreamapi.api.gui.model.GuiInterface;
import fr.dreamin.dreamapi.core.gui.item.GuiItems;
import fr.dreamin.dreamapi.core.item.builder.ItemBuilder;
import fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.config.model.ConfigTarget;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.List;

public final class AcousticInspectGUI extends GuiInterface {

  private final @NotNull ConfigTarget target;
  private final @NotNull GuiInterface parentGui;

  public AcousticInspectGUI(final @NotNull ConfigTarget target, final @NotNull GuiInterface parentGui) {
    this.target = target;
    this.parentGui = parentGui;
  }

  @Override
  public Component name(final @NotNull Player player) {
    return Component.translatable("config.gui.wall.title");
  }

  @Override
  public Gui guiUpper(final @NotNull Player player) {
    final var wallService = DreamVoice.getService(VoiceWallService.class);
    final var border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(Component.empty()).toGuiItem();

    final var isWallEnabled = wallService != null && wallService.isEnable();
    final var wallMode = wallService != null ? wallService.getMode() : VoiceWallMode.REALISTIC;
    final var isAirDamping = wallService != null && wallService.isAirDampingEnabled();
    final var isDebugging = wallService != null && wallService.hasDebugPlayer(player.getUniqueId());

    final var toggleEngineItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        final var mat = isWallEnabled ? Material.BEACON : Material.IRON_BARS;

        return new ItemBuilder(mat)
          .setName(isWallEnabled ? Component.translatable("config.gui.wall.engine_enabled") : Component.translatable("config.gui.wall.engine_disabled"))
          .setLore(List.of(
            Component.translatable("config.gui.wall.engine_lore1"),
            Component.translatable("config.gui.wall.engine_lore2")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (wallService != null) {
          wallService.setEnable(!isWallEnabled);
          p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, isWallEnabled ? 0.8f : 1.2f);
          AcousticInspectGUI.this.open(p);
        }
      }
    };

    final var modeItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.COMPARATOR)
          .setName(Component.translatable("config.gui.wall.mode", Component.text(wallMode.name())))
          .setLore(List.of(
            Component.translatable("config.gui.wall.mode_lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (wallService != null) {
          final var modes = VoiceWallMode.values();
          final var nextMode = modes[(wallMode.ordinal() + 1) % modes.length];
          wallService.setMode(nextMode);
          p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
          AcousticInspectGUI.this.open(p);
        }
      }
    };

    final var particleDebugItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        final var mat = isDebugging ? Material.BLAZE_POWDER : Material.GUNPOWDER;

        return new ItemBuilder(mat)
          .setName(isDebugging ? Component.translatable("config.gui.wall.particles_active") : Component.translatable("config.gui.wall.particles_off"))
          .setLore(List.of(
            Component.translatable("config.gui.wall.particles_lore1"),
            Component.translatable("config.gui.wall.particles_lore2")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (wallService != null) {
          wallService.toggleDebugPlayer(p.getUniqueId());
          p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, isDebugging ? 0.9f : 1.4f);
          AcousticInspectGUI.this.open(p);
        }
      }
    };

    final var airDampingItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        final var mat = isAirDamping ? Material.FEATHER : Material.STRING;

        return new ItemBuilder(mat)
          .setName(isAirDamping ? Component.translatable("config.gui.wall.air_active") : Component.translatable("config.gui.wall.air_off"))
          .setLore(List.of(
            Component.translatable("config.gui.wall.air_lore1"),
            Component.translatable("config.gui.wall.air_lore2")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (wallService != null) {
          wallService.setAirDampingEnabled(!isAirDamping);
          p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
          AcousticInspectGUI.this.open(p);
        }
      }
    };

    final var inspectTargetItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        final var mat = target.block() != null ? target.block().getType() : Material.BRICKS;
        final var desc = target.describe();

        return new ItemBuilder(mat.isItem() ? mat : Material.BRICKS)
          .setName(Component.translatable("config.gui.wall.target_name"))
          .setLore(List.of(
            Component.translatable("config.gui.wall.target_lore1", Component.text(desc)),
            Component.translatable("config.gui.wall.target_lore2", Component.text(target.location().getWorld() != null ? target.location().getWorld().getName() : "unknown"))
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {}
    };

    return Gui.builder()
      .setStructure(
        "# # # # # # # # #",
        "# E . M . P . D #",
        "# # # # I # # # #",
        ". . . . B . . . ."
      )
      .addIngredient('#', border)
      .addIngredient('E', toggleEngineItem)
      .addIngredient('M', modeItem)
      .addIngredient('P', particleDebugItem)
      .addIngredient('D', airDampingItem)
      .addIngredient('I', inspectTargetItem)
      .addIngredient('B', GuiItems.BACK(parentGui))
      .build();
  }

}
