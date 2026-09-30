package fr.dreamin.dreamvoice.fabric.network.model.radio;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundRadioJoinPacket(@NotNull String channelName, @NotNull String playerUuid) implements CustomPacketPayload {

  public static final Type<ServerBoundRadioJoinPacket> TYPE = new Type<>(DreamVoiceFabric.id("server_bound_radio_join"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundRadioJoinPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(128), ServerBoundRadioJoinPacket::channelName,
    ByteBufCodecs.stringUtf8(128), ServerBoundRadioJoinPacket::playerUuid,
    ServerBoundRadioJoinPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
