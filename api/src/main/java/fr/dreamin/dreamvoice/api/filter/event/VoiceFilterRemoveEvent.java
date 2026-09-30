package fr.dreamin.dreamvoice.api.filter.event;

import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired after a DSP voice filter is removed from a player.
 */
@Getter
@RequiredArgsConstructor
public final class VoiceFilterRemoveEvent extends VoiceEvent {

  private final @NotNull UUID playerUuid;
  private final @NotNull VoiceFilter filter;

}
