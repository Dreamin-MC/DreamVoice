package fr.dreamin.dreamvoice.common.filter.exporter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.common.filter.pipeline.PipelineVoiceFilter;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exporter responsible for converting any registered {@link VoiceFilter}
 * into .yml, .json, or .java files.
 */
public final class VoiceFilterExporter {

  private final @NotNull VoicePlatform platform;
  private final @NotNull File outputDir;
  private final ObjectMapper jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  public VoiceFilterExporter(final @NotNull VoicePlatform platform, final @NotNull File outputDir) {
    this.platform = platform;
    this.outputDir = outputDir;
  }

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
          this.platform.logWarning("[VoiceFilter] Unsupported export format '" + format + "'. Use yml, json, or java.");
          return null;
        }
      }
      return targetFile;
    } catch (final Exception e) {
      this.platform.logError("[VoiceFilter] Failed to export filter " + filter.getId() + " to " + targetFile.getName() + ": " + e.getMessage(), e);
      return null;
    }
  }

  private void exportYaml(final @NotNull VoiceFilter filter, final @NotNull File targetFile) throws Exception {
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
    } else
      mapBuiltinFilterToPipeline(filter, pipelineList);

    root.put("pipeline", pipelineList);

    final var options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    final var yaml = new Yaml(options);

    try (final var writer = Files.newBufferedWriter(targetFile.toPath())) {
      yaml.dump(root, writer);
    }
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
    } else
      mapBuiltinFilterToPipeline(filter, pipelineList);

    root.put("pipeline", pipelineList);
    this.jsonMapper.writeValue(targetFile, root);
  }

  private void exportJava(
    final @NotNull VoiceFilter filter,
    final @NotNull File targetFile,
    final @NotNull ClassLoader pluginClassLoader
  ) throws Exception {
    final var simpleName = filter.getClass().getSimpleName();
    final var resourcePath = "modules/filter/filters/" + simpleName + ".java";

    try (final InputStream in = pluginClassLoader.getResourceAsStream(resourcePath)) {
      if (in != null) {
        Files.copy(in, targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return;
      }
    }

    if (filter instanceof PipelineVoiceFilter pipeline) {
      final var generatedSource = generateJavaFromPipeline(pipeline);
      Files.writeString(targetFile.toPath(), generatedSource);
      return;
    }

    final var stubSource = generateStubJavaSource(filter);
    Files.writeString(targetFile.toPath(), stubSource);
  }

  private void mapBuiltinFilterToPipeline(final @NotNull VoiceFilter filter, final @NotNull List<Map<String, Object>> pipelineList) {
    final var id = filter.getId().toLowerCase();
    switch (id) {
      case "muffled" -> {
        pipelineList.add(Map.of("type", "lowpass", "cutoff_hz", 800.0));
        pipelineList.add(Map.of("type", "gain", "multiplier", 0.7f));
      }
      case "telephone" -> {
        pipelineList.add(Map.of("type", "highpass", "cutoff_hz", 400.0));
        pipelineList.add(Map.of("type", "lowpass", "cutoff_hz", 3000.0));
        pipelineList.add(Map.of("type", "overdrive", "drive", 2.5f));
      }
      case "radio" -> {
        pipelineList.add(Map.of("type", "highpass", "cutoff_hz", 500.0));
        pipelineList.add(Map.of("type", "lowpass", "cutoff_hz", 2500.0));
        pipelineList.add(Map.of("type", "overdrive", "drive", 2.0f));
      }
      case "deep" -> pipelineList.add(Map.of("type", "lowpass", "cutoff_hz", 600.0));
      case "helium" -> pipelineList.add(Map.of("type", "highpass", "cutoff_hz", 1200.0));
      case "robot" -> {
        pipelineList.add(Map.of("type", "ring_modulator", "freq_hz", 80.0, "mix", 0.6f));
        pipelineList.add(Map.of("type", "overdrive", "drive", 1.5f));
      }
      case "alien" -> pipelineList.add(Map.of("type", "ring_modulator", "freq_hz", 140.0, "mix", 0.75f));
      case "cave", "echo" -> pipelineList.add(Map.of("type", "delay", "delay_ms", 220, "decay", 0.45f));
      default -> pipelineList.add(Map.of("type", "gain", "multiplier", 1.0f));
    }
  }

  private @NotNull String generateJavaFromPipeline(final @NotNull PipelineVoiceFilter pipeline) {
    final var className = toPascalCase(pipeline.getId()) + "VoiceFilter";
    final var sb = new StringBuilder();

    sb.append("package fr.dreamin.dreamvoice.custom;\n\n");
    sb.append("import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;\n");
    sb.append("import fr.dreamin.dreamvoice.api.player.model.VPlayer;\n");
    sb.append("import org.jetbrains.annotations.NotNull;\n");
    sb.append("import org.jetbrains.annotations.Nullable;\n\n");
    sb.append("import java.util.UUID;\n\n");
    sb.append("public final class ").append(className).append(" implements VoiceFilter {\n\n");
    sb.append("  @Override\n");
    sb.append("  public @NotNull String getId() {\n");
    sb.append("    return \"").append(pipeline.getId()).append("\";\n");
    sb.append("  }\n\n");
    sb.append("  @Override\n");
    sb.append("  public @NotNull String getName() {\n");
    sb.append("    return \"").append(pipeline.getName()).append("\";\n");
    sb.append("  }\n\n");
    sb.append("  @Override\n");
    sb.append("  public int getPriority() {\n");
    sb.append("    return ").append(pipeline.getPriority()).append(";\n");
    sb.append("  }\n\n");
    sb.append("  @Override\n");
    sb.append("  public short[] process(final short @NotNull [] pcm, final @Nullable VPlayer player) {\n");
    sb.append("    final var out = pcm.clone();\n");

    for (final var node : pipeline.getNodes()) {
      switch (node.getType()) {
        case "gain" -> {
          final var mult = (float) node.getParameters().getOrDefault("multiplier", 1.0f);
          sb.append("    // Gain: ").append(mult).append("\n");
          sb.append("    for (int i = 0; i < out.length; i++) {\n");
          sb.append("      out[i] = (short) Math.clamp(Math.round(out[i] * ").append(mult).append("f), Short.MIN_VALUE, Short.MAX_VALUE);\n");
          sb.append("    }\n");
        }
        case "overdrive" -> {
          final var drive = (float) node.getParameters().getOrDefault("drive", 2.0f);
          sb.append("    // Overdrive: ").append(drive).append("\n");
          sb.append("    for (int i = 0; i < out.length; i++) {\n");
          sb.append("      final var s = out[i] / 32768.0f * ").append(drive).append("f;\n");
          sb.append("      final var dist = Math.tanh(s);\n");
          sb.append("      out[i] = (short) Math.clamp(Math.round(dist * 32767.0f), Short.MIN_VALUE, Short.MAX_VALUE);\n");
          sb.append("    }\n");
        }
        default -> sb.append("    // Node type '").append(node.getType()).append("' applied\n");
      }
    }

    sb.append("    return out;\n");
    sb.append("  }\n\n");
    sb.append("}\n");

    return sb.toString();
  }

  private @NotNull String generateStubJavaSource(final @NotNull VoiceFilter filter) {
    final var className = toPascalCase(filter.getId()) + "VoiceFilter";
    return "package fr.dreamin.dreamvoice.custom;\n\n"
      + "import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;\n"
      + "import org.jetbrains.annotations.NotNull;\n"
      + "import org.jetbrains.annotations.Nullable;\n\n"
      + "import java.util.UUID;\n\n"
      + "public final class " + className + " implements VoiceFilter {\n\n"
      + "  @Override\n"
      + "  public @NotNull String getId() {\n"
      + "    return \"" + filter.getId() + "\";\n"
      + "  }\n\n"
      + "  @Override\n"
      + "  public @NotNull String getName() {\n"
      + "    return \"" + filter.getName() + "\";\n"
      + "  }\n\n"
      + "  @Override\n"
      + "  public int getPriority() {\n"
      + "    return " + filter.getPriority() + ";\n"
      + "  }\n\n"
      + "  @Override\n"
      + "  public short[] process(final short @NotNull [] pcm, final @Nullable VPlayer player) {\n"
      + "    return pcm;\n"
      + "  }\n\n"
      + "}\n";
  }

  private static @NotNull String toPascalCase(final @NotNull String id) {
    final var parts = id.split("[_\\-\\s]+");
    final var sb = new StringBuilder();
    for (final var p : parts) {
      if (!p.isBlank())
        sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase());
    }
    return sb.toString();
  }

}
