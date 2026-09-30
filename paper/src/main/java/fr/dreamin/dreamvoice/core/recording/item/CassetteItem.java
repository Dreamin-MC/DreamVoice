package fr.dreamin.dreamvoice.core.recording.item;

import fr.dreamin.dreamapi.api.lang.utils.LangUtils;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.core.DreamVoice;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class CassetteItem {

  public static final NamespacedKey CASSETTE_KEY = new NamespacedKey(DreamVoice.getInstance(), "cassette_id");

  public static ItemStack create(final @NotNull VoiceRecording recording) {
    return create(recording, null, null);
  }

  public static ItemStack create(final @NotNull VoiceRecording recording, final @Nullable Player player) {
    return create(recording, null, player);
  }

  public static ItemStack create(
    final @NotNull VoiceRecording recording,
    final @Nullable String customAuthor,
    final @Nullable Player player
  ) {
    final var item = new ItemStack(Material.MUSIC_DISC_RELIC);
    final var meta = item.getItemMeta();
    if (meta == null)
      return item;

    final String speakerName;
    if (customAuthor != null && !customAuthor.isBlank()) {
      speakerName = customAuthor;
    } else {
      final var speakerPlayer = Bukkit.getOfflinePlayer(recording.getSpeakerUUID());
      speakerName = speakerPlayer.getName() != null ? speakerPlayer.getName() : "Unknown";
    }
    final var duration = String.format("%.1f", recording.getDurationSeconds());

    meta.displayName(Component.translatable("cassette.name"));

    meta.lore(List.of(
      Component.translatable("cassette.lore.author", Component.text(speakerName)),
      Component.translatable("cassette.lore.duration", Component.text(duration)),
      Component.translatable("cassette.lore.id", Component.text(recording.getUuid().toString().substring(0, 8))),
      Component.empty(),
      Component.translatable("cassette.lore.play")
    ));

    meta.getPersistentDataContainer().set(CASSETTE_KEY, PersistentDataType.STRING, recording.getUuid().toString());
    item.setItemMeta(meta);

    if (player != null)
      LangUtils.updateTranslate(player, item);

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

