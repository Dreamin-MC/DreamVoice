package fr.dreamin.dreamvoice.common.filter.loader;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * On-the-fly compiler for {@code .java} voice filter source files using the JDK JavaCompiler.
 */
public final class JavaSourceCompiler {

  private final @NotNull VoicePlatform platform;
  private final @NotNull ClassLoader parentClassLoader;

  public JavaSourceCompiler(final @NotNull VoicePlatform platform, final @NotNull ClassLoader parentClassLoader) {
    this.platform = platform;
    this.parentClassLoader = parentClassLoader;
  }

  /**
   * Compiles a .java source file into a {@link VoiceFilter} instance.
   *
   * @param sourceFile the .java file
   * @return compiled VoiceFilter instance or null if compilation failed
   */
  public @Nullable VoiceFilter compileFilter(final @NotNull File sourceFile) {
    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      this.platform.logWarning("[VoiceFilter] No JavaCompiler available on this JVM runtime. Ensure you are running on a full JDK to compile .java filters on-the-fly.");
      return null;
    }

    try {
      final var sourceCode = Files.readString(sourceFile.toPath());
      final var className = extractClassName(sourceFile, sourceCode);

      final var diagnostics = new DiagnosticCollector<JavaFileObject>();

      try (final var standardFileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
        final var classpathFiles = resolveClasspath(standardFileManager);
        if (!classpathFiles.isEmpty()) {
          try {
            standardFileManager.setLocation(javax.tools.StandardLocation.CLASS_PATH, classpathFiles);
          } catch (final IOException e) {
            this.platform.logWarning("[VoiceFilter] Failed to set compiler standardFileManager classpath: " + e.getMessage());
          }
        }

        final var byteCodeMap = new HashMap<String, ByteArrayOutputStream>();

        final var fileManager = new ForwardingJavaFileManager<JavaFileManager>(standardFileManager) {
          @Override
          public JavaFileObject getJavaFileForOutput(
            final Location location,
            final String name,
            final JavaFileObject.Kind kind,
            final FileObject sibling
          ) {
            return new SimpleJavaFileObject(URI.create("mem:///" + name.replace('.', '/') + kind.extension), kind) {
              @Override
              public OutputStream openOutputStream() {
                final var baos = new ByteArrayOutputStream();
                byteCodeMap.put(name, baos);
                return baos;
              }
            };
          }
        };

        final var compilationUnit = new SimpleJavaFileObject(
          URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
          JavaFileObject.Kind.SOURCE
        ) {
          @Override
          public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
            return sourceCode;
          }
        };

        final var options = new ArrayList<String>();
        options.add("-proc:none");
        if (!classpathFiles.isEmpty()) {
          final var cpString = classpathFiles.stream()
            .map(File::getAbsolutePath)
            .collect(java.util.stream.Collectors.joining(File.pathSeparator));
          options.add("-classpath");
          options.add(cpString);
        }

        final var task = compiler.getTask(
          null,
          fileManager,
          diagnostics,
          options,
          null,
          Collections.singletonList(compilationUnit)
        );

        final var success = task.call();
        if (!success) {
          this.platform.logWarning("[VoiceFilter] Failed to compile " + sourceFile.getName() + ":");
          for (final var diag : diagnostics.getDiagnostics())
            this.platform.logWarning(String.format("  Line %d: %s", diag.getLineNumber(), diag.getMessage(null)));
          return null;
        }

        final var loadedClass = getLoadedClass(byteCodeMap, className);
        if (!VoiceFilter.class.isAssignableFrom(loadedClass)) {
          this.platform.logWarning("[VoiceFilter] Class " + className + " in " + sourceFile.getName() + " does not implement VoiceFilter.");
          return null;
        }

        final var constructor = loadedClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (VoiceFilter) constructor.newInstance();
      }
    } catch (final Exception e) {
      this.platform.logError("[VoiceFilter] Error compiling filter " + sourceFile.getName() + ": " + e.getMessage(), e);
      return null;
    }
  }

  private Class<?> getLoadedClass(HashMap<String, ByteArrayOutputStream> byteCodeMap, String className) throws ClassNotFoundException {
    final var customLoader = new ClassLoader(this.parentClassLoader) {
      @Override
      protected Class<?> findClass(final String name) throws ClassNotFoundException {
        final var bytes = byteCodeMap.get(name);
        if (bytes != null) {
          final var b = bytes.toByteArray();
          return defineClass(name, b, 0, b.length);
        }
        return super.findClass(name);
      }
    };

    return customLoader.loadClass(className);
  }

  private @NotNull List<File> resolveClasspath(final @NotNull javax.tools.StandardJavaFileManager standardFileManager) {
    final var files = new java.util.LinkedHashSet<File>();

    try {
      final var existing = standardFileManager.getLocation(javax.tools.StandardLocation.CLASS_PATH);
      if (existing != null) {
        for (final var f : existing) {
          if (f != null && f.exists())
            files.add(f);
        }
      }
    } catch (final Throwable ignored) {}

    addClassLocation(JavaSourceCompiler.class, files);
    addClassLocation(VoiceFilter.class, files);
    addClassByName("fr.dreamin.dreamvoice.api.player.model.VPlayer", files);
    addClassByName("org.jetbrains.annotations.NotNull", files);
    addClassByName("org.jetbrains.annotations.Nullable", files);

    var cl = this.parentClassLoader;
    while (cl != null) {
      if (cl instanceof java.net.URLClassLoader ucl) {
        for (final var url : ucl.getURLs()) {
          try {
            final var f = new File(url.toURI());
            if (f.exists())
              files.add(f);
          } catch (final Throwable ignored) {}
        }
      }
      cl = cl.getParent();
    }

    final var sysCp = System.getProperty("java.class.path");
    if (sysCp != null && !sysCp.isBlank()) {
      for (final var entry : sysCp.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
        if (!entry.isBlank()) {
          final var f = new File(entry);
          if (f.exists())
            files.add(f);
        }
      }
    }

    return new ArrayList<>(files);
  }

  private void addClassLocation(final @Nullable Class<?> clazz, final @NotNull Set<File> files) {
    if (clazz == null)
      return;
    try {
      final var cs = clazz.getProtectionDomain().getCodeSource();
      if (cs != null && cs.getLocation() != null) {
        final var file = new File(cs.getLocation().toURI());
        if (file.exists())
          files.add(file);
      }
    } catch (final Throwable ignored) {}
  }

  private void addClassByName(final @NotNull String className, final @NotNull Set<File> files) {
    try {
      final var clazz = Class.forName(className, false, this.parentClassLoader);
      addClassLocation(clazz, files);
    } catch (final Throwable ignored) {}
  }

  private @NotNull String extractClassName(final @NotNull File file, final @NotNull String code) {
    var packageName = "";
    final var packageMatcher = java.util.regex.Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_.]+);", java.util.regex.Pattern.MULTILINE).matcher(code);
    if (packageMatcher.find())
      packageName = packageMatcher.group(1) + ".";

    final var simpleName = file.getName().replace(".java", "");
    return packageName + simpleName;
  }

}
