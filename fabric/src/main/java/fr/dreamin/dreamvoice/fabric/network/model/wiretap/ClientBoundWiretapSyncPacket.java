package fr.dreamin.dreamvoice.fabric.network.model.wiretap;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.CLIENT_BOUND_PLAY)
public record ClientBoundWiretapSyncPacket(@NotNull String json) implements CustomPacketPayload {

  public static final Type<ClientBoundWiretapSyncPacket> TYPE = new Type<>(DreamVoiceFabric.id("client_bound_wiretap_sync"));

  public static final StreamCodec<FriendlyByteBuf, ClientBoundWiretapSyncPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(DreamVoiceFabric.MAX_PACKET_STRING_LENGTH), ClientBoundWiretapSyncPacket::json,
    ClientBoundWiretapSyncPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
