package fr.dreamin.dreamvoice.core.config.ui.transmitter;

import fr.dreamin.dreamapi.api.gui.model.GuiInterface;
import fr.dreamin.dreamapi.core.gui.item.GuiItems;
import fr.dreamin.dreamapi.core.item.builder.ItemBuilder;
import fr.dreamin.dreamvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.config.model.ConfigTarget;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.Markers;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.ArrayList;
import java.util.List;

public final class TransmitterListGUI extends GuiInterface {

  private final @NotNull ConfigTarget target;
  private final @NotNull GuiInterface parentGui;

  public TransmitterListGUI(final @NotNull ConfigTarget target, final @NotNull GuiInterface parentGui) {
    this.target = target;
    this.parentGui = parentGui;
  }

  @Override
  public Component name(final @NotNull Player player) {
    return Component.translatable("config.gui.transmitter.title");
  }

  @Override
  public Gui guiUpper(final @NotNull Player player) {
    final var transmitterService = DreamVoice.getService(VoiceTransmitterService.class);
    final var isTransmitting = transmitterService != null && transmitterService.isTransmitter(player.getUniqueId());
    final var items = new ArrayList<Item>();

    if (transmitterService != null) {
      for (final var cfg : transmitterService.getReceivers(player.getUniqueId())) {
        final var recPlayer = Bukkit.getPlayer(cfg.getUuid());
        final var recName = recPlayer != null ? recPlayer.getName() : cfg.getUuid().toString().substring(0, 8);
        final var range = !cfg.hasMaxDistance() ? "INFINITE" : String.format("%.1fm", cfg.getMaxDistance());

        items.add(new AbstractItem() {
          @Override
          public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
            return new ItemBuilder(Material.PLAYER_HEAD)
              .setName(Component.translatable("config.gui.transmitter.receiver.name", Component.text(recName)))
              .setLore(List.of(
                Component.translatable("config.gui.transmitter.receiver.lore_range", Component.text(range)),
                Component.empty(),
                Component.translatable("config.gui.transmitter.receiver.lore_unlink")
              ))
              .toGuiItem();
          }

          @Override
          public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
            transmitterService.removeReceiver(player.getUniqueId(), cfg.getUuid());
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.9f);
            TransmitterListGUI.this.open(p);
          }
        });
      }
    }

    final var toggleTransmitter = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        final var mat = isTransmitting ? Material.TARGET : Material.IRON_BARS;

        return new ItemBuilder(mat)
          .setName(isTransmitting ? Component.translatable("config.gui.transmitter.toggle_enabled") : Component.translatable("config.gui.transmitter.toggle_disabled"))
          .setLore(List.of(
            Component.translatable("config.gui.transmitter.toggle_lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (transmitterService == null)
          return;

        if (isTransmitting)
          transmitterService.removeTransmitter(p.getUniqueId());
        else
          transmitterService.createTransmitter(p.getUniqueId());

        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, isTransmitting ? 0.8f : 1.2f);
        TransmitterListGUI.this.open(p);
      }
    };

    final var addTargetReceiver = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        final var canAdd = target.entity() instanceof Player;
        final var mat = canAdd ? Material.EMERALD_BLOCK : Material.GRAY_DYE;

        return new ItemBuilder(mat)
          .setName(canAdd ? Component.translatable("config.gui.transmitter.link_target_player", Component.text(target.entity().getName())) : Component.translatable("config.gui.transmitter.link_no_target"))
          .setLore(List.of(
            Component.translatable("config.gui.transmitter.link_lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (target.entity() instanceof Player targetPlayer && transmitterService != null) {
          transmitterService.addReceiver(p.getUniqueId(), targetPlayer.getUniqueId());
          p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
          TransmitterListGUI.this.open(p);
        }
      }
    };

    final var border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(Component.empty()).toGuiItem();

    return PagedGui.itemsBuilder()
      .setStructure(
        "X X X X X X X X X",
        "X X X X X X X X X",
        "P . T . . . A . N",
        "# # # # B # # # #"
      )
      .addIngredient('X', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
      .addIngredient('P', GuiItems.PREVIOUS())
      .addIngredient('N', GuiItems.NEXT())
      .addIngredient('T', toggleTransmitter)
      .addIngredient('A', addTargetReceiver)
      .addIngredient('B', GuiItems.BACK(parentGui))
      .addIngredient('#', border)
      .setContent(items)
      .build();
  }

}
