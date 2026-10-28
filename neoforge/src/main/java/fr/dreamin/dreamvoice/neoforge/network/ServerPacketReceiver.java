package fr.dreamin.dreamvoice.neoforge.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ServerPacketReceiver<T extends CustomPacketPayload> {
  void receive(final @NotNull T packet, final @NotNull IPayloadContext context);
}
