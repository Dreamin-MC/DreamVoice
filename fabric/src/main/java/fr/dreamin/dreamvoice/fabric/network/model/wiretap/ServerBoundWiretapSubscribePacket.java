package fr.dreamin.dreamvoice.fabric.network.model.wiretap;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundWiretapSubscribePacket(@NotNull String wiretapId, @NotNull String playerUuid) implements CustomPacketPayload {

  public static final Type<ServerBoundWiretapSubscribePacket> TYPE = new Type<>(DreamVoiceFabric.id("server_bound_wiretap_subscribe"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundWiretapSubscribePacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(128), ServerBoundWiretapSubscribePacket::wiretapId,
    ByteBufCodecs.stringUtf8(128), ServerBoundWiretapSubscribePacket::playerUuid,
    ServerBoundWiretapSubscribePacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
