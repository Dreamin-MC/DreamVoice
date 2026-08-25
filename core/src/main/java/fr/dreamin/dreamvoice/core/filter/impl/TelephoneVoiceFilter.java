package fr.dreamin.dreamvoice.core.filter.impl;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TelephoneVoiceFilter implements VoiceFilter {

  // Classic telephone bandpass: 350Hz to 3400Hz
  private static final float HP_ALPHA = 0.955f;
  private static final float LP_ALPHA = 0.360f;

  private final Map<UUID, PhoneState> states = new ConcurrentHashMap<>();

  @Override
  public @NotNull String getId() {
    return "telephone";
  }

  @Override
  public @NotNull String getName() {
    return "Telephone";
  }

  @Override
  public int getPriority() {
    return 15;
  }

  @Override
  public short[] process(final @NotNull short[] samples, final @Nullable VPlayer player) {
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, k -> new PhoneState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      final var input = (float) samples[i];

      // High-pass
      final var hp = HP_ALPHA * (state.prevHp + input - state.prevInput);
      state.prevInput = input;
      state.prevHp = hp;

      // Low-pass
      state.prevLp = state.prevLp + LP_ALPHA * (hp - state.prevLp);

      // Telephone line carbon mic non-linear saturation
      var x = state.prevLp / 14000.0f;
      if (x > 1.0f)
        x = 1.0f;
      else if (x < -1.0f)
        x = -1.0f;
      else
        x = (1.2f * x) - (0.2f * x * x * x);

      final var result = x * 18000.0f;
      output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(result)));
    }

    return output;
  }

  @Override
  public void resetState(final @NotNull UUID playerUuid) {
    this.states.remove(playerUuid);
  }

  private static final class PhoneState {
    float prevInput = 0.0f;
    float prevHp = 0.0f;
    float prevLp = 0.0f;
  }

}
