package fr.dreamin.dreamvoice.core.config.ui.broadcast;

import fr.dreamin.dreamapi.api.gui.model.GuiInterface;
import fr.dreamin.dreamapi.core.gui.item.GuiItems;
import fr.dreamin.dreamapi.core.item.builder.ItemBuilder;
import fr.dreamin.dreamvoice.api.broadcast.model.BroadcastPoint;
import fr.dreamin.dreamvoice.api.broadcast.service.VoiceBroadcastService;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
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

public final class BroadcastLinkSpeakersGUI extends GuiInterface {

  private final @NotNull BroadcastPoint point;
  private final @NotNull GuiInterface parentGui;

  public BroadcastLinkSpeakersGUI(final @NotNull BroadcastPoint point, final @NotNull GuiInterface parentGui) {
    this.point = point;
    this.parentGui = parentGui;
  }

  @Override
  public Component name(final @NotNull Player player) {
    return Component.translatable("config.gui.broadcast.link.title", Component.text(point.getName()));
  }

  @Override
  public Gui guiUpper(final @NotNull Player player) {
    final var speakerService = DreamVoice.getService(VoiceSpeakerService.class);
    final var broadcastService = DreamVoice.getService(VoiceBroadcastService.class);
    final var items = new ArrayList<Item>();

    if (speakerService != null) {
      for (final var speaker : speakerService.getSpeakers()) {
        final var isLinked = point.getTargetSpeakers().contains(speaker.getName().toLowerCase());

        items.add(new AbstractItem() {
          @Override
          public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
            final var mat = isLinked ? Material.LIME_DYE : Material.GRAY_DYE;

            return new ItemBuilder(mat)
              .setName(Component.translatable("config.gui.broadcast.link.item.name", Component.text(speaker.getName())))
              .setLore(List.of(
                isLinked ? Component.translatable("config.gui.broadcast.link.item.lore_linked") : Component.translatable("config.gui.broadcast.link.item.lore_unlinked"),
                Component.empty(),
                Component.translatable("config.gui.broadcast.link.item.lore_click")
              ))
              .toGuiItem();
          }

          @Override
          public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
            if (isLinked)
              point.unlinkSpeaker(speaker.getName());
            else
              point.linkSpeaker(speaker.getName());

            if (broadcastService != null)
              broadcastService.save();

            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, isLinked ? 0.9f : 1.2f);
            BroadcastLinkSpeakersGUI.this.open(p);
          }
        });
      }
    }

    final var border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(Component.empty()).toGuiItem();

    return PagedGui.itemsBuilder()
      .setStructure(
        "X X X X X X X X X",
        "X X X X X X X X X",
        "P . . . . . . . N",
        "# # # # B # # # #"
      )
      .addIngredient('X', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
      .addIngredient('P', GuiItems.PREVIOUS())
      .addIngredient('N', GuiItems.NEXT())
      .addIngredient('B', GuiItems.BACK(parentGui))
      .addIngredient('#', border)
      .setContent(items)
      .build();
  }

}
