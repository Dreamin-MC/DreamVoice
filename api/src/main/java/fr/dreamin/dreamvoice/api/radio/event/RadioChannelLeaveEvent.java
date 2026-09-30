package fr.dreamin.dreamvoice.api.radio.event;

import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.radio.model.RadioChannel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a player disconnects from a {@link RadioChannel}.
 */
@Getter
@RequiredArgsConstructor
public final class RadioChannelLeaveEvent extends VoiceEvent {

  private final @NotNull RadioChannel channel;
  private final @NotNull UUID playerUuid;

}
