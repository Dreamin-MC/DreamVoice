package fr.dreamin.dreamvoice.common.filter.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.common.filter.pipeline.PipelineVoiceFilter;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loader capable of reading file-based filters (.json, .yml, .yaml) and compiling .java filters.
 * Also handles extracting default resource filters to the data folder.
 */
public final class FileFilterLoader {

  private final @NotNull VoicePlatform platform;
  private final @NotNull File filterDirectory;
  private final @NotNull JavaSourceCompiler javaCompiler;
  private final ObjectMapper jsonMapper = new ObjectMapper();

  public FileFilterLoader(final @NotNull VoicePlatform platform, final @NotNull File filterDirectory) {
    this.platform = platform;
    this.filterDirectory = filterDirectory;
    this.javaCompiler = new JavaSourceCompiler(platform, getClass().getClassLoader());
  }

  private static final String[] DEFAULT_FILTERS = {
    "AlienVoiceFilter.java",
    "CaveVoiceFilter.java",
    "DeepVoiceFilter.java",
    "DisguiseVoiceFilter.java",
    "GasmaskVoiceFilter.java",
    "GhostVoiceFilter.java",
    "HeliumVoiceFilter.java",
    "MegaphoneVoiceFilter.java",
    "MuffledVoiceFilter.java",
    "RadioVoiceFilter.java",
    "RobotVoiceFilter.java",
    "TelephoneVoiceFilter.java",
    "UnderwaterVoiceFilter.java"
  };

  public void extractDefaults() {
    if (!this.filterDirectory.exists())
      this.filterDirectory.mkdirs();

    for (final var filterFile : DEFAULT_FILTERS)
      extractDefaultResource("modules/filter/filters/" + filterFile);
  }

  private void extractDefaultResource(final @NotNull String resourcePath) {
    final var fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
    final var target = new File(this.filterDirectory, fileName);
    if (target.exists())
      return;

    try (final InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (in != null)
        Files.copy(in, target.toPath());
    } catch (final Exception e) {
      this.platform.logWarning("[VoiceFilter] Failed to extract default resource " + resourcePath + ": " + e.getMessage());
    }
  }

  public @NotNull List<VoiceFilter> loadAll() {
    final var list = new ArrayList<VoiceFilter>();
    if (!this.filterDirectory.exists())
      return list;

    final var files = this.filterDirectory.listFiles();
    if (files == null)
      return list;

    for (final var file : files) {
      if (file.isDirectory())
        continue;

      final var filter = loadFromFile(file);
      if (filter != null)
        list.add(filter);
    }

    return list;
  }

  public @Nullable VoiceFilter loadFromFile(final @NotNull File file) {
    final var name = file.getName().toLowerCase();
    try {
      if (name.endsWith(".java"))
        return this.javaCompiler.compileFilter(file);

      if (name.endsWith(".json")) {
        final var tree = this.jsonMapper.readTree(file);
        return parsePipelineFromJson(file.getName(), tree);
      }

      if (name.endsWith(".yml") || name.endsWith(".yaml")) {
        final var yaml = new Yaml();
        try (final var reader = Files.newBufferedReader(file.toPath())) {
          final Object loaded = yaml.load(reader);
          if (loaded instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            final var casted = (Map<String, Object>) map;
            return parsePipelineFromYamlMap(file.getName(), casted);
          }
        }
      }
    } catch (final Exception e) {
      this.platform.logError("[VoiceFilter] Failed to parse filter file " + file.getName() + ": " + e.getMessage(), e);
    }
    return null;
  }

