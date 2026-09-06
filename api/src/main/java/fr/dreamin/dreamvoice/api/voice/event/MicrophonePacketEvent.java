package fr.dreamin.dreamvoice.api.voice.event;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import fr.dreamin.dreamapi.api.event.ToolsEvent;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a MicrophonePacket (raw speech from a client microphone) is intercepted.
 */
@Getter
public final class MicrophonePacketEvent extends ToolsEvent {

  private final @NotNull de.maxhenkel.voicechat.api.events.MicrophonePacketEvent scvEvent;
  private final @Nullable VoicechatConnection sender;
  private final @Nullable VoicechatConnection receiver;
  private final @NotNull MicrophonePacket packet;

  public MicrophonePacketEvent(
    final @NotNull de.maxhenkel.voicechat.api.events.MicrophonePacketEvent scvEvent,
    final @Nullable VoicechatConnection sender,
    final @Nullable VoicechatConnection receiver,
    final @NotNull MicrophonePacket packet
  ) {
    super(true);
    this.scvEvent = scvEvent;
    this.sender = sender;
    this.receiver = receiver;
    this.packet = packet;
  }

}
