package fr.dreamin.dreamvoice.fabric.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface ServerPacketReceiver<T extends CustomPacketPayload> {
  void receive(final @NotNull T packet, final @NotNull ServerPlayNetworking.Context context);
}
