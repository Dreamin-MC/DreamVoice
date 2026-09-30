package fr.dreamin.dreamvoice.core.config.ui;

import fr.dreamin.dreamapi.api.gui.model.GuiInterface;
import fr.dreamin.dreamapi.core.item.builder.ItemBuilder;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.config.model.ConfigTarget;
import fr.dreamin.dreamvoice.core.config.ui.broadcast.BroadcastListGUI;
import fr.dreamin.dreamvoice.core.config.ui.projection.ProjectionListGUI;
import fr.dreamin.dreamvoice.core.config.ui.radio.RadioListGUI;
import fr.dreamin.dreamvoice.core.config.ui.room.RoomListGUI;
import fr.dreamin.dreamvoice.core.config.ui.speaker.SpeakerListGUI;
import fr.dreamin.dreamvoice.core.config.ui.transmitter.TransmitterListGUI;
import fr.dreamin.dreamvoice.core.config.ui.wall.AcousticInspectGUI;
import fr.dreamin.dreamvoice.core.config.ui.wiretap.WiretapListGUI;
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

public final class VoiceConfigMainGUI extends GuiInterface {

  private @NotNull ConfigTarget target;

  public VoiceConfigMainGUI(final @NotNull ConfigTarget target) {
    this.target = target;
  }

  @Override
  public Component name(final @NotNull Player player) {
    return Component.translatable("config.gui.title");
  }

  @Override
  public Gui guiUpper(final @NotNull Player player) {
    final var border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
      .setName(Component.empty())
      .toGuiItem();

    final var speakerItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.JUKEBOX)
          .setName(Component.translatable("config.gui.main.speakers.name"))
          .setLore(List.of(
            Component.translatable("config.gui.main.speakers.lore1"),
            Component.translatable("config.gui.main.speakers.lore2")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        new SpeakerListGUI(target, VoiceConfigMainGUI.this).open(p);
      }
    };

    final var broadcastItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.LIGHTNING_ROD)
          .setName(Component.translatable("config.gui.main.broadcast.name"))
          .setLore(List.of(
            Component.translatable("config.gui.main.broadcast.lore1"),
            Component.translatable("config.gui.main.broadcast.lore2")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        new BroadcastListGUI(target, VoiceConfigMainGUI.this).open(p);
      }
    };

    final var wiretapItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.SCULK_SENSOR)
          .setName(Component.translatable("config.gui.main.wiretaps.name"))
          .setLore(List.of(
            Component.translatable("config.gui.main.wiretaps.lore1"),
            Component.translatable("config.gui.main.wiretaps.lore2")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        new WiretapListGUI(target, VoiceConfigMainGUI.this).open(p);
      }
    };

    final var roomItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.STRUCTURE_BLOCK)
          .setName(Component.translatable("config.gui.main.rooms.name"))
          .setLore(List.of(
            Component.translatable("config.gui.main.rooms.lore1"),
            Component.translatable("config.gui.main.rooms.lore2")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        new RoomListGUI(VoiceConfigMainGUI.this).open(p);
      }
    };

    final var radioItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.DAYLIGHT_DETECTOR)
          .setName(Component.translatable("config.gui.main.radio.name"))
          .setLore(List.of(
            Component.translatable("config.gui.main.radio.lore1"),
            Component.translatable("config.gui.main.radio.lore2")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        new RadioListGUI(VoiceConfigMainGUI.this).open(p);
      }
    };

    final var transmitterItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.TARGET)
          .setName(Component.translatable("config.gui.main.transmitters.name"))
          .setLore(List.of(
            Component.translatable("config.gui.main.transmitters.lore1"),
            Component.translatable("config.gui.main.transmitters.lore2")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        new TransmitterListGUI(target, VoiceConfigMainGUI.this).open(p);
      }
    };

    final var projectionItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.ENDER_EYE)
          .setName(Component.translatable("config.gui.main.projections.name"))
          .setLore(List.of(
            Component.translatable("config.gui.main.projections.lore1"),
            Component.translatable("config.gui.main.projections.lore2")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        new ProjectionListGUI(VoiceConfigMainGUI.this).open(p);
      }
    };

    final var acousticItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.BRICKS)
          .setName(Component.translatable("config.gui.main.acoustic.name"))
          .setLore(List.of(
            Component.translatable("config.gui.main.acoustic.lore1"),
            Component.translatable("config.gui.main.acoustic.lore2")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        new AcousticInspectGUI(target, VoiceConfigMainGUI.this).open(p);
      }
    };

    final var targetItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.RECOVERY_COMPASS)
          .setName(Component.translatable("config.gui.main.target.name"))
          .setLore(List.of(
            Component.translatable("config.gui.main.target.lore1", Component.text(target.describe())),
            Component.translatable("config.gui.main.target.lore2")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        target = ConfigTarget.resolve(p);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.2f);
        p.sendMessage(Component.translatable("config.target_selected", Component.text(target.describe())));
        VoiceConfigMainGUI.this.open(p);
      }
    };

    final var visService = DreamVoice.getInstance().getVisualizerService();
    final var visualizerItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        final boolean active = visService != null && visService.isEnabled(p.getUniqueId());
        final var mat = active ? Material.AMETHYST_CLUSTER : Material.AMETHYST_SHARD;

        return new ItemBuilder(mat)
          .setName(Component.translatable("config.gui.main.visualizer.name"))
          .setLore(List.of(
            active ? Component.translatable("config.gui.main.visualizer.lore_active") : Component.translatable("config.gui.main.visualizer.lore_inactive"),
            Component.empty(),
            Component.translatable("config.gui.main.visualizer.lore_click")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (visService != null) {
          final boolean now = visService.toggle(p.getUniqueId());
          p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, now ? 1.4f : 0.8f);
          VoiceConfigMainGUI.this.open(p);
        }
      }
    };

    return Gui.builder()
      .setStructure(
        "# # # # # # # # #",
        "# S . B . W . R #",
        "# D . T . P . A #",
        "# # # V C # # # #"
      )
      .addIngredient('#', border)
      .addIngredient('S', speakerItem)
      .addIngredient('B', broadcastItem)
      .addIngredient('W', wiretapItem)
      .addIngredient('R', roomItem)
      .addIngredient('D', radioItem)
      .addIngredient('T', transmitterItem)
      .addIngredient('P', projectionItem)
      .addIngredient('A', acousticItem)
      .addIngredient('V', visualizerItem)
      .addIngredient('C', targetItem)
      .build();
  }

}
