package fr.dreamin.dreamvoice.core.config.ui.speaker;

import fr.dreamin.dreamapi.api.gui.model.GuiInterface;
import fr.dreamin.dreamapi.core.gui.item.GuiItems;
import fr.dreamin.dreamapi.core.item.builder.ItemBuilder;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import fr.dreamin.dreamvoice.api.speaker.model.SpeakerMode;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.config.model.ConfigTarget;
import fr.dreamin.dreamvoice.core.utils.LocationUtils;
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

public final class SpeakerEditGUI extends GuiInterface {

  private final @NotNull Speaker speaker;
  private final @NotNull ConfigTarget target;
  private final @NotNull GuiInterface parentGui;

  public SpeakerEditGUI(final @NotNull Speaker speaker, final @NotNull ConfigTarget target, final @NotNull GuiInterface parentGui) {
    this.speaker = speaker;
    this.target = target;
    this.parentGui = parentGui;
  }

  @Override
  public Component name(final @NotNull Player player) {
    return Component.translatable("config.gui.speaker.edit_title", Component.text(speaker.getName()));
  }

  @Override
  public void open(final @NotNull Player player) {
    final var vis = DreamVoice.getInstance().getVisualizerService();
    if (vis != null)
      vis.setFocus(player.getUniqueId(), speaker);
    super.open(player);
  }

  @Override
  public Gui guiUpper(final @NotNull Player player) {
    final var speakerService = DreamVoice.getService(VoiceSpeakerService.class);
    final var border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(Component.empty()).toGuiItem();

    final var rangeItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.BEACON)
          .setName(Component.translatable("config.gui.speaker.edit.range", Component.text(speaker.getDistance())))
          .setLore(List.of(
            Component.translatable("config.gui.speaker.edit.range_lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {}
    };

    final var minusRange = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.RED_DYE)
          .setName(Component.translatable("config.gui.speaker.edit.minus_range"))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        speaker.updateDistance(Math.max(1.0f, speaker.getDistance() - 5.0f));
        if (speakerService != null)
          speakerService.save();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.9f);
        SpeakerEditGUI.this.open(p);
      }
    };

    final var plusRange = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.LIME_DYE)
          .setName(Component.translatable("config.gui.speaker.edit.plus_range"))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        speaker.updateDistance(speaker.getDistance() + 5.0f);
        if (speakerService != null)
          speakerService.save();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        SpeakerEditGUI.this.open(p);
      }
    };

    final var modeItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.REPEATER)
          .setName(Component.translatable("config.gui.speaker.edit.mode", Component.text(speaker.getMode().name())))
          .setLore(List.of(
            Component.translatable("config.gui.speaker.edit.mode_lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        final var newMode = speaker.getMode() == SpeakerMode.GLOBAL ? SpeakerMode.RESTRICTED : SpeakerMode.GLOBAL;
        speaker.setMode(newMode);
        if (speakerService != null)
          speakerService.save();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
        SpeakerEditGUI.this.open(p);
      }
    };

    final var teleportItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.ENDER_PEARL)
          .setName(Component.translatable("config.gui.speaker.edit.teleport"))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        final var loc = LocationUtils.toLocation(speaker.getLocation());
        if (loc != null)
          p.teleport(loc);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
      }
    };

    final var deleteItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.BARRIER)
          .setName(Component.translatable("config.gui.speaker.edit.delete"))
          .setLore(List.of(
            Component.translatable("config.gui.speaker.edit.delete_lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (speakerService != null) {
          speakerService.unregister(speaker);
          speakerService.save();
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
        p.sendMessage(Component.translatable("speaker.removed", Component.text(speaker.getName())));
        parentGui.open(p);
      }
    };

    return Gui.builder()
      .setStructure(
        "# # # # # # # # #",
        "# - R + . M . T #",
        "# # # # D # # # #",
        ". . . . B . . . ."
      )
      .addIngredient('#', border)
      .addIngredient('-', minusRange)
      .addIngredient('R', rangeItem)
      .addIngredient('+', plusRange)
      .addIngredient('M', modeItem)
      .addIngredient('T', teleportItem)
      .addIngredient('D', deleteItem)
      .addIngredient('B', GuiItems.BACK(parentGui))
      .build();
  }

}
