package fr.dreamin.dreamvoice.core.filter.impl;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AlienVoiceFilter implements VoiceFilter {

  private static final int BUFFER_SIZE = 4096;
  private static final float MOD_FREQ = 14.0f; // 14 Hz fast sci-fi vibrato

  private final Map<UUID, AlienState> states = new ConcurrentHashMap<>();

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
  public short[] process(final @NotNull short[] samples, final @Nullable VPlayer player) {
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, k -> new AlienState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      final var dry = (float) samples[i];
      state.buffer[state.writeIndex] = dry;
      state.writeIndex = (state.writeIndex + 1) % BUFFER_SIZE;

      state.phase += (2.0 * Math.PI * MOD_FREQ / 48000.0);
      if (state.phase >= 2.0 * Math.PI)
        state.phase -= 2.0 * Math.PI;

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

      output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(out)));
    }

    return output;
  }

  @Override
  public void resetState(final @NotNull UUID playerUuid) {
    this.states.remove(playerUuid);
  }

  private static final class AlienState {
    final float[] buffer = new float[BUFFER_SIZE];
    int writeIndex = 0;
    double phase = 0.0;
  }

}
