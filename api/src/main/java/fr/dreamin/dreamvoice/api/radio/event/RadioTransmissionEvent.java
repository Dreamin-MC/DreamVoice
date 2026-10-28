package fr.dreamin.dreamvoice.api.radio.event;

import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.radio.model.RadioChannel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a player transmits voice audio over a radio frequency channel.
 */
@Getter
public final class RadioTransmissionEvent extends VoiceEvent {

  private final @NotNull RadioChannel channel;
  private final @NotNull UUID senderUuid;
  private final byte[] opusData;

  public RadioTransmissionEvent(final @NotNull RadioChannel channel, final @NotNull UUID senderUuid, final byte[] opusData) {
    this.channel = channel;
    this.senderUuid = senderUuid;
    this.opusData = opusData != null ? opusData : new byte[0];
  }

  public RadioTransmissionEvent(final @NotNull RadioChannel channel, final @NotNull UUID senderUuid) {
    this(channel, senderUuid, new byte[0]);
  }

}
