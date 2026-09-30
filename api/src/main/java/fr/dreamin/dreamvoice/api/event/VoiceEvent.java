package fr.dreamin.dreamvoice.api.event;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;

/**
 * Base class for all DreamVoice lifecycle and audio events.
 */
public abstract class VoiceEvent {

  /**
   * Dispatches this event through the active DreamVoice platform bus.
   *
   * @return {@code true} if event was dispatched successfully
   */
  public boolean callEvent() {
    try {
      final var api = DreamVoiceAPI.getNullable();
      if (api != null)
        api.callEvent(this);
      return true;
    } catch (final Throwable ignored) {
      return true;
    }
  }

}
