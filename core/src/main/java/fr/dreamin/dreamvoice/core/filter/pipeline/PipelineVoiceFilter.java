package fr.dreamin.dreamvoice.core.filter.pipeline;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Composite DSP voice filter composed of a chained pipeline of audio processing nodes
 * loaded declaratively from .yml or .json files.
 */
public final class PipelineVoiceFilter implements VoiceFilter {

  public interface DspNode {
    short[] process(final short[] input, final UUID playerUuid);
    default void resetState(final UUID playerUuid) {}
    @NotNull String getType();
    @NotNull Map<String, Object> getParameters();
  }

  private final @NotNull String id;
  private final @NotNull String name;
  private final int priority;
  private final @NotNull List<DspNode> nodes = new ArrayList<>();

  public PipelineVoiceFilter(final @NotNull String id, final @NotNull String name, final int priority) {
    this.id = id;
    this.name = name;
    this.priority = priority;
  }

  public void addNode(final @NotNull DspNode node) {
    this.nodes.add(node);
  }

  public @NotNull List<DspNode> getNodes() {
    return Collections.unmodifiableList(this.nodes);
  }

  @Override
  public @NotNull String getId() {
    return this.id;
  }

  @Override
  public @NotNull String getName() {
    return this.name;
  }

  @Override
  public int getPriority() {
    return this.priority;
  }

  @Override
  public short[] process(final short @NotNull [] samples, final @Nullable VPlayer player) {
    if (this.nodes.isEmpty())
      return samples;

    final var uuid = player != null ? player.getUuid() : new UUID(0L, 0L);
    var buffer = samples;
    for (final var node : this.nodes)
      buffer = node.process(buffer, uuid);

    return buffer;
  }

  @Override
  public void resetState(final @NotNull UUID playerUuid) {
    for (final var node : this.nodes)
      node.resetState(playerUuid);
  }

  // -------------------------------------------------------------
  // Built-in DSP Nodes for declarative configuration
  // -------------------------------------------------------------

  @Getter
  public static final class LowPassNode implements DspNode {
    private final double cutoffHz;
    private final float cutoffAlpha;
    private final Map<UUID, Float> state = new ConcurrentHashMap<>();

    public LowPassNode(final double cutoffHz) {
      this.cutoffHz = cutoffHz;
      final var dt = 1.0 / 48000.0;
      final var rc = 1.0 / (2.0 * Math.PI * cutoffHz);
      this.cutoffAlpha = (float) (dt / (rc + dt));
    }

    @Override
    public @NotNull String getType() { return "lowpass"; }

    @Override
    public @NotNull Map<String, Object> getParameters() {
      return Map.of("cutoff_hz", this.cutoffHz);
    }

