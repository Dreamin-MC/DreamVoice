package fr.dreamin.dreamvoice.fabric.scanner;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class ClassScanner {

  public static Set<Class<?>> getClasses(
    final @NotNull String modId,
    final @NotNull String packageName,
    final boolean recursive
  ) throws IOException, ClassNotFoundException {
    final var packagePath = packageName.replace('.', '/');
    final var classes = new HashSet<Class<?>>();

    final var modContainer = FabricLoader.getInstance()
      .getModContainer(modId)
      .orElseThrow(() -> new IllegalArgumentException("Mod not found: " + modId));

    final var classLoader = ClassScanner.class.getClassLoader();
    final var context = new ScanContext(packageName, recursive, classes, classLoader);

    for (final var root : modContainer.getRootPaths()) {
      final var start = root.resolve(packagePath);

      if (!Files.exists(start))
        continue;

      scanPath(start, root, context);
    }

    return classes;
  }

  private static void scanPath(
    final @NotNull Path start,
    final @NotNull Path root,
    final @NotNull ScanContext context
  ) throws IOException, ClassNotFoundException {
    try (final var stream = context.recursive() ? Files.walk(start) : Files.list(start)) {
      for (final var path : (Iterable<Path>) stream::iterator) {
        if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".class"))
          continue;

        final var className = toClassName(root, path);

        if (className.contains(".mixin."))
          continue;

        if (matchesPackageScope(className, context.scanPackage(), context.recursive()))
          context.classes().add(Class.forName(className, false, context.classLoader()));
      }
    }
  }

  private static @NotNull String toClassName(final @NotNull Path root, final @NotNull Path classFile) {
    var relative = root.relativize(classFile).toString();

    while (relative.startsWith("/") || relative.startsWith("\\"))
      relative = relative.substring(1);

    return relative
      .replace('\\', '.')
      .replace('/', '.')
      .substring(0, relative.length() - 6);
  }

  private record ScanContext(
    @NotNull String scanPackage,
    boolean recursive,
    @NotNull Set<Class<?>> classes,
    @NotNull ClassLoader classLoader
  ) {}

  private static boolean matchesPackageScope(
    final @NotNull String className,
    final @NotNull String scanPackage,
    final boolean recursive
  ) {
    final var prefix = scanPackage + ".";
    if (!className.startsWith(prefix))
      return false;

    if (recursive)
      return true;

    final var relativeName = className.substring(prefix.length());
    return !relativeName.contains(".");
  }
}
