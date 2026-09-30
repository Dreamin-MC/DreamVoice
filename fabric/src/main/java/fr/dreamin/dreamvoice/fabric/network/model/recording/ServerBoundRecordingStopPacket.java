package fr.dreamin.dreamvoice.fabric.network.model.recording;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundRecordingStopPacket(@NotNull String targetPlayerUuid, boolean giveCassette) implements CustomPacketPayload {

  public static final Type<ServerBoundRecordingStopPacket> TYPE = new Type<>(DreamVoiceFabric.id("server_bound_recording_stop"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundRecordingStopPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(128), ServerBoundRecordingStopPacket::targetPlayerUuid,
    ByteBufCodecs.BOOL, ServerBoundRecordingStopPacket::giveCassette,
    ServerBoundRecordingStopPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
