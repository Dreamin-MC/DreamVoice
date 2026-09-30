package fr.dreamin.dreamvoice.fabric.item;

import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.fabric.lang.LanguageServerManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class FabricCassetteItem {

  public static final String CASSETTE_KEY = "dreamvoice:cassette_id";

  public static @NotNull ItemStack create(final @NotNull VoiceRecording recording, final @Nullable String authorName) {
    return create(recording, authorName, (ServerPlayer) null);
  }

  public static @NotNull ItemStack create(
    final @NotNull VoiceRecording recording,
    final @Nullable String authorName,
    final @Nullable ServerPlayer target
  ) {
    final var locale = target != null ? LanguageServerManager.resolvePlayerLocale(target) : "FR";
    return create(recording, authorName, locale);
  }

  public static @NotNull ItemStack create(
    final @NotNull VoiceRecording recording,
    final @Nullable String authorName,
    final @Nullable String locale
  ) {
    final var stack = new ItemStack(Items.MUSIC_DISC_RELIC);
    final var speakerName = authorName != null ? authorName : "Unknown";
    final var duration = String.format("%.1f", recording.getDurationSeconds());
    final var shortId = recording.getUuid().toString().substring(0, 8);

    final var nameComp = LanguageServerManager.translate("cassette.name", locale);
    final var authorComp = LanguageServerManager.translate("cassette.lore.author", locale, speakerName);
    final var durationComp = LanguageServerManager.translate("cassette.lore.duration", locale, duration);
    final var idComp = LanguageServerManager.translate("cassette.lore.id", locale, shortId);
    final var playComp = LanguageServerManager.translate("cassette.lore.play", locale);

    stack.set(DataComponents.CUSTOM_NAME, nameComp);
    stack.set(DataComponents.LORE, new ItemLore(List.of(
      authorComp,
      durationComp,
      idComp,
      Component.empty(),
      playComp
    )));

    CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(CASSETTE_KEY, recording.getUuid().toString()));
    return stack;
  }

  public static @Nullable UUID getRecordingUuid(final @Nullable ItemStack stack) {
    if (stack == null || stack.isEmpty())
      return null;
    final var customData = stack.get(DataComponents.CUSTOM_DATA);
    if (customData == null)
      return null;
    final var tag = customData.copyTag();
    final var raw = tag.getStringOr(CASSETTE_KEY, "");
    if (raw.isBlank())
      return null;
    try {
      return UUID.fromString(raw);
    } catch (final Exception ignored) {
      return null;
    }
  }
}
