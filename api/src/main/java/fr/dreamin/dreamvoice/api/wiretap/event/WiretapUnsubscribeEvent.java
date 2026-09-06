package fr.dreamin.dreamvoice.api.wiretap.event;

import fr.dreamin.dreamapi.api.event.ToolsEvent;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a player unsubscribes from eavesdropping on a {@link VoiceWiretap}.
 */
@Getter
@RequiredArgsConstructor
public final class WiretapUnsubscribeEvent extends ToolsEvent {

  private final @NotNull VoiceWiretap wiretap;
  private final @NotNull UUID playerUuid;

}
