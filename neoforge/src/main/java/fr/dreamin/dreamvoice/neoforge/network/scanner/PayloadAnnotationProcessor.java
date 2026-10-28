package fr.dreamin.dreamvoice.neoforge.network.scanner;

import fr.dreamin.dreamvoice.neoforge.DreamVoiceNeoForge;
import fr.dreamin.dreamvoice.neoforge.network.ServerPacketReceiver;
import fr.dreamin.dreamvoice.neoforge.network.annotation.DreamPacket;
import fr.dreamin.dreamvoice.neoforge.network.annotation.DreamServerReceiver;
import lombok.RequiredArgsConstructor;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public final class PayloadAnnotationProcessor {

  private final @NotNull Set<Class<?>> preScannedClasses;
  private final @NotNull Logger log;

  private final Map<Class<? extends CustomPacketPayload>, List<ReceiverInvoker>> receiverMap = new HashMap<>();
  private final Map<Class<?>, Object> instanceCache = new ConcurrentHashMap<>();

  public void process(final @NotNull RegisterPayloadHandlersEvent event) {
    final var start = System.currentTimeMillis();

    scanReceivers();

    final var registrar = event.registrar("1");
    final var loadedPackets = new AtomicInteger();
    final var failedPackets = new AtomicInteger();

    scanAndRegisterPackets(registrar, loadedPackets, failedPackets);

    final var end = System.currentTimeMillis();
    this.log.info("[NeoForge Payload] Registered {} packets and {} receivers in {}ms",
      loadedPackets.get(), this.receiverMap.values().stream().mapToInt(List::size).sum(), end - start);
  }

  private void scanReceivers() {
    for (final var clazz : this.preScannedClasses) {
      if (Modifier.isAbstract(clazz.getModifiers()))
        continue;

      // 1. Process Class-level Receiver
      if (clazz.isAnnotationPresent(DreamServerReceiver.class) || ServerPacketReceiver.class.isAssignableFrom(clazz)) {
        try {
          final var annotation = clazz.getAnnotation(DreamServerReceiver.class);
          final Class<? extends CustomPacketPayload> packetClass =
            (annotation != null && annotation.value() != DreamServerReceiver.VoidPayload.class)
              ? annotation.value()
              : getGenericPacketTypeFromInterface(clazz);

          if (packetClass != null) {
            final var instance = getInstance(clazz);
            if (instance instanceof ServerPacketReceiver receiver) {
              this.receiverMap.computeIfAbsent(packetClass, k -> new ArrayList<>())
                .add((packet, player, server, context) -> receiver.receive(packet, context));
            }
          }
        } catch (final Exception e) {
          this.log.error("[NeoForge Payload] Failed to register class receiver {}: {}", clazz.getName(), e.getMessage());
        }
      }

      // 2. Process Method-level Receivers
      for (final var method : clazz.getDeclaredMethods()) {
        if (method.isAnnotationPresent(DreamServerReceiver.class)) {
          try {
            method.setAccessible(true);
            final var annotation = method.getAnnotation(DreamServerReceiver.class);
            final Class<? extends CustomPacketPayload> packetClass;

            if (annotation != null && annotation.value() != DreamServerReceiver.VoidPayload.class) {
              packetClass = annotation.value();
            } else {
              Class<? extends CustomPacketPayload> found = null;
              for (final var paramType : method.getParameterTypes()) {
                if (CustomPacketPayload.class.isAssignableFrom(paramType)) {
                  found = (Class<? extends CustomPacketPayload>) paramType;
                  break;
                }
              }
              packetClass = found;
            }

            if (packetClass != null) {
              final var isStatic = Modifier.isStatic(method.getModifiers());
              final var instance = isStatic ? null : getInstance(clazz);

              this.receiverMap.computeIfAbsent(packetClass, k -> new ArrayList<>())
                .add((packet, player, server, context) -> {
                  try {
                    final var args = resolveMethodArgs(method, packet, player, server, context);
                    method.invoke(instance, args);
                  } catch (final Exception e) {
                    this.log.error("[NeoForge Payload] Error executing receiver method {}#{}: {}",
                      clazz.getName(), method.getName(), e.getMessage(), e);
                  }
                });
            }
          } catch (final Exception e) {
            this.log.error("[NeoForge Payload] Failed to register method receiver {}#{}: {}",
              clazz.getName(), method.getName(), e.getMessage());
          }
        }
      }
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void scanAndRegisterPackets(
    final @NotNull PayloadRegistrar registrar,
    final @NotNull AtomicInteger loaded,
    final @NotNull AtomicInteger failed
  ) {
    for (final var clazz : this.preScannedClasses) {
      if (!clazz.isAnnotationPresent(DreamPacket.class))
        continue;

      try {
        final var annotation = clazz.getAnnotation(DreamPacket.class);
        final var type = readStaticField(clazz, "TYPE", CustomPacketPayload.Type.class);
        final var codec = readStaticField(clazz, "CODEC", StreamCodec.class);

        switch (annotation.type()) {
          case CLIENT_BOUND_PLAY -> registrar.playToClient(type, codec, (packet, context) -> {});
          case SERVER_BOUND_PLAY -> registrar.playToServer(type, codec, (packet, context) -> {
            context.enqueueWork(() -> {
              final var player = context.player() instanceof ServerPlayer sp ? sp : null;
              final var server = player != null ? player.level().getServer() : (DreamVoiceNeoForge.getInstance() != null ? DreamVoiceNeoForge.getInstance().getServer() : null);
              final var invokers = this.receiverMap.get(packet.getClass());
              if (invokers != null) {
                for (final var invoker : invokers) {
                  try {
                    invoker.invoke(packet, player, server, context);
                  } catch (final Exception ex) {
                    this.log.error("[NeoForge Payload] Error in packet receiver for {}: {}",
                      packet.getClass().getSimpleName(), ex.getMessage(), ex);
                  }
                }
              }
            });
          });
          case CLIENT_BOUND_CONFIGURATION -> registrar.configurationToClient(type, codec, (packet, context) -> {});
          case SERVER_BOUND_CONFIGURATION -> registrar.configurationToServer(type, codec, (packet, context) -> {});
        }

        loaded.incrementAndGet();
      } catch (final Exception e) {
        failed.incrementAndGet();
        this.log.error("[NeoForge Payload] Failed to register packet {}: {}", clazz.getName(), e.getMessage());
      }
    }
  }

  private Object[] resolveMethodArgs(
    final @NotNull Method method,
    final @NotNull CustomPacketPayload packet,
    final ServerPlayer player,
    final MinecraftServer server,
    final IPayloadContext context
  ) {
    final var paramTypes = method.getParameterTypes();
    final var args = new Object[paramTypes.length];

    for (var i = 0; i < paramTypes.length; i++) {
      final var pType = paramTypes[i];
      if (CustomPacketPayload.class.isAssignableFrom(pType))
        args[i] = packet;
      else if (IPayloadContext.class.isAssignableFrom(pType))
        args[i] = context;
      else if (ServerPlayer.class.isAssignableFrom(pType))
        args[i] = player;
      else if (MinecraftServer.class.isAssignableFrom(pType))
        args[i] = server;
      else
        args[i] = null;
    }
    return args;
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends CustomPacketPayload> getGenericPacketTypeFromInterface(final @NotNull Class<?> clazz) {
    for (final var iface : clazz.getGenericInterfaces()) {
      if (iface instanceof ParameterizedType pType && pType.getRawType().equals(ServerPacketReceiver.class)) {
        final var typeArg = pType.getActualTypeArguments()[0];
        if (typeArg instanceof Class<?> c && CustomPacketPayload.class.isAssignableFrom(c))
          return (Class<? extends CustomPacketPayload>) c;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static <T> T readStaticField(final @NotNull Class<?> clazz, final @NotNull String fieldName, final @NotNull Class<T> expectedType)
    throws ReflectiveOperationException {
    final var field = clazz.getDeclaredField(fieldName);
    field.setAccessible(true);
    final var val = field.get(null);
    if (!expectedType.isInstance(val))
      throw new IllegalStateException("Field " + fieldName + " in " + clazz.getName() + " is not of type " + expectedType.getName());
    return (T) val;
  }

  private Object getInstance(final @NotNull Class<?> clazz) {
    return this.instanceCache.computeIfAbsent(clazz, c -> {
      try {
        final var ctor = c.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
      } catch (final Exception e) {
        throw new RuntimeException("Failed to instantiate " + c.getName(), e);
      }
    });
  }

  @FunctionalInterface
  private interface ReceiverInvoker {
    void invoke(CustomPacketPayload packet, ServerPlayer player, MinecraftServer server, IPayloadContext context);
  }
}
