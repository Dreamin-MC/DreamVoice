package fr.dreamin.dreamvoice.core.filter.impl;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UnderwaterVoiceFilter implements VoiceFilter {

  private static final float BASE_ALPHA = 0.038f; // ~280Hz cutoff at 48kHz
  private final Map<UUID, UnderwaterState> states = new ConcurrentHashMap<>();

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
  public short[] process(final @NotNull short[] samples, final @Nullable VPlayer player) {
    final var output = new short[samples.length];
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, k -> new UnderwaterState());

    for (int i = 0; i < samples.length; i++) {
      // Primary slow water motion LFO (~3.2 Hz)
      state.lfoPhase1 += (2.0 * Math.PI * 3.2 / 48000.0);
      if (state.lfoPhase1 >= 2.0 * Math.PI)
        state.lfoPhase1 -= 2.0 * Math.PI;

      // Secondary fast bubbling turbulence LFO (~7.8 Hz)
      state.lfoPhase2 += (2.0 * Math.PI * 7.8 / 48000.0);
      if (state.lfoPhase2 >= 2.0 * Math.PI)
        state.lfoPhase2 -= 2.0 * Math.PI;

      final var alphaMod = BASE_ALPHA * (0.85f + 0.30f * (float) Math.sin(state.lfoPhase1));

      // 4-pole cascaded lowpass (steep 24 dB/octave attenuation of mid/high frequencies)
      state.p1 = state.p1 + alphaMod * (samples[i] - state.p1);
      state.p2 = state.p2 + alphaMod * (state.p1 - state.p2);
      state.p3 = state.p3 + alphaMod * (state.p2 - state.p3);
      state.p4 = state.p4 + alphaMod * (state.p3 - state.p4);

      // Acoustic bubble/gurgle amplitude modulation
      final var bubble = 0.65f + 0.25f * (float) Math.sin(state.lfoPhase1) + 0.15f * (float) Math.sin(state.lfoPhase2);
      final var out = state.p4 * bubble * 2.2f;

      output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(out)));
    }

    return output;
  }

  @Override
  public void resetState(final @NotNull UUID playerUuid) {
    this.states.remove(playerUuid);
  }

  private static final class UnderwaterState {
    float p1 = 0.0f;
    float p2 = 0.0f;
    float p3 = 0.0f;
    float p4 = 0.0f;
    double lfoPhase1 = 0.0;
    double lfoPhase2 = 0.0;
  }

}


