package fr.dreamin.dreamvoice.core.recording.event;

import fr.dreamin.dreamapi.api.event.ToolsCancelEvent;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a player initiates playback of a physical Cassette item. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class CassettePlayEvent extends ToolsCancelEvent {

  private final @NotNull Player player;
  private final @NotNull VoiceRecording recording;
  private final @NotNull ItemStack item;

}
