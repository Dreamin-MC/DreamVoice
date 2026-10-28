package fr.dreamin.dreamvoice.neoforge.network.model.radio;

import fr.dreamin.dreamvoice.neoforge.DreamVoiceNeoForge;
import fr.dreamin.dreamvoice.neoforge.network.annotation.DreamPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@DreamPacket(type = DreamPacket.Type.SERVER_BOUND_PLAY)
public record ServerBoundRadioCreatePacket(@NotNull String json) implements CustomPacketPayload {

  public static final Type<ServerBoundRadioCreatePacket> TYPE = new Type<>(DreamVoiceNeoForge.id("server_bound_radio_create"));

  public static final StreamCodec<FriendlyByteBuf, ServerBoundRadioCreatePacket> CODEC = StreamCodec.composite(
    ByteBufCodecs.stringUtf8(DreamVoiceNeoForge.MAX_PACKET_STRING_LENGTH), ServerBoundRadioCreatePacket::json,
    ServerBoundRadioCreatePacket::new
  );

  @Override
  public @NotNull Type<? extends CustomPacketPayload> type() {
    return TYPE;
  }
}

