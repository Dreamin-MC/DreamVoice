package fr.dreamin.dreamvoice.core.config.ui.wiretap;

import fr.dreamin.dreamapi.api.gui.model.GuiInterface;
import fr.dreamin.dreamapi.core.gui.item.GuiItems;
import fr.dreamin.dreamapi.core.item.builder.ItemBuilder;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.config.model.ConfigTarget;
import fr.dreamin.dreamvoice.core.utils.LocationUtils;
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
import java.util.UUID;

public final class WiretapListGUI extends GuiInterface {

  private final @NotNull ConfigTarget target;
  private final @NotNull GuiInterface parentGui;

  public WiretapListGUI(final @NotNull ConfigTarget target, final @NotNull GuiInterface parentGui) {
    this.target = target;
    this.parentGui = parentGui;
  }

  @Override
  public Component name(final @NotNull Player player) {
    return Component.translatable("config.gui.wiretap.title");
  }

  @Override
  public Gui guiUpper(final @NotNull Player player) {
    final var wiretapService = DreamVoice.getService(VoiceWiretapService.class);
    final var items = new ArrayList<Item>();

    if (wiretapService != null) {
      for (final var wiretap : wiretapService.getWiretaps()) {
        items.add(new AbstractItem() {
          @Override
          public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
            final var loc = wiretap.getLocation();
            final var locStr = String.format("%.1f, %.1f, %.1f", loc.x(), loc.y(), loc.z());
            final var listenersCount = wiretap.getListeners().size();
            final var targetEntity = wiretap.getTargetEntityUuid() != null ? Bukkit.getEntity(wiretap.getTargetEntityUuid()) : null;
            final var entityName = targetEntity != null ? targetEntity.getType().name() : "None";

            return new ItemBuilder(Material.SCULK_SENSOR)
              .setName(Component.translatable("config.gui.wiretap.item.name", Component.text(wiretap.getName())))
              .setLore(List.of(
                Component.translatable("config.gui.wiretap.item.lore_listeners", Component.text(listenersCount)),
                Component.translatable("config.gui.wiretap.item.lore_entity", Component.text(entityName)),
                Component.translatable("config.gui.wiretap.item.lore_location", Component.text(locStr)),
                Component.empty(),
                Component.translatable("config.gui.wiretap.item.lore_click")
              ))
              .toGuiItem();
          }

          @Override
          public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            new WiretapEditGUI(wiretap, target, WiretapListGUI.this).open(p);
          }
        });
      }
    }

    final var createItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.EMERALD_BLOCK)
          .setName(Component.translatable("config.gui.wiretap.create.name"))
          .setLore(List.of(
            Component.translatable("config.gui.wiretap.create.lore_target", Component.text(target.describe())),
            Component.translatable("config.gui.wiretap.create.lore_click")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (wiretapService == null)
          return;

        final var shortId = UUID.randomUUID().toString().substring(0, 5);
        final var name = "bug_" + shortId;

        final VoiceWiretap wiretap;
        if (target.entity() != null)
          wiretap = wiretapService.createWiretap(name, LocationUtils.toVoiceLocation(target.entity().getLocation()), target.entity().getUniqueId());
        else
          wiretap = wiretapService.createWiretap(name, LocationUtils.toVoiceLocation(target.location()));

        wiretapService.save();
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
        p.sendMessage(Component.translatable("wiretap.created", Component.text(wiretap.getName())));
        new WiretapListGUI(target, parentGui).open(p);
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
