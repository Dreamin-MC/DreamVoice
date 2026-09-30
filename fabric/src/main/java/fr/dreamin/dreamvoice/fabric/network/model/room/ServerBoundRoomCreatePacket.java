package fr.dreamin.dreamvoice.fabric.network.model.room;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundRoomCreatePacket(@NotNull String json) implements CustomPacketPayload {

  public static final Type<ServerBoundRoomCreatePacket> TYPE = new Type<>(DreamVoiceFabric.id("server_bound_room_create"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundRoomCreatePacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(DreamVoiceFabric.MAX_PACKET_STRING_LENGTH), ServerBoundRoomCreatePacket::json,
    ServerBoundRoomCreatePacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
