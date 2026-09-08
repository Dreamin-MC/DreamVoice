package fr.dreamin.dreamvoice.core.filter.loader;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
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
import java.util.Map;
import java.util.logging.Logger;

/**
 * On-the-fly compiler for {@code .java} voice filter source files using the JDK JavaCompiler.
 */
public final class JavaSourceCompiler {

  private final @NotNull Logger logger;
  private final @NotNull ClassLoader parentClassLoader;

  public JavaSourceCompiler(final @NotNull Logger logger, final @NotNull ClassLoader parentClassLoader) {
    this.logger = logger;
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
      this.logger.warning("[VoiceFilter] No JavaCompiler available on this JVM runtime. Ensure you are running on a full JDK to compile .java filters on-the-fly.");
      return null;
    }

    try {
      final var sourceCode = Files.readString(sourceFile.toPath());
      final var className = extractClassName(sourceFile, sourceCode);

      final var diagnostics = new DiagnosticCollector<JavaFileObject>();
      final var standardFileManager = compiler.getStandardFileManager(diagnostics, null, null);
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
        this.logger.warning("[VoiceFilter] Failed to compile " + sourceFile.getName() + ":");
        for (final var diag : diagnostics.getDiagnostics())
          this.logger.warning(String.format("  Line %d: %s", diag.getLineNumber(), diag.getMessage(null)));
        return null;
      }

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

      final var loadedClass = customLoader.loadClass(className);
      if (!VoiceFilter.class.isAssignableFrom(loadedClass)) {
        this.logger.warning("[VoiceFilter] Class " + className + " in " + sourceFile.getName() + " does not implement VoiceFilter.");
        return null;
      }

      final var constructor = loadedClass.getDeclaredConstructor();
      constructor.setAccessible(true);
      return (VoiceFilter) constructor.newInstance();
    } catch (final Exception e) {
      this.logger.severe("[VoiceFilter] Error compiling filter " + sourceFile.getName() + ": " + e.getMessage());
      return null;
    }
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
