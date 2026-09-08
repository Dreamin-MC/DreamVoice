package fr.dreamin.dreamvoice.core.filter.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.core.filter.pipeline.PipelineVoiceFilter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Exporter responsible for converting any registered {@link VoiceFilter}
 * into .yml, .json, or .java files.
 */
public final class VoiceFilterExporter {

  private final @NotNull Logger logger;
  private final @NotNull File outputDir;
  private final ObjectMapper jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  public VoiceFilterExporter(final @NotNull Logger logger, final @NotNull File outputDir) {
    this.logger = logger;
    this.outputDir = outputDir;
  }

  /**
   * Exports a filter into the requested format (yml, json, java).
   *
   * @param filter the filter to export
   * @param format requested format ("yml", "yaml", "json", "java")
   * @param pluginClassLoader classloader to lookup resource source templates
   * @return exported File or null if failed
   */
  public @Nullable File exportFilter(
    final @NotNull VoiceFilter filter,
    final @NotNull String format,
    final @NotNull ClassLoader pluginClassLoader
  ) {
    if (!this.outputDir.exists())
      this.outputDir.mkdirs();

    final var cleanFormat = format.toLowerCase().trim();
    final var targetFile = new File(this.outputDir, filter.getId() + "." + (cleanFormat.equals("yaml") ? "yml" : cleanFormat));

    try {
      switch (cleanFormat) {
        case "yml", "yaml" -> exportYaml(filter, targetFile);
        case "json" -> exportJson(filter, targetFile);
        case "java" -> exportJava(filter, targetFile, pluginClassLoader);
        default -> {
          this.logger.warning("[VoiceFilter] Unsupported export format '" + format + "'. Use yml, json, or java.");
          return null;
        }
      }
      return targetFile;
    } catch (final Exception e) {
      this.logger.severe("[VoiceFilter] Failed to export filter " + filter.getId() + " to " + targetFile.getName() + ": " + e.getMessage());
      return null;
    }
  }

  private void exportYaml(final @NotNull VoiceFilter filter, final @NotNull File targetFile) throws Exception {
    final var yaml = new YamlConfiguration();
    yaml.set("id", filter.getId());
    yaml.set("name", filter.getName());
    yaml.set("priority", filter.getPriority());
    yaml.set("enabled", true);

    final var pipelineList = new ArrayList<Map<String, Object>>();
    if (filter instanceof PipelineVoiceFilter pipeline) {
      for (final var node : pipeline.getNodes()) {
        final var map = new LinkedHashMap<String, Object>();
        map.put("type", node.getType());
        map.putAll(node.getParameters());
        pipelineList.add(map);
      }
    } else {
      // Map built-in filter to best-effort pipeline representation
      mapBuiltinFilterToPipeline(filter, pipelineList);
    }

    yaml.set("pipeline", pipelineList);
    yaml.save(targetFile);
  }

  private void exportJson(final @NotNull VoiceFilter filter, final @NotNull File targetFile) throws Exception {
    final var root = new LinkedHashMap<String, Object>();
    root.put("id", filter.getId());
    root.put("name", filter.getName());
    root.put("priority", filter.getPriority());
    root.put("enabled", true);

    final var pipelineList = new ArrayList<Map<String, Object>>();
    if (filter instanceof PipelineVoiceFilter pipeline) {
      for (final var node : pipeline.getNodes()) {
        final var map = new LinkedHashMap<String, Object>();
        map.put("type", node.getType());
        map.putAll(node.getParameters());
        pipelineList.add(map);
      }
    } else {
      mapBuiltinFilterToPipeline(filter, pipelineList);
    }

    root.put("pipeline", pipelineList);
    this.jsonMapper.writeValue(targetFile, root);
  }

