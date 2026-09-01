package fr.dreamin.dreamvoice.core.recording.item;

import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class CassetteItem {

  public static final NamespacedKey CASSETTE_KEY = new NamespacedKey(DreamVoice.getInstance(), "cassette_id");

  public static ItemStack create(final @NotNull VoiceRecording recording) {
    final var item = new ItemStack(Material.MUSIC_DISC_RELIC);
    final var meta = item.getItemMeta();
    if (meta == null)
      return item;

    final var speakerPlayer = Bukkit.getOfflinePlayer(recording.getSpeakerUUID());
    final var speakerName = speakerPlayer.getName() != null ? speakerPlayer.getName() : "Unknown";
    final var duration = String.format("%.1f", recording.getDurationSeconds());

    meta.displayName(
      Component.text("Voice Cassette", NamedTextColor.GOLD, TextDecoration.BOLD)
        .decoration(TextDecoration.ITALIC, false)
    );

    meta.lore(List.of(
      Component.text("Author: ", NamedTextColor.GRAY)
        .append(Component.text(speakerName, NamedTextColor.YELLOW))
        .decoration(TextDecoration.ITALIC, false),
      Component.text("Duration: ", NamedTextColor.GRAY)
        .append(Component.text(duration + "s", NamedTextColor.AQUA))
        .decoration(TextDecoration.ITALIC, false),
      Component.text("ID: ", NamedTextColor.DARK_GRAY)
        .append(Component.text(recording.getUuid().toString().substring(0, 8) + "...", NamedTextColor.DARK_GRAY))
        .decoration(TextDecoration.ITALIC, false),
      Component.empty(),
      Component.text("▶ Right-Click to play", NamedTextColor.GREEN)
        .decoration(TextDecoration.ITALIC, false)
    ));

    meta.getPersistentDataContainer().set(CASSETTE_KEY, PersistentDataType.STRING, recording.getUuid().toString());
    item.setItemMeta(meta);

    return item;
  }

  public static ItemStack linkItem(final @NotNull ItemStack item, final @NotNull VoiceRecording recording) {
    return linkItem(item, recording.getUuid());
  }

  public static ItemStack linkItem(final @NotNull ItemStack item, final @NotNull UUID recordingUuid) {
    final var meta = item.getItemMeta();
    if (meta == null)
      return item;

    meta.getPersistentDataContainer().set(CASSETTE_KEY, PersistentDataType.STRING, recordingUuid.toString());
    item.setItemMeta(meta);
    return item;
  }

  public static @Nullable UUID getRecordingUuid(final @Nullable ItemStack item) {
    if (item == null || !item.hasItemMeta())
      return null;

    final var meta = item.getItemMeta();
    final var pdc = meta.getPersistentDataContainer();
    final var rawUuid = pdc.get(CASSETTE_KEY, PersistentDataType.STRING);
    if (rawUuid == null)
      return null;

    try {
      return UUID.fromString(rawUuid);
    } catch (Exception e) {
      return null;
    }
  }

}

