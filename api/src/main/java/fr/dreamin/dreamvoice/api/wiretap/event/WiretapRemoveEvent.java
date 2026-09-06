package fr.dreamin.dreamvoice.api.wiretap.event;

import fr.dreamin.dreamapi.api.event.ToolsEvent;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a {@link VoiceWiretap} is removed.
 */
@Getter
@RequiredArgsConstructor
public final class WiretapRemoveEvent extends ToolsEvent {

  private final @NotNull VoiceWiretap wiretap;

}
