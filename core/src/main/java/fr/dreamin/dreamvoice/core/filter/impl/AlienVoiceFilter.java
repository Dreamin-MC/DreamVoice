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
 * DSP audio filter simulating a futuristic alien / sci-fi vocal frequency modulator.
 */
public final class AlienVoiceFilter implements VoiceFilter {

  private static final int BUFFER_SIZE = 4096;
  private static final float MOD_FREQ = 14.0f; // 14 Hz fast sci-fi vibrato
  private static final double SAMPLE_RATE = 48000.0;
  private static final double TWO_PI = 2.0 * Math.PI;
  private static final double PHASE_INC = (TWO_PI * MOD_FREQ) / SAMPLE_RATE;

  private final Map<UUID, AlienState> states = new ConcurrentHashMap<>();

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public @NotNull String getId() {
    return "alien";
  }

  @Override
  public @NotNull String getName() {
    return "Alien / Sci-Fi";
  }

  @Override
  public int getPriority() {
    return 30;
  }

  @Override
  public short[] process(final short @NonNull [] samples, final @Nullable VPlayer player) {
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, k -> new AlienState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      final var dry = (float) samples[i];
      state.buffer[state.writeIndex] = dry;
      state.writeIndex = (state.writeIndex + 1) % BUFFER_SIZE;

      state.phase += PHASE_INC;
      if (state.phase >= TWO_PI)
        state.phase -= TWO_PI;

      // FM delay modulation (240 samples ~ 5ms vibrato depth)
      final var delayOffset = 480.0f + 240.0f * (float) Math.sin(state.phase);
      var readIdx = state.writeIndex - (int) delayOffset;
      while (readIdx < 0)
        readIdx += BUFFER_SIZE;
      readIdx %= BUFFER_SIZE;

      final var modSample = state.buffer[readIdx];
      // Tremolo amplitude modulation (22 Hz)
      final var tremolo = 0.65f + 0.35f * (float) Math.sin(state.phase * 1.5);
      final var out = (modSample * 0.75f + dry * 0.25f) * tremolo;

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

  private static final class AlienState {
    final float[] buffer = new float[BUFFER_SIZE];
    int writeIndex = 0;
    double phase = 0.0;
  }

}
