package fr.dreamin.dreamvoice.api.voice.event;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import fr.dreamin.dreamapi.api.event.ToolsEvent;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when an EntitySoundPacket (positional player speech) is intercepted from Simple Voice Chat.
 */
@Getter
public final class EntitySoundPacketEvent extends ToolsEvent {

  private final @NotNull de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent svcEvent;
  private final @NotNull VoicechatConnection sender;
  private final @NotNull VPlayer vSender;
  private final @NotNull VoicechatConnection receiver;
  private final @NotNull VPlayer vReceiver;
  private final @NotNull EntitySoundPacket packet;

  public EntitySoundPacketEvent(
    final @NotNull de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent svcEvent,
    final @NotNull VoicechatConnection sender,
    final @NotNull VPlayer vSender,
    final @NotNull VoicechatConnection receiver,
    final @NotNull VPlayer vReceiver,
    final @NotNull EntitySoundPacket packet
  ) {
    super(true);
    this.svcEvent = svcEvent;
    this.sender = sender;
    this.vSender = vSender;
    this.receiver = receiver;
    this.vReceiver = vReceiver;
    this.packet = packet;
  }

}
