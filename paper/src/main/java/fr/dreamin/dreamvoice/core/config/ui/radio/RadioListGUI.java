package fr.dreamin.dreamvoice.core.config.ui.radio;

import fr.dreamin.dreamapi.api.gui.model.GuiInterface;
import fr.dreamin.dreamapi.core.gui.item.GuiItems;
import fr.dreamin.dreamapi.core.item.builder.ItemBuilder;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
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

public final class RadioListGUI extends GuiInterface {

  private final @NotNull GuiInterface parentGui;

  public RadioListGUI(final @NotNull GuiInterface parentGui) {
    this.parentGui = parentGui;
  }

  @Override
  public Component name(final @NotNull Player player) {
    return Component.translatable("config.gui.radio.title");
  }

  @Override
  public Gui guiUpper(final @NotNull Player player) {
    final var radioService = DreamVoice.getService(VoiceRadioService.class);
    final var items = new ArrayList<Item>();
    final var playerChannel = radioService != null ? radioService.getChannelOfPlayer(player.getUniqueId()) : null;

    if (radioService != null) {
      for (final var channel : radioService.getChannels()) {
        final var isCurrent = playerChannel != null && playerChannel.getName().equalsIgnoreCase(channel.getName());

        items.add(new AbstractItem() {
          @Override
          public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
            final var mat = isCurrent ? Material.LIME_DYE : Material.GRAY_DYE;

            return new ItemBuilder(mat)
              .setName(Component.translatable("config.gui.radio.item.name", Component.text(channel.getName())))
              .setLore(List.of(
                isCurrent ? Component.translatable("config.gui.radio.item.lore_current") : Component.translatable("config.gui.radio.item.lore_tune"),
                Component.translatable("config.gui.radio.item.lore_listeners", Component.text(channel.getMembers().size()))
              ))
              .toGuiItem();
          }

          @Override
          public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
            radioService.joinChannel(p.getUniqueId(), channel.getName());
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.7f, 1.2f);
            p.sendMessage(Component.translatable("radio.connected", Component.text(channel.getName())));
            RadioListGUI.this.open(p);
          }
        });
      }
    }

    final var leaveItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.BARRIER)
          .setName(Component.translatable("config.gui.radio.leave.name"))
          .setLore(List.of(
            Component.translatable("config.gui.radio.leave.lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (radioService != null && playerChannel != null) {
          radioService.leaveChannel(p.getUniqueId());
          p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.8f);
          p.sendMessage(Component.translatable("radio.disconnected"));
          RadioListGUI.this.open(p);
        }
      }
    };

    final var border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(Component.empty()).toGuiItem();

    return PagedGui.itemsBuilder()
      .setStructure(
        "X X X X X X X X X",
        "X X X X X X X X X",
        "P . . . D . . . N",
        "# # # # B # # # #"
      )
      .addIngredient('X', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
      .addIngredient('P', GuiItems.PREVIOUS())
      .addIngredient('N', GuiItems.NEXT())
      .addIngredient('D', leaveItem)
      .addIngredient('B', GuiItems.BACK(parentGui))
      .addIngredient('#', border)
      .setContent(items)
      .build();
  }

}
