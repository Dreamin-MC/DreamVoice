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

public final class WiretapEditGUI extends GuiInterface {

  private final @NotNull VoiceWiretap wiretap;
  private final @NotNull ConfigTarget target;
  private final @NotNull GuiInterface parentGui;

  public WiretapEditGUI(final @NotNull VoiceWiretap wiretap, final @NotNull ConfigTarget target, final @NotNull GuiInterface parentGui) {
    this.wiretap = wiretap;
    this.target = target;
    this.parentGui = parentGui;
  }

  @Override
  public Component name(final @NotNull Player player) {
    return Component.translatable("config.gui.wiretap.edit_title", Component.text(wiretap.getName()));
  }

  @Override
  public void open(final @NotNull Player player) {
    final var vis = DreamVoice.getInstance().getVisualizerService();
    if (vis != null)
      vis.setFocus(player.getUniqueId(), wiretap);
    super.open(player);
  }

  @Override
  public Gui guiUpper(final @NotNull Player player) {
    final var wiretapService = DreamVoice.getService(VoiceWiretapService.class);
    final var border = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setName(Component.empty()).toGuiItem();

    final var isListening = wiretap.getListeners().contains(player.getUniqueId());

    final var listenItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        final var mat = isListening ? Material.NOTE_BLOCK : Material.JUKEBOX;

        return new ItemBuilder(mat)
          .setName(isListening ? Component.translatable("config.gui.wiretap.edit.stop_listen") : Component.translatable("config.gui.wiretap.edit.start_listen"))
          .setLore(List.of(
            Component.translatable("config.gui.wiretap.edit.listen_lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (wiretapService == null)
          return;

        if (isListening)
          wiretapService.removeListener(wiretap.getName(), p.getUniqueId());
        else
          wiretapService.addListener(wiretap.getName(), p.getUniqueId());

        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, isListening ? 0.9f : 1.2f);
        WiretapEditGUI.this.open(p);
      }
    };

    final var teleportItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.ENDER_PEARL)
          .setName(Component.translatable("config.gui.wiretap.edit.teleport"))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        final var loc = LocationUtils.toLocation(wiretap.getLocation());
        if (loc != null)
          p.teleport(loc);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
      }
    };

    final var deleteItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.BARRIER)
          .setName(Component.translatable("config.gui.wiretap.edit.delete"))
          .setLore(List.of(
            Component.translatable("config.gui.wiretap.edit.delete_lore")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (wiretapService != null) {
          wiretapService.removeWiretap(wiretap.getName());
          wiretapService.save();
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
        p.sendMessage(Component.translatable("wiretap.removed", Component.text(wiretap.getName())));
        parentGui.open(p);
      }
    };

    return Gui.builder()
      .setStructure(
        "# # # # # # # # #",
        "# . L . T . D . #",
        "# # # # B # # # #"
      )
      .addIngredient('#', border)
      .addIngredient('L', listenItem)
      .addIngredient('T', teleportItem)
      .addIngredient('D', deleteItem)
      .addIngredient('B', GuiItems.BACK(parentGui))
      .build();
  }

}
