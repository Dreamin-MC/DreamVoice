package fr.dreamin.dreamvoice.api.wiretap.event;

import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired after a {@link VoiceWiretap} is removed.
 */
@Getter
@RequiredArgsConstructor
public final class WiretapRemoveEvent extends VoiceEvent {

  private final @NotNull VoiceWiretap wiretap;

}
