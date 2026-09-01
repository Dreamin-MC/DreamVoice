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
 * DSP audio filter simulating a claustrophobic gas mask / closed helmet acoustic environment.
 */
public final class GasmaskVoiceFilter implements VoiceFilter {

  // Bandpass 500Hz to 1800Hz with heavy boxy resonance
  private static final float HP_ALPHA = 0.935f;
  private static final float LP_ALPHA = 0.210f;

  private final Map<UUID, GasmaskState> states = new ConcurrentHashMap<>();

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public @NotNull String getId() {
    return "gasmask";
  }

  @Override
  public @NotNull String getName() {
    return "Gas Mask / Helmet";
  }

  @Override
  public int getPriority() {
    return 15;
  }

  @Override
  public short[] process(final short @NonNull [] samples, final @Nullable VPlayer player) {
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, _ -> new GasmaskState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      final var input = (float) samples[i];

      // High-pass (cut sub-bass rumble)
      final var hp = HP_ALPHA * (state.prevHp + input - state.prevInput);
      state.prevInput = input;
      state.prevHp = hp;

      // 2-stage low-pass for claustrophobic helmet acoustic isolation
      state.stage1 = state.stage1 + LP_ALPHA * (hp - state.stage1);
      state.stage2 = state.stage2 + LP_ALPHA * (state.stage1 - state.stage2);

      // Mild helmet diaphragm compression
      var x = state.stage2 / 18000.0f;
      if (x > 1.0f)
        x = 1.0f;
      else if (x < -1.0f)
        x = -1.0f;
      else
        x = x - (x * x * x) / 3.0f;

      final var result = x * 22000.0f;
      output[i] = (short) Math.clamp(Math.round(result), Short.MIN_VALUE, Short.MAX_VALUE);
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

  private static final class GasmaskState {
    float prevInput = 0.0f;
    float prevHp = 0.0f;
    float stage1 = 0.0f;
    float stage2 = 0.0f;
  }

}
