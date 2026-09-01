package fr.dreamin.dreamvoice.api.voice.event;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import fr.dreamin.dreamapi.api.event.ToolsEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a MicrophonePacket (raw speech from a client microphone) is intercepted.
 */
@Getter
@RequiredArgsConstructor
public final class MicrophonePacketEvent extends ToolsEvent {

  private final @NotNull de.maxhenkel.voicechat.api.events.MicrophonePacketEvent scvEvent;
  private final @Nullable VoicechatConnection sender;
  private final @Nullable VoicechatConnection receiver;
  private final @NotNull MicrophonePacket packet;

}
