package fr.dreamin.dreamvoice.fabric.network.model.filter;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.CLIENT_BOUND_PLAY)
public record ClientBoundFilterSyncPacket(@NotNull String playerUuid, @NotNull String activeFiltersJson) implements CustomPacketPayload {

  public static final Type<ClientBoundFilterSyncPacket> TYPE = new Type<>(DreamVoiceFabric.id("client_bound_filter_sync"));

  public static final StreamCodec<FriendlyByteBuf, ClientBoundFilterSyncPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(128), ClientBoundFilterSyncPacket::playerUuid,
    ByteBufCodecs.stringUtf8(DreamVoiceFabric.MAX_PACKET_STRING_LENGTH), ClientBoundFilterSyncPacket::activeFiltersJson,
    ClientBoundFilterSyncPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
