package fr.dreamin.dreamvoice.api.speaker.event;

import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired after a player is unlinked from an authorized speaker.
 */
@Getter
@RequiredArgsConstructor
public final class SpeakerUnlinkPlayerEvent extends VoiceEvent {

  private final @NotNull Speaker speaker;
  private final @NotNull UUID playerUuid;

}
