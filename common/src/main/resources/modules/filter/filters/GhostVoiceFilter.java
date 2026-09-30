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
 * DSP audio filter simulating an eerie, spectral ghost voice with chorus detuning and modulated whisper reflections.
 * Features linearly interpolated chorus delay taps and soft-knee dynamics to eliminate modulation clicks and clipping.
 */
public final class GhostVoiceFilter implements VoiceFilter {

  private static final int BUFFER_SIZE = 16384; // ~340ms memory
  private static final float DELAY_TAP = 7200.0f; // 150ms echo
  private static final double SAMPLE_RATE = 48000.0;
  private static final double TWO_PI = 2.0 * Math.PI;
  private static final double LFO_INC = (TWO_PI * 0.8) / SAMPLE_RATE;

  private final Map<UUID, GhostState> states = new ConcurrentHashMap<>();

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public @NotNull String getId() {
    return "ghost";
  }

  @Override
  public @NotNull String getName() {
    return "Ghost";
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
    final var state = this.states.computeIfAbsent(uuid, _ -> new GhostState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      final var dry = (float) samples[i];

      state.lfoPhase += LFO_INC;
      if (state.lfoPhase >= TWO_PI)
        state.lfoPhase -= TWO_PI;

      final var modDelay = DELAY_TAP + (480.0f * (float) Math.sin(state.lfoPhase));
      final var wetEcho = state.getEchoInterpolated(modDelay);

      // Low-pass whisper filtering
      state.filterLp = state.filterLp + 0.12f * (dry - state.filterLp);

      final var eerie = (dry * 0.50f) + (state.filterLp * 0.28f) + (wetEcho * 0.35f);
      state.writeEcho(dry * 0.60f + wetEcho * 0.38f);

      var norm = eerie / 32767.0f;
      if (norm > 0.85f || norm < -0.85f) {
        final var abs = Math.abs(norm);
        final var excess = abs - 0.85f;
        final var compressed = 0.85f + 0.15f * (float) Math.tanh(excess / 0.15f);
        final var sign = norm < 0 ? -1.0f : 1.0f;
        norm = sign * compressed;
      }

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

  private static final class GhostState {
    final float[] delay = new float[BUFFER_SIZE];
    int writeIndex = 0;
    float filterLp = 0.0f;
    double lfoPhase = 0.0;

    void writeEcho(final float sample) {
      this.delay[this.writeIndex] = sample;
      this.writeIndex = (this.writeIndex + 1) % BUFFER_SIZE;
    }

    float getEchoInterpolated(final float delaySamples) {
      var readPos = (float) this.writeIndex - delaySamples;
      while (readPos < 0.0f)
        readPos += BUFFER_SIZE;
      while (readPos >= (float) BUFFER_SIZE)
        readPos -= BUFFER_SIZE;

      final var i0 = (int) readPos;
      final var i1 = (i0 + 1) % BUFFER_SIZE;
      final var frac = readPos - (float) i0;
      return this.delay[i0] * (1.0f - frac) + this.delay[i1] * frac;
    }
  }

}


