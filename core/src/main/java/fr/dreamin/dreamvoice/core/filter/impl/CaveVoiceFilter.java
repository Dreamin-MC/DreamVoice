package fr.dreamin.dreamvoice.core.filter.impl;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DSP audio filter simulating stone cavern multi-tap reverb and low-frequency echo reflections.
 */
public final class CaveVoiceFilter implements VoiceFilter {

  private static final int BUFFER_SIZE = 16384; // ~340ms memory
  private static final int TAP_1 = 1152; // ~24ms early rock slap
  private static final int TAP_2 = 1920; // ~40ms rock tunnel bounce
  private static final int TAP_3 = 3120; // ~65ms cavern wall flutter
  private static final int TAP_4 = 4800; // ~100ms deep shaft echo

  private final Map<UUID, DelayBuffer> delayBuffers = new ConcurrentHashMap<>();

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public @NotNull String getId() {
    return "cave";
  }

  @Override
  public @NotNull String getName() {
    return "Cave";
  }

  @Override
  public int getPriority() {
    return 20;
  }

  @Override
  public short[] process(final short @NonNull [] samples, final @Nullable VPlayer player) {
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var delay = this.delayBuffers.computeIfAbsent(uuid, k -> new DelayBuffer());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      final var dry = (float) samples[i];

      final var e1 = delay.get(TAP_1);
      final var e2 = delay.get(TAP_2);
      final var e3 = delay.get(TAP_3);
      final var e4 = delay.get(TAP_4);

      final var wet = (e1 * 0.45f) + (e2 * 0.35f) + (e3 * 0.28f) + (e4 * 0.22f);
      final var combined = (dry * 0.75f) + (wet * 0.60f);

      delay.write(dry + wet * 0.42f);
      output[i] = (short) Math.clamp(Math.round(combined), Short.MIN_VALUE, Short.MAX_VALUE);
    }

    return output;
  }

  @Override
  public void resetState(final @NotNull UUID playerUuid) {
    this.delayBuffers.remove(playerUuid);
  }

  // ###############################################################
  // ----------------------- PRIVATE METHODS -----------------------
  // ###############################################################

  private static final class DelayBuffer {
    private final float[] buffer = new float[BUFFER_SIZE];
    private int writeIndex = 0;
    private float lpFilter = 0.0f;

    void write(final float sample) {
      this.lpFilter = this.lpFilter + 0.18f * (sample - this.lpFilter);
      this.buffer[this.writeIndex] = this.lpFilter;
      this.writeIndex = (this.writeIndex + 1) % BUFFER_SIZE;
    }

    float get(final int delaySamples) {
      var readIndex = this.writeIndex - delaySamples;
      if (readIndex < 0)
        readIndex += BUFFER_SIZE;
      return this.buffer[readIndex];
    }
  }

}
