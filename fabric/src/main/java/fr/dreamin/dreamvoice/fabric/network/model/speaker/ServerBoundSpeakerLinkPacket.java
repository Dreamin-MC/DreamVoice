package fr.dreamin.dreamvoice.fabric.network.model.speaker;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundSpeakerLinkPacket(@NotNull String speakerId, @NotNull String playerUuid) implements CustomPacketPayload {

  public static final Type<ServerBoundSpeakerLinkPacket> TYPE = new Type<>(DreamVoiceFabric.id("server_bound_speaker_link"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundSpeakerLinkPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(128), ServerBoundSpeakerLinkPacket::speakerId,
    ByteBufCodecs.stringUtf8(128), ServerBoundSpeakerLinkPacket::playerUuid,
    ServerBoundSpeakerLinkPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
