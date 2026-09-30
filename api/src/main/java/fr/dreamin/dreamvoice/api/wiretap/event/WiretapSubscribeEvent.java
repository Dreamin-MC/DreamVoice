package fr.dreamin.dreamvoice.api.wiretap.event;

import fr.dreamin.dreamvoice.api.event.VoiceCancelEvent;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired before a player begins eavesdropping on a {@link VoiceWiretap}. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class WiretapSubscribeEvent extends VoiceCancelEvent {

  private final @NotNull VoiceWiretap wiretap;
  private final @NotNull UUID playerUuid;

}
