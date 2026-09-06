package fr.dreamin.dreamvoice.api.recording.event;

import fr.dreamin.dreamapi.api.event.ToolsCancelEvent;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when creating a physical playable Cassette item. Cancellable.
 */
@Getter
@Setter
@RequiredArgsConstructor
public final class CassetteCreateEvent extends ToolsCancelEvent {

  private final @NotNull VoiceRecording recording;
  private @NotNull ItemStack itemStack;

}