  private void exportJava(
    final @NotNull VoiceFilter filter,
    final @NotNull File targetFile,
    final @NotNull ClassLoader pluginClassLoader
  ) throws Exception {
    // 1. If it's a known built-in class, check if the source is embedded in resources
    final var simpleName = filter.getClass().getSimpleName();
    final var resourcePath = "modules/filter/filters/" + simpleName + ".java";

    try (final InputStream in = pluginClassLoader.getResourceAsStream(resourcePath)) {
      if (in != null) {
        Files.copy(in, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return;
      }
    }

    // 2. If it is a PipelineVoiceFilter, generate a full self-contained Java source file
    if (filter instanceof PipelineVoiceFilter pipeline) {
      final var generatedSource = generateJavaFromPipeline(pipeline);
      Files.writeString(targetFile.toPath(), generatedSource);
      return;
    }

    // 3. Fallback: generate an annotated template with the filter's metadata
    final var template = """
      package fr.dreamin.dreamvoice.custom;

      import fr.dreamin.dreamvoice.api.filter.annotation.AutoVoiceFilter;
      import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
      import fr.dreamin.dreamvoice.api.player.model.VPlayer;
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.Nullable;

      @AutoVoiceFilter(priority = %d)
      public class %s implements VoiceFilter {

        @Override
        public @NotNull String getId() {
          return "%s";
        }

        @Override
        public @NotNull String getName() {
          return "%s";
        }

        @Override
        public int getPriority() {
          return %d;
        }

        @Override
        public short[] process(final short @NotNull [] samples, final @Nullable VPlayer player) {
          final var out = new short[samples.length];
          for (var i = 0; i < samples.length; i++) {
            out[i] = samples[i]; // Custom DSP algorithm here
          }
          return out;
        }

      }
      """.formatted(
      filter.getPriority(),
      toPascalCase(filter.getId()) + "VoiceFilter",
      filter.getId(),
      filter.getName(),
      filter.getPriority()
    );

    Files.writeString(targetFile.toPath(), template);
  }

  private void mapBuiltinFilterToPipeline(final @NotNull VoiceFilter filter, final @NotNull List<Map<String, Object>> pipelineList) {
    final var id = filter.getId().toLowerCase();
    switch (id) {
      case "muffled" -> pipelineList.add(Map.of("type", "lowpass", "cutoff_hz", 1200.0));
      case "robot" -> {
        pipelineList.add(Map.of("type", "ring_modulator", "freq_hz", 80.0, "mix", 0.75));
        pipelineList.add(Map.of("type", "gain", "multiplier", 1.1));
      }
      case "telephone" -> {
        pipelineList.add(Map.of("type", "highpass", "cutoff_hz", 350.0));
        pipelineList.add(Map.of("type", "lowpass", "cutoff_hz", 3400.0));
        pipelineList.add(Map.of("type", "overdrive", "drive", 1.3));
      }
      case "radio" -> {
        pipelineList.add(Map.of("type", "highpass", "cutoff_hz", 450.0));
        pipelineList.add(Map.of("type", "lowpass", "cutoff_hz", 2700.0));
        pipelineList.add(Map.of("type", "overdrive", "drive", 1.8));
      }
      case "underwater" -> pipelineList.add(Map.of("type", "lowpass", "cutoff_hz", 320.0));
      case "cave" -> pipelineList.add(Map.of("type", "delay", "delay_ms", 120, "decay", 0.35));
      case "ghost" -> {
        pipelineList.add(Map.of("type", "highpass", "cutoff_hz", 700.0));
        pipelineList.add(Map.of("type", "delay", "delay_ms", 180, "decay", 0.45));
      }
      case "gasmask" -> {
        pipelineList.add(Map.of("type", "lowpass", "cutoff_hz", 900.0));
        pipelineList.add(Map.of("type", "overdrive", "drive", 1.2));
      }
      case "megaphone" -> {
        pipelineList.add(Map.of("type", "highpass", "cutoff_hz", 500.0));
        pipelineList.add(Map.of("type", "lowpass", "cutoff_hz", 3500.0));
        pipelineList.add(Map.of("type", "overdrive", "drive", 2.2));
        pipelineList.add(Map.of("type", "gain", "multiplier", 1.3));
      }
      default -> pipelineList.add(Map.of("type", "gain", "multiplier", 1.0));
    }
  }

  private @NotNull String generateJavaFromPipeline(final @NotNull PipelineVoiceFilter pipeline) {
    final var className = toPascalCase(pipeline.getId()) + "VoiceFilter";
    final var sb = new StringBuilder();
    sb.append("package fr.dreamin.dreamvoice.custom;\n\n");
    sb.append("import fr.dreamin.dreamvoice.api.filter.annotation.AutoVoiceFilter;\n");
    sb.append("import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;\n");
    sb.append("import fr.dreamin.dreamvoice.api.player.model.VPlayer;\n");
    sb.append("import org.jetbrains.annotations.NotNull;\n");
    sb.append("import org.jetbrains.annotations.Nullable;\n\n");
    sb.append("@AutoVoiceFilter(priority = ").append(pipeline.getPriority()).append(")\n");
    sb.append("public class ").append(className).append(" implements VoiceFilter {\n\n");
    sb.append("  @Override\n  public @NotNull String getId() { return \"").append(pipeline.getId()).append("\"; }\n\n");
    sb.append("  @Override\n  public @NotNull String getName() { return \"").append(pipeline.getName()).append("\"; }\n\n");
    sb.append("  @Override\n  public int getPriority() { return ").append(pipeline.getPriority()).append("; }\n\n");
    sb.append("  @Override\n  public short[] process(final short @NotNull [] samples, final @Nullable VPlayer player) {\n");
    sb.append("    final var out = new short[samples.length];\n");
    sb.append("    for (var i = 0; i < samples.length; i++) {\n");
    sb.append("      out[i] = samples[i];\n");
    sb.append("    }\n");
    sb.append("    return out;\n");
    sb.append("  }\n\n");
    sb.append("}\n");
    return sb.toString();
  }

  private static @NotNull String toPascalCase(final @NotNull String text) {
    final var parts = text.split("[_-]");
    final var sb = new StringBuilder();
    for (final var part : parts) {
      if (!part.isEmpty())
        sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
    }
    return sb.length() > 0 ? sb.toString() : "Custom";
  }

}
