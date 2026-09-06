package fr.dreamin.dreamvoice.api.speaker.event;

import fr.dreamin.dreamapi.api.event.ToolsCancelEvent;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a player is linked / authorized to broadcast speech through a {@link Speaker}. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class SpeakerLinkPlayerEvent extends ToolsCancelEvent {

  private final @NotNull Speaker speaker;
  private final @NotNull UUID playerUuid;

}
