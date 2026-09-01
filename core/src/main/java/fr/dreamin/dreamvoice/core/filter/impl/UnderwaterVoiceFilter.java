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
 * DSP audio filter simulating underwater acoustics with 4-pole low-pass filtering and turbulence LFOs.
 */
public final class UnderwaterVoiceFilter implements VoiceFilter {

  private static final float BASE_ALPHA = 0.038f; // ~280Hz cutoff at 48kHz
  private static final double SAMPLE_RATE = 48000.0;
  private static final double TWO_PI = 2.0 * Math.PI;
  private static final double LFO1_INC = (TWO_PI * 3.2) / SAMPLE_RATE;
  private static final double LFO2_INC = (TWO_PI * 7.8) / SAMPLE_RATE;

  private final Map<UUID, UnderwaterState> states = new ConcurrentHashMap<>();

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public @NotNull String getId() {
    return "underwater";
  }

  @Override
  public @NotNull String getName() {
    return "Underwater";
  }

  @Override
  public int getPriority() {
    return 10;
  }

  @Override
  public short[] process(final short @NonNull [] samples, final @Nullable VPlayer player) {
    final var output = new short[samples.length];
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, _ -> new UnderwaterState());

    for (int i = 0; i < samples.length; i++) {
      state.lfoPhase1 += LFO1_INC;
      if (state.lfoPhase1 >= TWO_PI)
        state.lfoPhase1 -= TWO_PI;

      state.lfoPhase2 += LFO2_INC;
      if (state.lfoPhase2 >= TWO_PI)
        state.lfoPhase2 -= TWO_PI;

      final var alphaMod = BASE_ALPHA * (0.85f + 0.30f * (float) Math.sin(state.lfoPhase1));

      // 4-pole cascaded lowpass (steep 24 dB/octave attenuation of mid/high frequencies)
      state.p1 = state.p1 + alphaMod * (samples[i] - state.p1);
      state.p2 = state.p2 + alphaMod * (state.p1 - state.p2);
      state.p3 = state.p3 + alphaMod * (state.p2 - state.p3);
      state.p4 = state.p4 + alphaMod * (state.p3 - state.p4);

      // Acoustic bubble/gurgle amplitude modulation
      final var bubble = 0.65f + 0.25f * (float) Math.sin(state.lfoPhase1) + 0.15f * (float) Math.sin(state.lfoPhase2);
      final var out = state.p4 * bubble * 2.2f;

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

  private static final class UnderwaterState {
    float p1 = 0.0f;
    float p2 = 0.0f;
    float p3 = 0.0f;
    float p4 = 0.0f;
    double lfoPhase1 = 0.0;
    double lfoPhase2 = 0.0;
  }

}
