package fr.dreamin.dreamvoice.fabric.network.model.projection;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundProjectionCreatePacket(@NotNull String json) implements CustomPacketPayload {

  public static final Type<ServerBoundProjectionCreatePacket> TYPE = new Type<>(DreamVoiceFabric.id("server_bound_projection_create"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundProjectionCreatePacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(DreamVoiceFabric.MAX_PACKET_STRING_LENGTH), ServerBoundProjectionCreatePacket::json,
    ServerBoundProjectionCreatePacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
