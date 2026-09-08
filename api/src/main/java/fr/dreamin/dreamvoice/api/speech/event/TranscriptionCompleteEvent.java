package fr.dreamin.dreamvoice.api.speech.event;

import fr.dreamin.dreamvoice.api.speech.model.SpeechTranscriptionResult;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a voice recording transcription has completed successfully.
 */
@Getter
public final class TranscriptionCompleteEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();

  private final @NotNull Player player;
  private final @Nullable Block stationBlock;
  private final @NotNull SpeechTranscriptionResult result;
  @Setter
  private @NotNull ItemStack resultBook;

  public TranscriptionCompleteEvent(
    final @NotNull Player player,
    final @Nullable Block stationBlock,
    final @NotNull SpeechTranscriptionResult result,
    final @NotNull ItemStack resultBook
  ) {
    super(false);
    this.player = player;
    this.stationBlock = stationBlock;
    this.result = result;
    this.resultBook = resultBook;
  }

  @Override
  public @NotNull HandlerList getHandlers() {
    return HANDLERS;
  }

  public static @NotNull HandlerList getHandlerList() {
    return HANDLERS;
  }

}
