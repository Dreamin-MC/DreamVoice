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
 * DSP audio filter simulating a deep monster pitch shift (-6.5 semitones) using dual-grain synthesis.
 */
public final class DeepVoiceFilter implements VoiceFilter {

  private static final int GRAIN_SIZE = 1200; // 25ms grain at 48kHz
  private static final float PITCH_RATIO = 0.68f; // -6.5 semitones (monster / deep voice)

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
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, _ -> new PitchState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      state.buffer[state.writePos] = samples[i];
      state.writePos = (state.writePos + 1) % GRAIN_SIZE;

      state.phase1 = (state.phase1 + 1.0f);
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

  private static float getOut(PitchState state) {
    final var phase2 = (state.phase1 + (GRAIN_SIZE / 2f)) % GRAIN_SIZE;

    final var w1 = 1.0f - Math.abs((state.phase1 - (GRAIN_SIZE / 2f)) / (GRAIN_SIZE / 2f));
    final var w2 = 1.0f - Math.abs((phase2 - (GRAIN_SIZE / 2f)) / (GRAIN_SIZE / 2f));

    final var offset1 = (int) (state.phase1 * (PITCH_RATIO - 1.0f));
    final var offset2 = (int) (phase2 * (PITCH_RATIO - 1.0f));

    var read1 = (state.writePos - offset1) % GRAIN_SIZE;
    if (read1 < 0)
      read1 += GRAIN_SIZE;

    var read2 = (state.writePos - offset2) % GRAIN_SIZE;
    if (read2 < 0)
      read2 += GRAIN_SIZE;

    final var s1 = state.buffer[read1];
    final var s2 = state.buffer[read2];

    return (s1 * w1) + (s2 * w2);
  }

  private static final class PitchState {
    final float[] buffer = new float[GRAIN_SIZE];
    int writePos = 0;
    float phase1 = 0.0f;
  }

}
