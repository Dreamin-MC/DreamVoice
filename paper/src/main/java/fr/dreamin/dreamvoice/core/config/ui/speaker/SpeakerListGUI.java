package fr.dreamin.dreamvoice.core.config.ui.speaker;

import fr.dreamin.dreamapi.api.gui.model.GuiInterface;
import fr.dreamin.dreamapi.core.gui.item.GuiItems;
import fr.dreamin.dreamapi.core.item.builder.ItemBuilder;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
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

public final class SpeakerListGUI extends GuiInterface {

  private final @NotNull ConfigTarget target;
  private final @NotNull GuiInterface parentGui;

  public SpeakerListGUI(final @NotNull ConfigTarget target, final @NotNull GuiInterface parentGui) {
    this.target = target;
    this.parentGui = parentGui;
  }

  @Override
  public Component name(final @NotNull Player player) {
    return Component.translatable("config.gui.speaker.title");
  }

  @Override
  public Gui guiUpper(final @NotNull Player player) {
    final var speakerService = DreamVoice.getService(VoiceSpeakerService.class);
    final var items = new ArrayList<Item>();

    if (speakerService != null) {
      for (final var speaker : speakerService.getSpeakers()) {
        items.add(new AbstractItem() {
          @Override
          public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
            final var loc = speaker.getLocation();
            final var locStr = String.format("%.1f, %.1f, %.1f", loc.x(), loc.y(), loc.z());
            return new ItemBuilder(Material.JUKEBOX)
              .setName(Component.translatable("config.gui.speaker.item.name", Component.text(speaker.getName())))
              .setLore(List.of(
                Component.translatable("config.gui.speaker.item.lore_range", Component.text(speaker.getDistance())),
                Component.translatable("config.gui.speaker.item.lore_mode", Component.text(speaker.getMode().name())),
                Component.translatable("config.gui.speaker.item.lore_location", Component.text(locStr)),
                Component.empty(),
                Component.translatable("config.gui.speaker.item.lore_click")
              ))
              .toGuiItem();
          }

          @Override
          public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            new SpeakerEditGUI(speaker, target, SpeakerListGUI.this).open(p);
          }
        });
      }
    }

    final var createItem = new AbstractItem() {
      @Override
      public @NotNull ItemProvider getItemProvider(final @NotNull Player p) {
        return new ItemBuilder(Material.EMERALD_BLOCK)
          .setName(Component.translatable("config.gui.speaker.create.name"))
          .setLore(List.of(
            Component.translatable("config.gui.speaker.create.lore_target", Component.text(target.describe())),
            Component.translatable("config.gui.speaker.create.lore_click")
          ))
          .toGuiItem();
      }

      @Override
      public void handleClick(final @NotNull ClickType clickType, final @NotNull Player p, final @NotNull Click click) {
        if (speakerService == null)
          return;

        final var shortId = UUID.randomUUID().toString().substring(0, 5);
        final var name = "speaker_" + shortId;

        final var builder = Speaker.builder()
          .name(name)
          .location(LocationUtils.toVoiceLocation(target.location()))
          .distance(15.0f);

        if (target.entity() != null)
          builder.targetEntity(target.entity().getUniqueId());

        builder.build();

        speakerService.save();
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
        p.sendMessage(Component.translatable("speaker.created", Component.text(name), Component.text("15.0")));
        new SpeakerListGUI(target, parentGui).open(p);
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
