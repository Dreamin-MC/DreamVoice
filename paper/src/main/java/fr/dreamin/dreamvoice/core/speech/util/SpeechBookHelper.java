package fr.dreamin.dreamvoice.core.speech.util;

import fr.dreamin.dreamvoice.api.speech.model.SpeechTranscriptionResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public final class SpeechBookHelper {

  private static final MiniMessage MM = MiniMessage.miniMessage();

  private SpeechBookHelper() {}

  public static @NotNull ItemStack createReportBook(final @NotNull Player player, final @NotNull SpeechTranscriptionResult result) {
    final var item = new ItemStack(Material.WRITTEN_BOOK);
    final var meta = (BookMeta) item.getItemMeta();
    if (meta == null)
      return item;

    final var recId = result.recordingUuid() != null ? result.recordingUuid().toString().substring(0, 8) : "Live";
    final var duration = String.format(Locale.US, "%.1f", result.durationSeconds());
    final var authorName = resolveSpeakerName(result.speakerUuid());

    var title = "Report #" + recId;

    meta.setTitle(title);
    meta.setAuthor("Analysis Station");

    final var header = "--- Transcription: " + recId + " ---\nDuration: " + duration + "s\nSpeaker: " + authorName;

    final var pages = new ArrayList<Component>();
    final var firstPageLines = new StringBuilder();
    firstPageLines.append(header).append("\n\n");

    if (result.segments().isEmpty()) {
      firstPageLines.append("<gray><i>(No speech detected)</i></gray>");
      pages.add(MM.deserialize(firstPageLines.toString()));
    } else {
      var currentPage = new StringBuilder(firstPageLines.toString());
      for (final var seg : result.segments()) {
        final var minutes = (int) (seg.offsetSeconds() / 60);
        final var seconds = (int) (seg.offsetSeconds() % 60);
        final var timeTag = String.format("[%02d:%02d]", minutes, seconds);
        final var line = "<gray>" + timeTag + "</gray> <gold>" + seg.speakerName() + "</gold>: " + seg.text() + "\n";

        if (currentPage.length() + line.length() > 250) {
          pages.add(MM.deserialize(currentPage.toString()));
          currentPage = new StringBuilder(line);
        } else
          currentPage.append(line);
      }
      if (!currentPage.isEmpty())
        pages.add(MM.deserialize(currentPage.toString()));
    }

    meta.pages(pages);
    item.setItemMeta(meta);
    return item;
  }

  private static @NotNull String resolveSpeakerName(final @Nullable UUID uuid) {
    if (uuid == null)
      return "Unknown";
    final var offline = Bukkit.getOfflinePlayer(uuid);
    return offline.getName() != null ? offline.getName() : uuid.toString().substring(0, 8);
  }
}
