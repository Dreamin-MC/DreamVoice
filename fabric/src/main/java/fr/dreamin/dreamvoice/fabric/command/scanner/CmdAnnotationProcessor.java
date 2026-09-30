package fr.dreamin.dreamvoice.fabric.command.scanner;

import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.command.annotation.DreamCmd;
import net.minecraft.commands.CommandSourceStack;
import org.incendo.cloud.annotations.AnnotationParser;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class CmdAnnotationProcessor {

  private final Map<Class<?>, Object> instanceCache = new HashMap<>();

  private final @NotNull AnnotationParser<CommandSourceStack> annotationParser;
  private final @NotNull Set<Class<?>> preScannedClasses;
  private final @NotNull Logger log;

  public CmdAnnotationProcessor(
    final @NotNull AnnotationParser<CommandSourceStack> annotationParser,
    final @NotNull Set<Class<?>> preScannedClasses,
    final @NotNull Logger log
  ) {
    this.annotationParser = annotationParser;
    this.preScannedClasses = preScannedClasses;
    this.log = log;
  }

  public void process() {
    final var start = System.currentTimeMillis();

    final var loaded = new AtomicInteger();
    final var failed = new AtomicInteger();

    scanDreamCmdClasses(this.preScannedClasses, loaded, failed);

    final var end = System.currentTimeMillis();
    this.log.info("[DreamCmd] Loaded {} commands ({} failed) in {}ms", loaded.get(), failed.get(), end - start);
  }

  private void scanDreamCmdClasses(final @NotNull Set<Class<?>> classes, final @NotNull AtomicInteger loaded, final @NotNull AtomicInteger failed) {
    for (final var clazz : classes) {
      try {
        if (!clazz.isAnnotationPresent(DreamCmd.class))
          continue;

        registerCmd(clazz);
        loaded.incrementAndGet();
      } catch (Exception e) {
        failed.incrementAndGet();
        this.log.error("[DreamCmd] Failed to load command {}: {}", clazz.getName(), e.getMessage(), e);
      }
    }
  }

  private void registerCmd(final @NotNull Class<?> clazz) {
    final var instance = getInstance(clazz);
    this.annotationParser.parse(instance);
  }

  private Object getInstance(final @NotNull Class<?> clazz) {
    return this.instanceCache.computeIfAbsent(clazz, c -> {
      try {
        for (final var ctor : c.getDeclaredConstructors()) {
          if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0].isAssignableFrom(DreamVoiceFabric.class)) {
            ctor.setAccessible(true);
            return ctor.newInstance(DreamVoiceFabric.getInstance());
          }
        }
        final var ctor = c.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
      } catch (Exception e) {
        throw new RuntimeException("[CmdAnnotationProcessor] Failed to create instance of " + c.getName(), e);
      }
    });
  }
}
