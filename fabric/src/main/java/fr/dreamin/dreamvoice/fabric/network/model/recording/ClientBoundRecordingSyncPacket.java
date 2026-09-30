package fr.dreamin.dreamvoice.fabric.network.model.recording;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.CLIENT_BOUND_PLAY)
public record ClientBoundRecordingSyncPacket(@NotNull String json) implements CustomPacketPayload {

  public static final Type<ClientBoundRecordingSyncPacket> TYPE = new Type<>(DreamVoiceFabric.id("client_bound_recording_sync"));

  public static final StreamCodec<FriendlyByteBuf, ClientBoundRecordingSyncPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(DreamVoiceFabric.MAX_PACKET_STRING_LENGTH), ClientBoundRecordingSyncPacket::json,
    ClientBoundRecordingSyncPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
