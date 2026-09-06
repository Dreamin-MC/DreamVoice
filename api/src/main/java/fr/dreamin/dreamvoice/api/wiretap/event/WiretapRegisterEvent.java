package fr.dreamin.dreamvoice.api.wiretap.event;

import fr.dreamin.dreamapi.api.event.ToolsCancelEvent;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired before a {@link VoiceWiretap} is registered. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class WiretapRegisterEvent extends ToolsCancelEvent {

  private final @NotNull VoiceWiretap wiretap;

}
