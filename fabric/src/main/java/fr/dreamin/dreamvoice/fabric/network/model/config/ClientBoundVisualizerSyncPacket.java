package fr.dreamin.dreamvoice.fabric.network.model.config;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.CLIENT_BOUND_PLAY)
public record ClientBoundVisualizerSyncPacket(boolean enabled) implements CustomPacketPayload {

  public static final Type<ClientBoundVisualizerSyncPacket> TYPE = new Type<>(DreamVoiceFabric.id("client_bound_visualizer_sync"));

  public static final StreamCodec<FriendlyByteBuf, ClientBoundVisualizerSyncPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.BOOL, ClientBoundVisualizerSyncPacket::enabled,
    ClientBoundVisualizerSyncPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
