package fr.dreamin.dreamvoice.core.filter.impl;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GhostVoiceFilter implements VoiceFilter {

  private static final int BUFFER_SIZE = 16384; // ~340ms memory
  private static final int DELAY_TAP = 7200; // 150ms echo

  private final Map<UUID, GhostState> states = new ConcurrentHashMap<>();

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
  public short[] process(final @NotNull short[] samples, final @Nullable VPlayer player) {
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, k -> new GhostState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      final var dry = (float) samples[i];

      // Chorus / Pitch detune LFO (0.8 Hz)
      state.lfoPhase += (2.0 * Math.PI * 0.8 / 48000.0);
      if (state.lfoPhase >= 2.0 * Math.PI)
        state.lfoPhase -= 2.0 * Math.PI;

      final var modDelay = DELAY_TAP + (int) (480.0f * Math.sin(state.lfoPhase));
      final var wetEcho = state.getEcho(modDelay);

      // Low-pass whisper filtering
      state.filterLp = state.filterLp + 0.12f * (dry - state.filterLp);

      final var eerie = (dry * 0.50f) + (state.filterLp * 0.40f) + (wetEcho * 0.55f);
      state.writeEcho(dry * 0.70f + wetEcho * 0.50f);

      output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(eerie)));
    }

    return output;
  }

  @Override
  public void resetState(final @NotNull UUID playerUuid) {
    this.states.remove(playerUuid);
  }

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
