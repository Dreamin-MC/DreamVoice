package fr.dreamin.dreamvoice.fabric.network.model.recording;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundCassetteCreatePacket(@NotNull String recordingUuid, @NotNull String customTitle) implements CustomPacketPayload {

  public static final Type<ServerBoundCassetteCreatePacket> TYPE = new Type<>(DreamVoiceFabric.id("server_bound_cassette_create"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundCassetteCreatePacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(128), ServerBoundCassetteCreatePacket::recordingUuid,
    ByteBufCodecs.stringUtf8(256), ServerBoundCassetteCreatePacket::customTitle,
    ServerBoundCassetteCreatePacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
