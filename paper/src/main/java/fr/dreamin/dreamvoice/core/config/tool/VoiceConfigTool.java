package fr.dreamin.dreamvoice.core.config.tool;

import fr.dreamin.dreamapi.api.item.ItemAction;
import fr.dreamin.dreamapi.api.item.ItemDefinition;
import fr.dreamin.dreamapi.api.item.ItemDefinitionSupplier;
import fr.dreamin.dreamapi.api.item.annotations.DreamItem;
import fr.dreamin.dreamvoice.core.config.model.ConfigTarget;
import fr.dreamin.dreamvoice.core.config.ui.VoiceConfigMainGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@DreamItem
public final class VoiceConfigTool implements ItemDefinitionSupplier {

  public static final String ID = "dreamvoice_config_tool";

  @Override
  public ItemDefinition get() {
    return createDefinition();
  }

  public static ItemDefinition createDefinition() {
    final var baseItem = new ItemStack(Material.ECHO_SHARD);
    final var meta = baseItem.getItemMeta();
    if (meta != null) {
      meta.displayName(Component.translatable("config.tool.name"));
      meta.lore(List.of(
        Component.translatable("config.tool.lore_desc"),
        Component.empty(),
        Component.translatable("config.tool.lore_action1"),
        Component.translatable("config.tool.lore_action2")
      ));
      baseItem.setItemMeta(meta);
    }

    return ItemDefinition.builder()
      .id(ID)
      .item(baseItem)
      .handler(ItemAction.RIGHT_CLICK, ctx -> {
        final var player = ctx.player();
        final var target = ConfigTarget.resolve(player);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        player.sendMessage(Component.translatable("config.target_selected", Component.text(target.describe())));
        new VoiceConfigMainGUI(target).open(player);
        return true;
      })
      .handler(ItemAction.LEFT_CLICK, ctx -> {
        final var player = ctx.player();
        final var target = ConfigTarget.resolve(player);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
        player.sendMessage(Component.translatable("config.target_selected", Component.text(target.describe())));
        new VoiceConfigMainGUI(target).open(player);
        return true;
      })
      .build();
  }

  public static void give(final @NotNull Player player) {
    final var def = createDefinition();
    player.getInventory().addItem(def.getItem().clone());
    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
    player.sendMessage(Component.translatable("config.tool_given"));
  }

  public static boolean isHoldingConfigTool(final @NotNull Player player) {
    return isConfigTool(player.getInventory().getItemInMainHand())
      || isConfigTool(player.getInventory().getItemInOffHand());
  }

  public static boolean isConfigTool(final @Nullable ItemStack item) {
    if (item == null || item.getType() != Material.ECHO_SHARD)
      return false;

    final var meta = item.getItemMeta();
    return meta.hasDisplayName();
  }

  public static boolean hasConfigTool(final @NotNull Player player) {
    if (isHoldingConfigTool(player))
      return true;

    for (final var item : player.getInventory().getContents()) {
      if (isConfigTool(item))
        return true;
    }
    return false;
  }

}
