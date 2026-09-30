package fr.dreamin.dreamvoice.fabric.network.utils;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;

public final class InstanceUtils {

  public static <T> T createInstance(final @NotNull Class<?> clazz) {
    try {
      final var ctor = resolveConstructor(clazz);
      final var args = resolveConstructorArgs(ctor);

      @SuppressWarnings("unchecked")
      final var instance = (T) ctor.newInstance(args);

      return instance;
    } catch (final Exception e) {
      throw new RuntimeException("[InstanceUtils] Failed to create instance of %s: %s".formatted(clazz.getName(), e.getMessage()), e);
    }
  }

  private static Constructor<?> resolveConstructor(final @NotNull Class<?> clazz) {
    final var constructors = clazz.getDeclaredConstructors();

    for (final var c : constructors) {
      var compatible = true;
      for (final var param : c.getParameterTypes())
        if (!DreamVoiceFabric.class.isAssignableFrom(param)) {
          compatible = false;
          break;
        }
      if (compatible && c.getParameterCount() > 0) {
        c.setAccessible(true);
        return c;
      }
    }

    try {
      final var c = clazz.getDeclaredConstructor();
      c.setAccessible(true);
      return c;
    } catch (final Exception e) {
      throw new RuntimeException("[InstanceUtils] No suitable constructor found for " + clazz.getName(), e);
    }
  }

  private static Object[] resolveConstructorArgs(final @NotNull Constructor<?> constructor) {
    final var params = constructor.getParameterTypes();
    final var args = new Object[params.length];

    for (var i = 0; i < params.length; i++) {
      final var param = params[i];
      if (DreamVoiceFabric.class.isAssignableFrom(param)) {
        args[i] = DreamVoiceFabric.getInstance();
        continue;
      }
      throw new RuntimeException(
        String.format("[InstanceUtils] Unable to resolve dependency: %s for constructor %s", param.getName(), constructor)
      );
    }

    return args;
  }
}
