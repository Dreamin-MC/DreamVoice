package fr.dreamin.dreamvoice.api.player.event;

import fr.dreamin.dreamvoice.api.event.VoiceCancelEvent;
import fr.dreamin.dreamvoice.api.player.model.PlayerState;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a player's {@link PlayerState} is changed. Cancellable.
 */
@Getter
@Setter
@RequiredArgsConstructor
public final class PlayerStateChangeEvent extends VoiceCancelEvent {

  private final @NotNull VPlayer vPlayer;
  private final @NotNull PlayerState oldState;
  private @NotNull PlayerState newState;

}
