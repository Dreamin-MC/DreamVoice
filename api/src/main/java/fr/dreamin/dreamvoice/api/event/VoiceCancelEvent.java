package fr.dreamin.dreamvoice.api.event;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for cancellable DreamVoice events.
 */
@Getter
@Setter
public abstract class VoiceCancelEvent extends VoiceEvent {

  private boolean cancelled = false;

  @Override
  public boolean callEvent() {
    try {
      final var api = DreamVoiceAPI.getNullable();
      if (api != null)
        api.callEvent(this);
      return !this.cancelled;
    } catch (final Throwable ignored) {
      return !this.cancelled;
    }
  }

}
