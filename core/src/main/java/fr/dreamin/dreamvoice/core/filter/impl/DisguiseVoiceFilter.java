package fr.dreamin.dreamvoice.core.filter.impl;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Disguise / Anonymizer voice filter.
 * Completely obscures the speaker's vocal identity and timbre while keeping speech clear.
 */
public final class DisguiseVoiceFilter implements VoiceFilter {

  private static final int SAMPLE_RATE = 48000;
  private static final int GRAIN_SIZE = 1200; // 25ms grains
  private static final float PITCH_FACTOR = 0.76f; // Deep anonymizing shift

  private float phase = 0.0f;
  private float lfoPhase = 0.0f;

  @Override
  public @NotNull String getId() {
    return "disguise";
  }

  @Override
  public @NotNull String getName() {
    return "Disguise / Anonymizer";
  }

  @Override
  public short[] process(final @NotNull short[] samples, final @Nullable VPlayer player) {
    if (samples.length == 0)
      return samples;

    final var output = new short[samples.length];
    final var grainSize = GRAIN_SIZE;
    final var hopSize = grainSize / 2;

    // 1. Dual-grain pitch shift & formant masking
    for (int i = 0; i < samples.length; i++) {
      final var readPos1 = (int) (this.phase) % grainSize;
      final var readPos2 = (int) (this.phase + hopSize) % grainSize;

      final var window1 = 0.5f * (1.0f - (float) Math.cos(2.0 * Math.PI * readPos1 / grainSize));
      final var window2 = 0.5f * (1.0f - (float) Math.cos(2.0 * Math.PI * readPos2 / grainSize));

      final var sampleIndex1 = Math.max(0, Math.min(samples.length - 1, i - readPos1 + (int) (readPos1 * PITCH_FACTOR)));
      final var sampleIndex2 = Math.max(0, Math.min(samples.length - 1, i - readPos2 + (int) (readPos2 * PITCH_FACTOR)));

      final var s1 = samples[sampleIndex1] * window1;
      final var s2 = samples[sampleIndex2] * window2;

      var blended = s1 + s2;

      // 2. Harmonic modulation (38Hz sideband wobble)
      final var mod = 0.88f + 0.12f * (float) Math.sin(this.lfoPhase);
      this.lfoPhase += (2.0f * (float) Math.PI * 38.0f) / SAMPLE_RATE;
      if (this.lfoPhase > 2.0f * Math.PI)
        this.lfoPhase -= (float) (2.0f * Math.PI);

      blended *= mod;

      // 3. Subtle soft saturation to disguise vocal harmonics
      final var normalized = blended / 32768.0f;
      final var distorted = (float) Math.tanh(normalized * 1.35f) * 0.85f;

      output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(distorted * 32767.0f)));

      this.phase += (1.0f - PITCH_FACTOR);
      if (this.phase >= grainSize)
        this.phase -= grainSize;
    }

    return output;
  }

  @Override
  public void resetState(final @NotNull UUID playerUuid) {
    this.phase = 0.0f;
    this.lfoPhase = 0.0f;
  }

}
