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
 * DSP audio filter simulating a deep monster / bass voice effect (-6.5 semitones) using smooth dual-grain synthesis.
 * Uses 40ms grains with Hann windowing (constant 1.0 sum) and linear interpolation to eliminate low-frequency buzz and grain boundary clicks.
 */
public final class DeepVoiceFilter implements VoiceFilter {

  private static final int GRAIN_SIZE = 1920; // 40ms grain at 48kHz (removes grain buzz)
  private static final float PITCH_RATIO = 0.68f; // -6.5 semitones (monster / deep voice)
  private static final double TWO_PI = 2.0 * Math.PI;

  private final Map<UUID, PitchState> states = new ConcurrentHashMap<>();

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public @NotNull String getId() {
    return "deep";
  }

  @Override
  public @NotNull String getName() {
    return "Deep Voice (Monster)";
  }

  @Override
  public int getPriority() {
    return 40;
  }

  @Override
  public short[] process(final short @NonNull [] samples, final @Nullable VPlayer player) {
    if (samples.length == 0)
      return samples;

    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, _ -> new PitchState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      state.buffer[state.writePos] = (float) samples[i];
      state.writePos = (state.writePos + 1) % GRAIN_SIZE;

      state.phase1 += 1.0f;
      if (state.phase1 >= GRAIN_SIZE)
        state.phase1 -= GRAIN_SIZE;

      final var out = getOut(state);
      output[i] = (short) Math.clamp(Math.round(out), Short.MIN_VALUE, Short.MAX_VALUE);
    }

    return output;
  }

  @Override
  public void resetState(final @NotNull UUID playerUuid) {
    this.states.remove(playerUuid);
  }

  // ###############################################################
  // ----------------------- PRIVATE METHODS -----------------------
  // ###############################################################

  private static float getOut(final PitchState state) {
    final var phase2 = (state.phase1 + (GRAIN_SIZE / 2.0f)) % GRAIN_SIZE;

    // Hann windows: strictly sums to 1.0 with zero derivative at boundaries
    final var w1 = 0.5f * (1.0f - (float) Math.cos(TWO_PI * state.phase1 / GRAIN_SIZE));
    final var w2 = 0.5f * (1.0f - (float) Math.cos(TWO_PI * phase2 / GRAIN_SIZE));

    final var offset1 = state.phase1 * (PITCH_RATIO - 1.0f);
    final var offset2 = phase2 * (PITCH_RATIO - 1.0f);

    final var s1 = state.readInterpolated(state.writePos - offset1);
    final var s2 = state.readInterpolated(state.writePos - offset2);

    return (s1 * w1) + (s2 * w2);
  }

  private static final class PitchState {
    final float[] buffer = new float[GRAIN_SIZE];
    int writePos = 0;
    float phase1 = 0.0f;

    float readInterpolated(float readPos) {
      while (readPos < 0.0f)
        readPos += GRAIN_SIZE;
      while (readPos >= (float) GRAIN_SIZE)
        readPos -= GRAIN_SIZE;

      final var i0 = (int) readPos;
      final var i1 = (i0 + 1) % GRAIN_SIZE;
      final var frac = readPos - (float) i0;
      return this.buffer[i0] * (1.0f - frac) + this.buffer[i1] * frac;
    }
  }

}


