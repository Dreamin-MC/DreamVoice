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
 * DSP audio filter simulating a classic telephone carbon microphone bandpass (350Hz to 3400Hz).
 * Features 2nd-order biquad G.711 telecom bandpass and vintage carbon microphone asymmetrical saturation.
 */
public final class TelephoneVoiceFilter implements VoiceFilter {

  private static final float SAMPLE_RATE = 48000.0f;
  private final Map<UUID, PhoneState> states = new ConcurrentHashMap<>();

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

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
  public short[] process(final short @NonNull [] samples, final @Nullable VPlayer player) {
    if (samples.length == 0)
      return samples;

    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, _ -> new PhoneState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      final var input = (float) samples[i];

      // 2nd-order Butterworth bandpass (350Hz - 3400Hz)
      final var hp = state.hp.process(input);
      final var lp = state.lp.process(hp);

      // Carbon capsule asymmetrical soft saturation
      var x = lp / 16000.0f;
      if (x > 0.0f) {
        x = x - (0.25f * x * x);
      } else {
        x = x + (0.12f * x * x);
      }
      x = (float) Math.tanh(x * 1.25f) * 0.88f;

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

  private static final class PhoneState {
    final RadioVoiceFilter.Biquad hp = new RadioVoiceFilter.Biquad();
    final RadioVoiceFilter.Biquad lp = new RadioVoiceFilter.Biquad();

    PhoneState() {
      this.hp.setHighPass(350.0f, SAMPLE_RATE, 0.707f);
      this.lp.setLowPass(3400.0f, SAMPLE_RATE, 0.707f);
    }
  }

}
