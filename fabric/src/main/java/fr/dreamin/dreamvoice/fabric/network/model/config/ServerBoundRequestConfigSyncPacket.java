package fr.dreamin.dreamvoice.fabric.network.model.config;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundRequestConfigSyncPacket(@NotNull String targetModule) implements CustomPacketPayload {

  public static final Type<ServerBoundRequestConfigSyncPacket> TYPE = new Type<>(DreamVoiceFabric.id("server_bound_request_config_sync"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundRequestConfigSyncPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(64), ServerBoundRequestConfigSyncPacket::targetModule,
    ServerBoundRequestConfigSyncPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
