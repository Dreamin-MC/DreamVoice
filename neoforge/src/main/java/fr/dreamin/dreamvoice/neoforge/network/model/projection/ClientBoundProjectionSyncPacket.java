package fr.dreamin.dreamvoice.neoforge.network.model.projection;

import fr.dreamin.dreamvoice.neoforge.DreamVoiceNeoForge;
import fr.dreamin.dreamvoice.neoforge.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.CLIENT_BOUND_PLAY)
public record ClientBoundProjectionSyncPacket(@NotNull String json) implements CustomPacketPayload {

  public static final Type<ClientBoundProjectionSyncPacket> TYPE = new Type<>(DreamVoiceNeoForge.id("client_bound_projection_sync"));

  public static final StreamCodec<FriendlyByteBuf, ClientBoundProjectionSyncPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(DreamVoiceNeoForge.MAX_PACKET_STRING_LENGTH), ClientBoundProjectionSyncPacket::json,
    ClientBoundProjectionSyncPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}

