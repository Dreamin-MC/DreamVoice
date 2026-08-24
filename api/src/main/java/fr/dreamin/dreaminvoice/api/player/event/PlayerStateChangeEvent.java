package fr.dreamin.dreaminvoice.api.player.event;

import fr.dreamin.dreamapi.api.event.ToolsCancelEvent;
import fr.dreamin.dreaminvoice.api.player.model.PlayerState;
import fr.dreamin.dreaminvoice.api.player.model.VPlayer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Getter @Setter
@RequiredArgsConstructor
public final class PlayerStateChangeEvent extends ToolsCancelEvent {

  private final @NotNull VPlayer vPlayer;

  private final @NotNull PlayerState oldState;
  private @NotNull PlayerState newState;

}
