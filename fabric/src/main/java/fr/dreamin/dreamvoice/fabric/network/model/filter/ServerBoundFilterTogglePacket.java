package fr.dreamin.dreamvoice.fabric.network.model.filter;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundFilterTogglePacket(@NotNull String playerUuid, @NotNull String filterName) implements CustomPacketPayload {

  public static final Type<ServerBoundFilterTogglePacket> TYPE = new Type<>(DreamVoiceFabric.id("server_bound_filter_toggle"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundFilterTogglePacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(128), ServerBoundFilterTogglePacket::playerUuid,
    ByteBufCodecs.stringUtf8(128), ServerBoundFilterTogglePacket::filterName,
    ServerBoundFilterTogglePacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
