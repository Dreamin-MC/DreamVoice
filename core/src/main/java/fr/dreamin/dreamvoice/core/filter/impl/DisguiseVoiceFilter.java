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
 * Disguise / Anonymizer voice filter.
 * Obscures the speaker's vocal identity and formant timbre using smooth pitch shifting, sideband wobble, and subtle saturation.
 * Features per-player state, circular buffer, Hann windowing, and linear interpolation.
 */
public final class DisguiseVoiceFilter implements VoiceFilter {

  private static final int SAMPLE_RATE = 48000;
  private static final int GRAIN_SIZE = 1920; // 40ms grains at 48kHz
  private static final float PITCH_FACTOR = 0.78f; // Deep anonymizing shift
  private static final double TWO_PI = 2.0 * Math.PI;
  private static final double LFO_INC = (TWO_PI * 18.0) / SAMPLE_RATE;

  private final Map<UUID, DisguiseState> states = new ConcurrentHashMap<>();

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public @NotNull String getId() {
    return "disguise";
  }

  @Override
  public @NotNull String getName() {
    return "Disguise / Anonymizer";
  }

  @Override
  public int getPriority() {
    return 35;
  }

  @Override
  public short[] process(final short @NonNull [] samples, final @Nullable VPlayer player) {
    if (samples.length == 0)
      return samples;

    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, _ -> new DisguiseState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      state.buffer[state.writePos] = (float) samples[i];
      state.writePos = (state.writePos + 1) % GRAIN_SIZE;

      state.phase1 += 1.0f;
      if (state.phase1 >= GRAIN_SIZE)
        state.phase1 -= GRAIN_SIZE;

      final var phase2 = (state.phase1 + (GRAIN_SIZE / 2.0f)) % GRAIN_SIZE;

      // Hann windows (constant 1.0 sum)
      final var w1 = 0.5f * (1.0f - (float) Math.cos(TWO_PI * state.phase1 / GRAIN_SIZE));
      final var w2 = 0.5f * (1.0f - (float) Math.cos(TWO_PI * phase2 / GRAIN_SIZE));

      final var offset1 = state.phase1 * (PITCH_FACTOR - 1.0f);
      final var offset2 = phase2 * (PITCH_FACTOR - 1.0f);

      final var s1 = state.readInterpolated(state.writePos - offset1);
      final var s2 = state.readInterpolated(state.writePos - offset2);

      var blended = (s1 * w1) + (s2 * w2);

      // Subtle pitch wobble (18 Hz)
      state.lfoPhase += LFO_INC;
      if (state.lfoPhase >= TWO_PI)
        state.lfoPhase -= TWO_PI;

      final var wobble = 0.90f + 0.10f * (float) Math.sin(state.lfoPhase);
      blended *= wobble;

      // Soft saturation to obscure natural harmonic overtone profile
      var norm = blended / 32768.0f;
      norm = (float) Math.tanh(norm * 1.35f) * 0.88f;

      output[i] = (short) Math.clamp(Math.round(norm * 32767.0f), Short.MIN_VALUE, Short.MAX_VALUE);
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

  private static final class DisguiseState {
    final float[] buffer = new float[GRAIN_SIZE];
    int writePos = 0;
    float phase1 = 0.0f;
    double lfoPhase = 0.0;

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
