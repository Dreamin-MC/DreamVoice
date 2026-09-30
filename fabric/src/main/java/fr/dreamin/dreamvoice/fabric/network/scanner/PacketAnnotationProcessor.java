package fr.dreamin.dreamvoice.fabric.network.scanner;

import fr.dreamin.dreamvoice.fabric.network.annotation.DreamPacket;
import lombok.RequiredArgsConstructor;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public final class PacketAnnotationProcessor {

  private final @NotNull Set<Class<?>> preScannedClasses;
  private final @NotNull Logger log;

  public void process() {
    final var start = System.currentTimeMillis();

    final var loaded = new AtomicInteger();
    final var failed = new AtomicInteger();

    scanDreamPacketClasses(this.preScannedClasses, loaded, failed);

    final var end = System.currentTimeMillis();
    this.log.info("[DreamPacket] Loaded {} packets ({} failed) in {}ms", loaded.get(), failed.get(), end - start);
  }

  private void scanDreamPacketClasses(
    final @NotNull Set<Class<?>> classes,
    final @NotNull AtomicInteger loaded,
    final @NotNull AtomicInteger failed
  ) {
    for (final var clazz : classes) {
      try {
        if (!clazz.isAnnotationPresent(DreamPacket.class))
          continue;

        registerPacket(clazz);
        loaded.incrementAndGet();
      } catch (final Exception e) {
        failed.incrementAndGet();
        this.log.error("[DreamPacket] Failed to load packet {}: {}", clazz.getName(), e.getMessage());
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void registerPacket(final @NotNull Class<?> clazz) throws ReflectiveOperationException {
    final var annotation = clazz.getAnnotation(DreamPacket.class);

    final var type = readStaticField(clazz, "TYPE", CustomPacketPayload.Type.class);
    final var codec = readStaticField(clazz, "CODEC", StreamCodec.class);

    switch (annotation.type()) {
      case CLIENT_BOUND_PLAY -> PayloadTypeRegistry.clientboundPlay().register(type, codec);
      case CLIENT_BOUND_CONFIGURATION -> PayloadTypeRegistry.clientboundConfiguration().register(type, codec);
      case SERVER_BOUND_PLAY -> PayloadTypeRegistry.serverboundPlay().register(type, codec);
      case SERVER_BOUND_CONFIGURATION -> PayloadTypeRegistry.serverboundConfiguration().register(type, codec);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T readStaticField(
    final @NotNull Class<?> owner,
    final @NotNull String fieldName,
    final @NotNull Class<T> expectedType
  ) throws ReflectiveOperationException {
    final var field = owner.getDeclaredField(fieldName);

    if (!Modifier.isStatic(field.getModifiers()))
      throw new IllegalStateException(owner.getName() + "#" + fieldName + " must be static");

    field.setAccessible(true);
    final var value = field.get(null);

    if (value == null)
      throw new IllegalStateException(owner.getName() + "#" + fieldName + " is null");

    if (!expectedType.isInstance(value))
      throw new IllegalStateException(
        owner.getName() + "#" + fieldName + " must be of type " + expectedType.getName()
      );

    return (T) value;
  }
}