  private @Nullable VoiceFilter parsePipelineFromJson(final @NotNull String fileName, final @NotNull JsonNode root) {
    final var id = root.has("id") ? root.get("id").asText() : fileName.replaceFirst("\\.[^.]+$", "").toLowerCase();
    final var name = root.has("name") ? root.get("name").asText() : id;
    final var priority = root.has("priority") ? root.get("priority").asInt(0) : 0;
    final var enabled = !root.has("enabled") || root.get("enabled").asBoolean(true);

    if (!enabled)
      return null;

    final var pipeline = new PipelineVoiceFilter(id, name, priority);
    final var pipelineNode = root.get("pipeline");
    if (pipelineNode != null && pipelineNode.isArray()) {
      for (final var step : pipelineNode) {
        final var type = step.has("type") ? step.get("type").asText().toLowerCase() : "";
        addNodeToPipeline(pipeline, fileName, type,
          step.has("cutoff_hz") ? step.get("cutoff_hz").asDouble() : null,
          step.has("multiplier") ? (float) step.get("multiplier").asDouble() : null,
          step.has("drive") ? (float) step.get("drive").asDouble() : null,
          step.has("freq_hz") ? step.get("freq_hz").asDouble() : null,
          step.has("mix") ? (float) step.get("mix").asDouble() : null,
          step.has("delay_ms") ? step.get("delay_ms").asInt() : null,
          step.has("decay") ? (float) step.get("decay").asDouble() : null
        );
      }
    }

    return pipeline;
  }

  @SuppressWarnings("unchecked")
  private @Nullable VoiceFilter parsePipelineFromYamlMap(final @NotNull String fileName, final @NotNull Map<String, Object> map) {
    final var id = map.containsKey("id") ? String.valueOf(map.get("id")) : fileName.replaceFirst("\\.[^.]+$", "").toLowerCase();
    final var name = map.containsKey("name") ? String.valueOf(map.get("name")) : id;
    final var priority = map.get("priority") instanceof Number n ? n.intValue() : 0;
    final var enabled = !map.containsKey("enabled") || Boolean.TRUE.equals(map.get("enabled"));

    if (!enabled)
      return null;

    final var pipeline = new PipelineVoiceFilter(id, name, priority);
    final var pipelineRaw = map.get("pipeline");
    if (pipelineRaw instanceof List<?> list) {
      for (final var item : list) {
        if (item instanceof Map<?, ?> step) {
          final var rawType = step.get("type");
          final var type = rawType != null ? String.valueOf(rawType).toLowerCase() : "";
          final var cutoffHz = step.get("cutoff_hz") instanceof Number n ? n.doubleValue() : null;
          final var multiplier = step.get("multiplier") instanceof Number n ? n.floatValue() : null;
          final var drive = step.get("drive") instanceof Number n ? n.floatValue() : null;
          final var freqHz = step.get("freq_hz") instanceof Number n ? n.doubleValue() : null;
          final var mix = step.get("mix") instanceof Number n ? n.floatValue() : null;
          final var delayMs = step.get("delay_ms") instanceof Number n ? n.intValue() : null;
          final var decay = step.get("decay") instanceof Number n ? n.floatValue() : null;

          addNodeToPipeline(pipeline, fileName, type, cutoffHz, multiplier, drive, freqHz, mix, delayMs, decay);
        }
      }
    }

    return pipeline;
  }

  private void addNodeToPipeline(
    final @NotNull PipelineVoiceFilter pipeline,
    final @NotNull String fileName,
    final @NotNull String type,
    final Double cutoffHz,
    final Float multiplier,
    final Float drive,
    final Double freqHz,
    final Float mix,
    final Integer delayMs,
    final Float decay
  ) {
    switch (type) {
      case "lowpass", "low_pass" -> pipeline.addNode(new PipelineVoiceFilter.LowPassNode(cutoffHz != null ? cutoffHz : 1000.0));
      case "highpass", "high_pass" -> pipeline.addNode(new PipelineVoiceFilter.HighPassNode(cutoffHz != null ? cutoffHz : 500.0));
      case "gain", "volume" -> pipeline.addNode(new PipelineVoiceFilter.GainNode(multiplier != null ? multiplier : 1.0f));
      case "overdrive", "distortion" -> pipeline.addNode(new PipelineVoiceFilter.OverdriveNode(drive != null ? drive : 2.0f));
      case "ring_modulator", "carrier" -> pipeline.addNode(new PipelineVoiceFilter.RingModulatorNode(freqHz != null ? freqHz : 80.0, mix != null ? mix : 0.5f));
      case "delay", "echo" -> pipeline.addNode(new PipelineVoiceFilter.DelayEchoNode(delayMs != null ? delayMs : 200, decay != null ? decay : 0.4f));
      default -> this.platform.logWarning("[VoiceFilter] Unknown DSP node type '" + type + "' in " + fileName);
    }
  }

}
