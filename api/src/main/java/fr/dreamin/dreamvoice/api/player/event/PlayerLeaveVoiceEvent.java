package fr.dreamin.dreamvoice.api.player.event;

import fr.dreamin.dreamapi.api.event.ToolsEvent;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a player disconnects from Simple Voice Chat.
 */
@Getter
@RequiredArgsConstructor
public final class PlayerLeaveVoiceEvent extends ToolsEvent {

  private final @NotNull VPlayer vPlayer;

}
