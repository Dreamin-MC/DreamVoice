package fr.dreamin.dreamvoice.fabric.network.model.speaker;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundSpeakerUnlinkPacket(@NotNull String speakerId, @NotNull String playerUuid) implements CustomPacketPayload {

  public static final Type<ServerBoundSpeakerUnlinkPacket> TYPE = new Type<>(DreamVoiceFabric.id("server_bound_speaker_unlink"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundSpeakerUnlinkPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(128), ServerBoundSpeakerUnlinkPacket::speakerId,
    ByteBufCodecs.stringUtf8(128), ServerBoundSpeakerUnlinkPacket::playerUuid,
    ServerBoundSpeakerUnlinkPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