    @Override
    public short[] process(final short[] input, final UUID playerUuid) {
      final var out = new short[input.length];
      var prev = this.state.getOrDefault(playerUuid, 0f);
      for (var i = 0; i < input.length; i++) {
        prev = prev + this.cutoffAlpha * (input[i] - prev);
        out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(prev)));
      }
      this.state.put(playerUuid, prev);
      return out;
    }

    @Override
    public void resetState(final UUID playerUuid) {
      this.state.remove(playerUuid);
    }
  }

  @Getter
  public static final class HighPassNode implements DspNode {
    private final double cutoffHz;
    private final float alpha;
    private final Map<UUID, float[]> state = new ConcurrentHashMap<>();

    public HighPassNode(final double cutoffHz) {
      this.cutoffHz = cutoffHz;
      final var dt = 1.0 / 48000.0;
      final var rc = 1.0 / (2.0 * Math.PI * cutoffHz);
      this.alpha = (float) (rc / (rc + dt));
    }

    @Override
    public @NotNull String getType() { return "highpass"; }

    @Override
    public @NotNull Map<String, Object> getParameters() {
      return Map.of("cutoff_hz", this.cutoffHz);
    }

    @Override
    public short[] process(final short[] input, final UUID playerUuid) {
      final var out = new short[input.length];
      final var s = this.state.computeIfAbsent(playerUuid, _ -> new float[]{0f, 0f});
      var prevX = s[0];
      var prevY = s[1];

      for (var i = 0; i < input.length; i++) {
        final var x = (float) input[i];
        final var y = this.alpha * (prevY + x - prevX);
        prevX = x;
        prevY = y;
        out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(y)));
      }
      s[0] = prevX;
      s[1] = prevY;
      return out;
    }

    @Override
    public void resetState(final UUID playerUuid) {
      this.state.remove(playerUuid);
    }
  }

  @Getter
  public static final class GainNode implements DspNode {
    private final float multiplier;

    public GainNode(final float multiplier) {
      this.multiplier = multiplier;
    }

    @Override
    public @NotNull String getType() { return "gain"; }

    @Override
    public @NotNull Map<String, Object> getParameters() {
      return Map.of("multiplier", this.multiplier);
    }

    @Override
    public short[] process(final short[] input, final UUID playerUuid) {
      final var out = new short[input.length];
      for (var i = 0; i < input.length; i++)
        out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(input[i] * this.multiplier)));
      return out;
    }
  }

  @Getter
  public static final class OverdriveNode implements DspNode {
    private final float drive;

    public OverdriveNode(final float drive) {
      this.drive = drive;
    }

    @Override
    public @NotNull String getType() { return "overdrive"; }

    @Override
    public @NotNull Map<String, Object> getParameters() {
      return Map.of("drive", this.drive);
    }

    @Override
    public short[] process(final short[] input, final UUID playerUuid) {
      final var out = new short[input.length];
      for (var i = 0; i < input.length; i++) {
        final var norm = (input[i] / 32768.0f) * this.drive;
        final var clipped = Math.tanh(norm);
        out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(clipped * 32767.0)));
      }
      return out;
    }
  }

  @Getter
  public static final class RingModulatorNode implements DspNode {
    private final double freq;
    private final float mix;
    private final Map<UUID, Double> phases = new ConcurrentHashMap<>();

    public RingModulatorNode(final double freq, final float mix) {
      this.freq = freq;
      this.mix = mix;
    }

    @Override
    public @NotNull String getType() { return "ring_modulator"; }

    @Override
    public @NotNull Map<String, Object> getParameters() {
      return Map.of("freq_hz", this.freq, "mix", this.mix);
    }

    @Override
    public short[] process(final short[] input, final UUID playerUuid) {
      final var out = new short[input.length];
      var phase = this.phases.getOrDefault(playerUuid, 0.0);
      final var phaseInc = (2.0 * Math.PI * this.freq) / 48000.0;

      for (var i = 0; i < input.length; i++) {
        final var carrier = Math.sin(phase);
        phase += phaseInc;
        if (phase > 2.0 * Math.PI)
          phase -= 2.0 * Math.PI;

        final var wet = input[i] * carrier;
        final var blended = (1.0f - this.mix) * input[i] + this.mix * wet;
        out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(blended)));
      }
      this.phases.put(playerUuid, phase);
      return out;
    }

    @Override
    public void resetState(final UUID playerUuid) {
      this.phases.remove(playerUuid);
    }
  }

  @Getter
  public static final class DelayEchoNode implements DspNode {
    private final int delayMs;
    private final int delaySamples;
    private final float decay;
    private final Map<UUID, short[]> delayBuffers = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> writePointers = new ConcurrentHashMap<>();

    public DelayEchoNode(final int delayMs, final float decay) {
      this.delayMs = delayMs;
      this.delaySamples = Math.max(1, (int) (48000.0 * (delayMs / 1000.0)));
      this.decay = decay;
    }

    @Override
    public @NotNull String getType() { return "delay"; }

    @Override
    public @NotNull Map<String, Object> getParameters() {
      return Map.of("delay_ms", this.delayMs, "decay", this.decay);
    }

    @Override
    public short[] process(final short[] input, final UUID playerUuid) {
      final var buffer = this.delayBuffers.computeIfAbsent(playerUuid, _ -> new short[this.delaySamples]);
      var ptr = this.writePointers.getOrDefault(playerUuid, 0);
      final var out = new short[input.length];

      for (var i = 0; i < input.length; i++) {
        final var delayed = buffer[ptr];
        final var mixed = (int) input[i] + (int) (delayed * this.decay);
        final var clamped = (short) Math.max(-32768, Math.min(32767, mixed));

        buffer[ptr] = clamped;
        ptr = (ptr + 1) % this.delaySamples;
        out[i] = clamped;
      }
      this.writePointers.put(playerUuid, ptr);
      return out;
    }

    @Override
    public void resetState(final UUID playerUuid) {
      this.delayBuffers.remove(playerUuid);
      this.writePointers.remove(playerUuid);
    }
  }

}
