package fr.dreamin.dreamvoice.fabric.network.model.recording;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundRecordingPlayPacket(@NotNull String recordingUuid, @NotNull String targetPlayerUuid) implements CustomPacketPayload {

  public static final Type<ServerBoundRecordingPlayPacket> TYPE = new Type<>(DreamVoiceFabric.id("server_bound_recording_play"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundRecordingPlayPacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(128), ServerBoundRecordingPlayPacket::recordingUuid,
    ByteBufCodecs.stringUtf8(128), ServerBoundRecordingPlayPacket::targetPlayerUuid,
    ServerBoundRecordingPlayPacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}
