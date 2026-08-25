package fr.dreamin.dreamvoice.core.filter.impl;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RadioVoiceFilter implements VoiceFilter {

  // High-pass 300Hz, Low-pass 3400Hz at 48kHz
  private static final float HP_ALPHA = 0.962f;
  private static final float LP_ALPHA = 0.360f;

  private final Map<UUID, FilterState> states = new ConcurrentHashMap<>();

  @Override
  public @NotNull String getId() {
    return "radio";
  }

  @Override
  public @NotNull String getName() {
    return "Radio";
  }

  @Override
  public int getPriority() {
    return 15;
  }

  @Override
  public short[] process(final @NotNull short[] samples, final @Nullable VPlayer player) {
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, k -> new FilterState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      final var input = (float) samples[i];

      // High-pass filter (cut below 450Hz)
      final var hp = HP_ALPHA * (state.prevHp + input - state.prevInput);
      state.prevInput = input;
      state.prevHp = hp;

      // Low-pass filter (cut above 2800Hz)
      state.prevLp = state.prevLp + LP_ALPHA * (hp - state.prevLp);

      // Walkie-talkie 10-bit quantization & soft clipping
      final var quantized = Math.round(state.prevLp / 32.0f) * 32.0f;
      var x = quantized / 20000.0f;
      if (x > 1.0f)
        x = 1.0f;
      else if (x < -1.0f)
        x = -1.0f;
      else
        x = x - (x * x * x) / 3.0f;

      final var result = x * 24000.0f;
      output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(result)));
    }

    return output;
  }


  @Override
  public void resetState(final @NotNull UUID playerUuid) {
    this.states.remove(playerUuid);
  }

  private static final class FilterState {
    float prevInput = 0.0f;
    float prevHp = 0.0f;
    float prevLp = 0.0f;
  }

}
