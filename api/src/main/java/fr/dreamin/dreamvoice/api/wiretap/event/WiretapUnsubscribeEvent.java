package fr.dreamin.dreamvoice.api.wiretap.event;

import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired after a player stops eavesdropping on a {@link VoiceWiretap}.
 */
@Getter
@RequiredArgsConstructor
public final class WiretapUnsubscribeEvent extends VoiceEvent {

  private final @NotNull VoiceWiretap wiretap;
  private final @NotNull UUID playerUuid;

}
