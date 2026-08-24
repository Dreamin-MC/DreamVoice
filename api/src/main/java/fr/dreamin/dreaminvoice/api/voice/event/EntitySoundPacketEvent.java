package fr.dreamin.dreaminvoice.api.voice.event;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import fr.dreamin.dreamapi.api.event.ToolsEvent;
import fr.dreamin.dreaminvoice.api.player.model.VPlayer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public final class EntitySoundPacketEvent extends ToolsEvent {

  private final @NotNull de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent svcEvent;
  private final @NotNull VoicechatConnection sender;
  private final @NotNull VPlayer vSender;
  private final @NotNull VoicechatConnection receiver;
  private final @NotNull VPlayer vReceiver;
  private final @NotNull EntitySoundPacket packet;

}
