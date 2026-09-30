package fr.dreamin.dreamvoice.core.config.ui.projection;

import fr.dreamin.dreamapi.api.gui.model.GuiInterface;
import fr.dreamin.dreamapi.core.gui.item.GuiItems;
import fr.dreamin.dreamapi.core.item.builder.ItemBuilder;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.core.DreamVoice;
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

public final class ProjectionListGUI extends GuiInterface {

  private final @NotNull GuiInterface parentGui;

  public ProjectionListGUI(final @NotNull GuiInterface parentGui) {
    this.parentGui = parentGui;
  }

  @Override
  public Component name(final @NotNull Player player) {
    return Component.translatable("config.gui.projection.title");
  }

  @Override
  public Gui guiUpper(final @NotNull Player player) {
    final var projService = DreamVoice.getService(VoiceProjectionService.class);
    final var items = new ArrayList<Item>();

    if (projService != null) {
      for (final var proj : projService.getProjections()) {
        final var anchor = proj.getAnchorLocation();
        final var locStr = String.format("%.1f, %.1f, %.1f", anchor.x(), anchor.y(), anchor.z());
        final var owner = Bukkit.getPlayer(proj.getPlayerUuid());
        final var ownerName = owner != null ? owner.getName() : proj.getPlayerUuid().toString().substring(0, 8);

        items.add(new AbstractItem() {
          @Override
          public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
            return new ItemBuilder(Material.ENDER_EYE)
              .setName(Component.translatable("config.gui.projection.item.name", Component.text(ownerName)))
              .setLore(List.of(
                Component.translatable("config.gui.projection.item.lore_speaker", Component.text(ownerName)),
                Component.translatable("config.gui.projection.item.lore_anchor", Component.text(locStr)),
                Component.translatable("config.gui.projection.item.lore_emit_anchor", Component.text(proj.isEmitVoiceAtAnchor() ? "YES" : "NO")),
                Component.translatable("config.gui.projection.item.lore_emit_body", Component.text(proj.isEmitVoiceAtPlayer() ? "YES" : "NO")),
                Component.empty(),
                Component.translatable("config.gui.projection.item.lore_teleport")
              ))
              .toGuiItem();
          }

          @Override
          public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
            final var targetLoc = LocationUtils.toLocation(proj.getAnchorLocation());
            if (targetLoc != null)
              p.teleport(targetLoc);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
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
