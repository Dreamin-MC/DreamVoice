package fr.dreamin.dreamvoice.neoforge.scanner;

import fr.dreamin.dreamvoice.neoforge.DreamVoiceNeoForge;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
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

    Path root = null;
    FileSystem fsToClose = null;

    final var modFileInfo = ModList.get().getModFileById(modId);
    if (modFileInfo != null) {
      final var filePath = modFileInfo.getFile().getFilePath();
      if (Files.isDirectory(filePath)) {
        root = filePath;
      } else {
        try {
          fsToClose = FileSystems.newFileSystem(filePath, (ClassLoader) null);
          root = fsToClose.getPath("/");
        } catch (final Exception ignored) {}
      }
    }

    if (root == null) {
      final var codeSource = DreamVoiceNeoForge.class.getProtectionDomain().getCodeSource();
      if (codeSource != null) {
        final var loc = codeSource.getLocation();
        try {
          final var p = Paths.get(loc.toURI());
          if (Files.isDirectory(p)) {
            root = p;
          } else {
            fsToClose = FileSystems.newFileSystem(p, (ClassLoader) null);
            root = fsToClose.getPath("/");
          }
        } catch (final Exception ignored) {}
      }
    }

    if (root == null)
      return classes;

    try {
      final var start = root.resolve(packagePath);
      if (!Files.exists(start))
        return classes;

      final var classLoader = ClassScanner.class.getClassLoader();
      final var context = new ScanContext(packageName, recursive, classes, classLoader);
      scanPath(start, root, context);
    } finally {
      if (fsToClose != null) {
        try {
          fsToClose.close();
        } catch (final Exception ignored) {}
      }
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

  private static String toClassName(final @NotNull Path root, final @NotNull Path classFilePath) {
    var relative = root.relativize(classFilePath).toString();
    if (relative.startsWith("/") || relative.startsWith("\\"))
      relative = relative.substring(1);

    return relative
      .replace('/', '.')
      .replace('\\', '.')
      .replaceAll("\\.class$", "");
  }

  private static boolean matchesPackageScope(
    final @NotNull String className,
    final @NotNull String scanPackage,
    final boolean recursive
  ) {
    if (!className.startsWith(scanPackage + "."))
      return false;

    if (recursive)
      return true;

    final var sub = className.substring(scanPackage.length() + 1);
    return !sub.contains(".");
  }

  private record ScanContext(
    String scanPackage,
    boolean recursive,
    Set<Class<?>> classes,
    ClassLoader classLoader
  ) {}
}
