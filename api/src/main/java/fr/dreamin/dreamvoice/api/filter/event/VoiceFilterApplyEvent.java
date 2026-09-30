package fr.dreamin.dreamvoice.api.filter.event;

import fr.dreamin.dreamvoice.api.event.VoiceCancelEvent;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired before a DSP voice filter is attached to a player. Cancellable.
 */
@Getter
@RequiredArgsConstructor
public final class VoiceFilterApplyEvent extends VoiceCancelEvent {

  private final @NotNull UUID playerUuid;
  private final @NotNull VoiceFilter filter;

}
