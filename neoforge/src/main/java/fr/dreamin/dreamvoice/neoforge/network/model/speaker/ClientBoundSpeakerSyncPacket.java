package fr.dreamin.dreamvoice.neoforge.network.model.speaker;

import fr.dreamin.dreamvoice.neoforge.DreamVoiceNeoForge;
import fr.dreamin.dreamvoice.neoforge.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.CLIENT_BOUND_PLAY)
public record ClientBoundSpeakerSyncPacket(@NotNull String json) implements CustomPacketPayload {

  public static final Type<ClientBoundSpeakerSyncPacket> TYPE = new Type<>(DreamVoiceNeoForge.id("client_bound_speaker_sync"));

  public static final StreamCodec<FriendlyByteBuf, ClientBoundSpeakerSyncPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(DreamVoiceNeoForge.MAX_PACKET_STRING_LENGTH), ClientBoundSpeakerSyncPacket::json,
    ClientBoundSpeakerSyncPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}

