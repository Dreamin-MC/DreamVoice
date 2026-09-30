package fr.dreamin.dreamvoice.fabric.event;

import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Fabric event hooks for DreamVoice acoustic events.
 * Other Fabric mods can register listeners to receive all DreamVoice events.
 */
public final class DreamVoiceEvents {

  @FunctionalInterface
  public interface VoiceEventListener {
    void onVoiceEvent(VoiceEvent event);
  }

  public static final Event<VoiceEventListener> VOICE_EVENT = EventFactory.createArrayBacked(
    VoiceEventListener.class,
    listeners -> event -> {
      for (var listener : listeners)
        listener.onVoiceEvent(event);
    }
  );

  private DreamVoiceEvents() {}
}
