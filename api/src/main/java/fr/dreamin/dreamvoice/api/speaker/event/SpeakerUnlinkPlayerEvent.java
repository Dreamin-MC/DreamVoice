package fr.dreamin.dreamvoice.api.speaker.event;

import fr.dreamin.dreamapi.api.event.ToolsEvent;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a player is unlinked from a {@link Speaker}.
 */
@Getter
@RequiredArgsConstructor
public final class SpeakerUnlinkPlayerEvent extends ToolsEvent {

  private final @NotNull Speaker speaker;
  private final @NotNull UUID playerUuid;

}
