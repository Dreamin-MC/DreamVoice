package fr.dreamin.dreamvoice.api.speaker.event;

import fr.dreamin.dreamvoice.api.event.VoiceCancelEvent;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired before a player is linked as an authorized speaker. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class SpeakerLinkPlayerEvent extends VoiceCancelEvent {

  private final @NotNull Speaker speaker;
  private final @NotNull UUID playerUuid;

}
