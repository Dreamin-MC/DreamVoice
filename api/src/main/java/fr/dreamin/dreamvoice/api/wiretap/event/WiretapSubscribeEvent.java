package fr.dreamin.dreamvoice.api.wiretap.event;

import fr.dreamin.dreamapi.api.event.ToolsCancelEvent;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a player subscribes to live eavesdropping on a {@link VoiceWiretap}. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class WiretapSubscribeEvent extends ToolsCancelEvent {

  private final @NotNull VoiceWiretap wiretap;
  private final @NotNull UUID playerUuid;

}
