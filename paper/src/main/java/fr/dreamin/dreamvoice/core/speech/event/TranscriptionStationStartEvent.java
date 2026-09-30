package fr.dreamin.dreamvoice.core.speech.event;

import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import lombok.Getter;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a player initiates physical transcription at a station block. Cancellable.
 */
@Getter
public final class TranscriptionStationStartEvent extends Event implements Cancellable {

  private static final HandlerList HANDLERS = new HandlerList();

  private final @NotNull Player player;
  private final @NotNull Block stationBlock;
  private final @NotNull ItemStack cassetteItem;
  private final @Nullable VoiceRecording recording;
  private boolean cancelled = false;

  public TranscriptionStationStartEvent(
    final @NotNull Player player,
    final @NotNull Block stationBlock,
    final @NotNull ItemStack cassetteItem,
    final @Nullable VoiceRecording recording
  ) {
    super(false);
    this.player = player;
    this.stationBlock = stationBlock;
    this.cassetteItem = cassetteItem;
    this.recording = recording;
  }

  @Override
  public boolean isCancelled() {
    return this.cancelled;
  }

  @Override
  public void setCancelled(final boolean cancel) {
    this.cancelled = cancel;
  }

  @Override
  public @NotNull HandlerList getHandlers() {
    return HANDLERS;
  }

  public static @NotNull HandlerList getHandlerList() {
    return HANDLERS;
  }

}
