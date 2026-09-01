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
 */
public final class GhostVoiceFilter implements VoiceFilter {

  private static final int BUFFER_SIZE = 16384; // ~340ms memory
  private static final int DELAY_TAP = 7200; // 150ms echo
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
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, _ -> new GhostState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      final var dry = (float) samples[i];

      state.lfoPhase += LFO_INC;
      if (state.lfoPhase >= TWO_PI)
        state.lfoPhase -= TWO_PI;

      final var modDelay = DELAY_TAP + (int) (480.0f * Math.sin(state.lfoPhase));
      final var wetEcho = state.getEcho(modDelay);

      // Low-pass whisper filtering
      state.filterLp = state.filterLp + 0.12f * (dry - state.filterLp);

      final var eerie = (dry * 0.50f) + (state.filterLp * 0.40f) + (wetEcho * 0.55f);
      state.writeEcho(dry * 0.70f + wetEcho * 0.50f);

      output[i] = (short) Math.clamp(Math.round(eerie), Short.MIN_VALUE, Short.MAX_VALUE);
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

    float getEcho(final int delaySamples) {
      var readIndex = this.writeIndex - delaySamples;
      while (readIndex < 0)
        readIndex += BUFFER_SIZE;
      readIndex = readIndex % BUFFER_SIZE;
      return this.delay[readIndex];
    }
  }

}
