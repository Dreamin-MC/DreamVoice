package fr.dreamin.dreaminvoice.api.player.event;

import fr.dreamin.dreamapi.api.event.ToolsEvent;
import fr.dreamin.dreaminvoice.api.player.model.VPlayer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public final class PlayerJoinVoiceEvent extends ToolsEvent {

  private final @NotNull VPlayer vPlayer;

}
