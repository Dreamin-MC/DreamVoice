package fr.dreamin.dreamvoice.fabric.network.model.filter;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundFilterClearPacket(@NotNull String playerUuid) implements CustomPacketPayload {

  public static final Type<ServerBoundFilterClearPacket> TYPE = new Type<>(DreamVoiceFabric.id("server_bound_filter_clear"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundFilterClearPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(128), ServerBoundFilterClearPacket::playerUuid,
    ServerBoundFilterClearPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
