package fr.dreamin.dreamvoice.core.config.ui.room;

import fr.dreamin.dreamapi.api.gui.model.GuiInterface;
import fr.dreamin.dreamapi.core.gui.item.GuiItems;
import fr.dreamin.dreamapi.core.item.builder.ItemBuilder;
import fr.dreamin.dreamvoice.api.room.service.VoiceRoomService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
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

public final class RoomListGUI extends GuiInterface {

  private final @NotNull GuiInterface parentGui;

  public RoomListGUI(final @NotNull GuiInterface parentGui) {
    this.parentGui = parentGui;
  }

  @Override
  public Component name(final @NotNull Player player) {
    return Component.translatable("config.gui.room.title");
  }

  @Override
  public Gui guiUpper(final @NotNull Player player) {
    final var roomService = DreamVoice.getService(VoiceRoomService.class);
    final var items = new ArrayList<Item>();

    if (roomService != null) {
      for (final var room : roomService.getRooms()) {
        final var isolation = roomService.getEffectiveIsolation(room);
        items.add(new AbstractItem() {
          @Override
          public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
            return new ItemBuilder(Material.STRUCTURE_BLOCK)
              .setName(Component.translatable("config.gui.room.item.name", Component.text(room.getName())))
              .setLore(List.of(
                Component.translatable("config.gui.room.item.lore_id", Component.text(room.getId())),
                Component.translatable("config.gui.room.item.lore_preset", Component.text(room.getPresetId())),
                Component.translatable("config.gui.room.item.lore_isolation", Component.text(isolation)),
                Component.translatable("config.gui.room.item.lore_cuboids", Component.text(room.getCuboids().size()))
              ))
              .toGuiItem();
          }

          @Override
          public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {}
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
