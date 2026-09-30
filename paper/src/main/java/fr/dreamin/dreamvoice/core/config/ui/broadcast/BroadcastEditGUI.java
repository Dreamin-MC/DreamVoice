package fr.dreamin.dreamvoice.core.config.ui.broadcast;

import fr.dreamin.dreamapi.api.gui.model.GuiInterface;
import fr.dreamin.dreamapi.core.gui.item.GuiItems;
import fr.dreamin.dreamapi.core.item.builder.ItemBuilder;
import fr.dreamin.dreamvoice.api.broadcast.model.BroadcastPoint;
import fr.dreamin.dreamvoice.api.broadcast.service.VoiceBroadcastService;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
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

import java.util.ArrayList;
import java.util.List;

public final class BroadcastEditGUI extends GuiInterface {

  private final @NotNull BroadcastPoint point;
  private final @NotNull ConfigTarget target;
  private final @NotNull GuiInterface parentGui;

  public BroadcastEditGUI(final @NotNull BroadcastPoint point, final @NotNull ConfigTarget target, final @NotNull GuiInterface parentGui) {
    this.point = point;
    this.target = target;
    this.parentGui = parentGui;
  }

  @Override
  public Component name(final @NotNull Player player) {
    return Component.translatable("config.gui.broadcast.edit_title", Component.text(point.getName()));
  }

  @Override
  public void open(final @NotNull Player player) {
    final var vis = DreamVoice.getInstance().getVisualizerService();
    if (vis != null)
      vis.setFocus(player.getUniqueId(), point);
    super.open(player);
  }

  @Override
  public Gui guiUpper(final @NotNull Player player) {
    final var broadcastService = DreamVoice.getService(VoiceBroadcastService.class);
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    final var border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(Component.empty()).toGuiItem();

    final var toggleItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        final var mat = point.isEnabled() ? Material.LEVER : Material.REDSTONE_TORCH;

        return new ItemBuilder(mat)
          .setName(point.isEnabled() ? Component.translatable("config.gui.broadcast.edit.state_enabled") : Component.translatable("config.gui.broadcast.edit.state_disabled"))
          .setLore(List.of(
            Component.translatable("config.gui.broadcast.edit.state_lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        point.toggle();
        if (broadcastService != null)
          broadcastService.save();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, point.isEnabled() ? 1.2f : 0.8f);
        BroadcastEditGUI.this.open(p);
      }
    };

    final var radiusItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.COMPASS)
          .setName(Component.translatable("config.gui.broadcast.edit.radius", Component.text(point.getRadius())))
          .setLore(List.of(
            Component.translatable("config.gui.broadcast.edit.radius_lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {}
    };

    final var minusRadius = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.RED_DYE)
          .setName(Component.translatable("config.gui.broadcast.edit.minus_radius"))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        point.setRadius(Math.max(0.5, point.getRadius() - 1.0));
        if (broadcastService != null)
          broadcastService.save();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.9f);
        BroadcastEditGUI.this.open(p);
      }
    };

    final var plusRadius = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.LIME_DYE)
          .setName(Component.translatable("config.gui.broadcast.edit.plus_radius"))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        point.setRadius(point.getRadius() + 1.0);
        if (broadcastService != null)
          broadcastService.save();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        BroadcastEditGUI.this.open(p);
      }
    };

    final var allSpeakersItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.NOTE_BLOCK)
          .setName(point.isAllSpeakers() ? Component.translatable("config.gui.broadcast.edit.targets_all") : Component.translatable("config.gui.broadcast.edit.targets_specific", Component.text(point.getTargetSpeakers().size())))
          .setLore(List.of(
            Component.translatable("config.gui.broadcast.edit.targets_lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        point.setAllSpeakers(!point.isAllSpeakers());
        if (broadcastService != null)
          broadcastService.save();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
        BroadcastEditGUI.this.open(p);
      }
    };

    final var linkSpeakersItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.CHEST)
          .setName(Component.translatable("config.gui.broadcast.edit.link_speakers"))
          .setLore(List.of(
            Component.translatable("config.gui.broadcast.edit.link_speakers_lore1", Component.text(point.getTargetSpeakers().size())),
            Component.translatable("config.gui.broadcast.edit.link_speakers_lore2")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        new BroadcastLinkSpeakersGUI(point, BroadcastEditGUI.this).open(p);
      }
    };

    final var filterItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        final var currentFilter = point.getFilterId() != null ? point.getFilterId() : "NONE";
        return new ItemBuilder(Material.POTION)
          .setName(Component.translatable("config.gui.broadcast.edit.filter", Component.text(currentFilter)))
          .setLore(List.of(
            Component.translatable("config.gui.broadcast.edit.filter_lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (filterService == null)
          return;

        final var filters = new ArrayList<String>();
        filters.add(null);
        for (final var f : filterService.getAvailableFilters())
          filters.add(f.getId());

        final var currentIdx = filters.indexOf(point.getFilterId());
        final var nextIdx = (currentIdx + 1) % filters.size();
        final var nextFilter = filters.get(nextIdx);

        point.setFilterId(nextFilter);
        if (broadcastService != null)
          broadcastService.save();

        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.3f);
        BroadcastEditGUI.this.open(p);
      }
    };

    final var teleportItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.ENDER_PEARL)
          .setName(Component.translatable("config.gui.broadcast.edit.teleport"))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        final var loc = LocationUtils.toLocation(point.getLocation());
        if (loc != null)
          p.teleport(loc);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
      }
    };

    final var deleteItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.BARRIER)
          .setName(Component.translatable("config.gui.broadcast.edit.delete"))
          .setLore(List.of(
            Component.translatable("config.gui.broadcast.edit.delete_lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (broadcastService != null) {
          broadcastService.unregister(point.getUuid());
          broadcastService.save();
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
        p.sendMessage(Component.translatable("broadcast.removed", Component.text(point.getName())));
        parentGui.open(p);
      }
    };

    return Gui.builder()
      .setStructure(
        "# # # # # # # # #",
        "# O . - R + . A #",
        "# L . F . T . D #",
        "# # # # B # # # #"
      )
      .addIngredient('#', border)
      .addIngredient('O', toggleItem)
      .addIngredient('-', minusRadius)
      .addIngredient('R', radiusItem)
      .addIngredient('+', plusRadius)
      .addIngredient('A', allSpeakersItem)
      .addIngredient('L', linkSpeakersItem)
      .addIngredient('F', filterItem)
      .addIngredient('T', teleportItem)
      .addIngredient('D', deleteItem)
      .addIngredient('B', GuiItems.BACK(parentGui))
      .build();
  }

}
