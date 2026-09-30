package fr.dreamin.dreamvoice.core.config.ui.broadcast;

import fr.dreamin.dreamapi.api.gui.model.GuiInterface;
import fr.dreamin.dreamapi.core.gui.item.GuiItems;
import fr.dreamin.dreamapi.core.item.builder.ItemBuilder;
import fr.dreamin.dreamvoice.api.broadcast.model.BroadcastPoint;
import fr.dreamin.dreamvoice.api.broadcast.service.VoiceBroadcastService;
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
import xyz.xenondevs.invui.gui.Markers;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.AbstractItem;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BroadcastListGUI extends GuiInterface {

  private final @NotNull ConfigTarget target;
  private final @NotNull GuiInterface parentGui;

  public BroadcastListGUI(final @NotNull ConfigTarget target, final @NotNull GuiInterface parentGui) {
    this.target = target;
    this.parentGui = parentGui;
  }

  @Override
  public Component name(final @NotNull Player player) {
    return Component.translatable("config.gui.broadcast.title");
  }

  @Override
  public Gui guiUpper(final @NotNull Player player) {
    final var broadcastService = DreamVoice.getService(VoiceBroadcastService.class);
    final var items = new ArrayList<Item>();

    if (broadcastService != null) {
      for (final var point : broadcastService.getBroadcastPoints()) {
        items.add(new AbstractItem() {
          @Override
          public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
            final var loc = point.getLocation();
            final var locStr = String.format("%.1f, %.1f, %.1f", loc.x(), loc.y(), loc.z());
            final var mat = point.isEnabled() ? Material.LIGHTNING_ROD : Material.IRON_BARS;

            return new ItemBuilder(mat)
              .setName(Component.translatable("config.gui.broadcast.item.name", Component.text(point.getName())))
              .setLore(List.of(
                point.isEnabled() ? Component.translatable("config.gui.broadcast.item.lore_status_enabled") : Component.translatable("config.gui.broadcast.item.lore_status_disabled"),
                Component.translatable("config.gui.broadcast.item.lore_radius", Component.text(point.getRadius())),
                Component.translatable("config.gui.broadcast.item.lore_speakers", Component.text(point.isAllSpeakers() ? "ALL" : String.valueOf(point.getTargetSpeakers().size()))),
                Component.translatable("config.gui.broadcast.item.lore_filter", Component.text(point.getFilterId() != null ? point.getFilterId() : "none")),
                Component.translatable("config.gui.broadcast.item.lore_location", Component.text(locStr)),
                Component.empty(),
                Component.translatable("config.gui.broadcast.item.lore_click")
              ))
              .toGuiItem();
          }

          @Override
          public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            new BroadcastEditGUI(point, target, BroadcastListGUI.this).open(p);
          }
        });
      }
    }

    final var createItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.EMERALD_BLOCK)
          .setName(Component.translatable("config.gui.broadcast.create.name"))
          .setLore(List.of(
            Component.translatable("config.gui.broadcast.create.lore_target", Component.text(target.describe())),
            Component.translatable("config.gui.broadcast.create.lore_click")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (broadcastService == null)
          return;

        final var shortId = UUID.randomUUID().toString().substring(0, 5);
        final var name = "mic_" + shortId;

        final BroadcastPoint point;
        if (target.entity() != null)
          point = new BroadcastPoint(name, LocationUtils.toVoiceLocation(target.entity().getLocation()), target.entity().getUniqueId());
        else
          point = new BroadcastPoint(name, LocationUtils.toVoiceLocation(target.location()));

        broadcastService.register(point);
        broadcastService.save();
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
        p.sendMessage(Component.translatable("broadcast.created", Component.text(name), Component.text(point.getRadius())));
        new BroadcastListGUI(target, parentGui).open(p);
      }
    };

    final var border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(Component.empty()).toGuiItem();

    return PagedGui.itemsBuilder()
      .setStructure(
        "X X X X X X X X X",
        "X X X X X X X X X",
        "P . . . C . . . N",
        "# # # # B # # # #"
      )
      .addIngredient('X', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
      .addIngredient('P', GuiItems.PREVIOUS())
      .addIngredient('N', GuiItems.NEXT())
      .addIngredient('C', createItem)
      .addIngredient('B', GuiItems.BACK(parentGui))
      .addIngredient('#', border)
      .setContent(items)
      .build();
  }

}
