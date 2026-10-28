package fr.dreamin.dreamvoice.neoforge.event;

import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public class NeoForgeVoiceEvent extends Event {

  private final @NotNull VoiceEvent voiceEvent;

}
