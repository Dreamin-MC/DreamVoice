package fr.dreamin.dreamvoice.api.filter.event;

import fr.dreamin.dreamapi.api.event.ToolsEvent;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a DSP voice filter is removed from a player.
 */
@Getter
@RequiredArgsConstructor
public final class VoiceFilterRemoveEvent extends ToolsEvent {

  private final @NotNull UUID playerUuid;
  private final @NotNull VoiceFilter filter;

}
