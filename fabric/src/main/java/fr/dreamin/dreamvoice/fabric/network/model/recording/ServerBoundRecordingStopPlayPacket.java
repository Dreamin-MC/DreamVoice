package fr.dreamin.dreamvoice.fabric.network.model.recording;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundRecordingStopPlayPacket(@NotNull String recordingUuid) implements CustomPacketPayload {

  public static final Type<ServerBoundRecordingStopPlayPacket> TYPE = new Type<>(DreamVoiceFabric.id("server_bound_recording_stop_play"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundRecordingStopPlayPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(128), ServerBoundRecordingStopPlayPacket::recordingUuid,
    ServerBoundRecordingStopPlayPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
