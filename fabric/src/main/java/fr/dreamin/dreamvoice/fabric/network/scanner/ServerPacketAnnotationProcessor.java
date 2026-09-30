package fr.dreamin.dreamvoice.fabric.network.scanner;

import fr.dreamin.dreamvoice.fabric.network.ServerPacketReceiver;
import fr.dreamin.dreamvoice.fabric.network.annotation.DreamServerReceiver;
import fr.dreamin.dreamvoice.fabric.network.utils.InstanceUtils;
import lombok.RequiredArgsConstructor;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@RequiredArgsConstructor
public final class ServerPacketAnnotationProcessor {

  private final Map<Class<?>, Object> instanceCache = new HashMap<>();
  private final @NotNull Set<Class<?>> preScannedClasses;
  private final @NotNull Logger log;

  public void process() {
    final var start = System.currentTimeMillis();

    final var loaded = new AtomicInteger();
    final var failed = new AtomicInteger();

    scanServerReceivers(this.preScannedClasses, loaded, failed);

    final var end = System.currentTimeMillis();
    this.log.info("[DreamServerReceiver] Loaded {} receivers ({} failed) in {}ms", loaded.get(), failed.get(), end - start);
  }

  private void scanServerReceivers(
    final @NotNull Set<Class<?>> classes,
    final @NotNull AtomicInteger loaded,
    final @NotNull AtomicInteger failed
  ) {
    for (final var clazz : classes) {
      if (Modifier.isAbstract(clazz.getModifiers()))
        continue;

      // 1. Process Class-level Receiver
      if (clazz.isAnnotationPresent(DreamServerReceiver.class) || ServerPacketReceiver.class.isAssignableFrom(clazz)) {
        try {
          registerClassReceiver(clazz);
          loaded.incrementAndGet();
        } catch (final Exception e) {
          failed.incrementAndGet();
          this.log.error("[DreamServerReceiver] Failed to load class receiver {}: {}", clazz.getName(), e.getMessage());
        }
      }

      // 2. Process Method-level Receivers
      for (final var method : clazz.getDeclaredMethods()) {
        if (method.isAnnotationPresent(DreamServerReceiver.class)) {
          try {
            registerMethodReceiver(clazz, method);
            loaded.incrementAndGet();
          } catch (final Exception e) {
            failed.incrementAndGet();
            this.log.error("[DreamServerReceiver] Failed to load method receiver {}#{}: {}", clazz.getName(), method.getName(), e.getMessage());
          }
        }
      }
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void registerClassReceiver(final @NotNull Class<?> clazz) throws ReflectiveOperationException {
    final var annotation = clazz.getAnnotation(DreamServerReceiver.class);
    final Class<? extends CustomPacketPayload> packetClass =
      (annotation != null && annotation.value() != DreamServerReceiver.VoidPayload.class)
        ? annotation.value()
        : getGenericPacketTypeFromInterface(clazz);

    if (packetClass == null)
      throw new IllegalStateException("Could not determine CustomPacketPayload class for " + clazz.getName());

    final var type = readStaticTypeField(packetClass);
    final var instance = getInstance(clazz);

    if (instance instanceof ServerPacketReceiver receiver)
      ServerPlayNetworking.registerGlobalReceiver(type, (packet, context) -> receiver.receive(packet, context));
    else
      throw new IllegalStateException("Class " + clazz.getName() + " has @DreamServerReceiver but does not implement ServerPacketReceiver");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void registerMethodReceiver(final @NotNull Class<?> clazz, final @NotNull Method method) throws ReflectiveOperationException {
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

    if (packetClass == null)
      throw new IllegalStateException("Could not determine CustomPacketPayload class for method " + clazz.getName() + "#" + method.getName());

    final var type = readStaticTypeField(packetClass);
    final var isStatic = Modifier.isStatic(method.getModifiers());
    final var instance = isStatic ? null : getInstance(clazz);

    ServerPlayNetworking.registerGlobalReceiver(type, (packet, context) ->
      context.server().execute(() -> {
        try {
          final var args = resolveMethodArgs(method, packet, context);
          method.invoke(instance, args);
        } catch (final Exception e) {
          log.error("[DreamServerReceiver] Error executing receiver method {}#{}: {}", clazz.getName(), method.getName(), e.getMessage(), e);
        }
      })
    );
  }

  private Object[] resolveMethodArgs(
    final @NotNull Method method,
    final @NotNull CustomPacketPayload packet,
    final @NotNull ServerPlayNetworking.Context context
  ) {
    final var paramTypes = method.getParameterTypes();
    final var args = new Object[paramTypes.length];

    for (var i = 0; i < paramTypes.length; i++) {
      final var pType = paramTypes[i];
      if (CustomPacketPayload.class.isAssignableFrom(pType))
        args[i] = packet;
      else if (ServerPlayNetworking.Context.class.isAssignableFrom(pType))
        args[i] = context;
      else if (ServerPlayer.class.isAssignableFrom(pType))
        args[i] = context.player();
      else if (MinecraftServer.class.isAssignableFrom(pType))
        args[i] = context.server();
      else
        args[i] = null;
    }

    return args;
  }

  private Class<? extends CustomPacketPayload> getGenericPacketTypeFromInterface(final @NotNull Class<?> clazz) {
    for (final var iface : clazz.getGenericInterfaces()) {
      if (iface instanceof ParameterizedType pt && pt.getRawType().equals(ServerPacketReceiver.class)) {
        final var typeArg = pt.getActualTypeArguments()[0];
        if (typeArg instanceof Class<?> c && CustomPacketPayload.class.isAssignableFrom(c)) {
          @SuppressWarnings("unchecked")
          final var res = (Class<? extends CustomPacketPayload>) c;
          return res;
        }
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> readStaticTypeField(
    final @NotNull Class<T> packetClass
  ) throws ReflectiveOperationException {
    final var field = packetClass.getDeclaredField("TYPE");
    field.setAccessible(true);
    final var value = field.get(null);

    if (value == null)
      throw new IllegalStateException(packetClass.getName() + "#TYPE is null");

    if (!(value instanceof CustomPacketPayload.Type<?>))
      throw new IllegalStateException(packetClass.getName() + "#TYPE must be of type CustomPacketPayload.Type");

    return (CustomPacketPayload.Type<T>) value;
  }

  private Object getInstance(final @NotNull Class<?> clazz) {
    return this.instanceCache.computeIfAbsent(clazz, InstanceUtils::createInstance);
  }
}
