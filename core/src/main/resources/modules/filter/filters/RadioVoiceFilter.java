package fr.dreamin.dreamvoice.core.filter.impl;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DSP audio filter simulating a military/police walkie-talkie transceiver radio.
 * Uses cascaded 2nd-order Butterworth filters (450Hz HP, 2700Hz LP), mid-presence horn boost,
 * warm analog saturation, and subtle RF carrier hiss.
 */
public final class RadioVoiceFilter implements VoiceFilter {

  private static final float SAMPLE_RATE = 48000.0f;
  private final Map<UUID, RadioState> states = new ConcurrentHashMap<>();

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

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
  public short[] process(final short @NonNull [] samples, final @Nullable VPlayer player) {
    if (samples.length == 0)
      return samples;

    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, _ -> new RadioState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      final var input = (float) samples[i];

      // 1. Cascaded 2nd-order Butterworth bandpass (450Hz to 2700Hz, 24 dB/octave)
      final var hp = state.hp.process(input);
      final var lp = state.lp.process(hp);

      // 2. Transceiver speaker presence resonance peak (+4dB at 1.5kHz)
      final var peak = state.peak.process(lp);

      // 3. Warm analog soft overdrive (tanh) to emulate walkie-talkie preamp distortion without harsh digital buzzing
      var norm = peak / 18000.0f;
      norm = (float) Math.tanh(norm * 1.5f) * 0.85f;

      // 4. Subtle RF carrier noise hiss (-42 dB) while speech is active
      state.noise = state.noise * 0.88f + (state.random.nextFloat() - 0.5f) * 0.12f;
      final var rfHiss = state.noise * 180.0f;

      final var result = (norm * 24000.0f) + rfHiss;
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

  private static final class RadioState {
    final Biquad hp = new Biquad();
    final Biquad lp = new Biquad();
    final Biquad peak = new Biquad();
    final Random random = new Random();
    float noise = 0.0f;

    RadioState() {
      this.hp.setHighPass(480.0f, SAMPLE_RATE, 0.707f);
      this.lp.setLowPass(2600.0f, SAMPLE_RATE, 0.707f);
      this.peak.setPeaking(1500.0f, SAMPLE_RATE, 4.0f, 1.2f);
    }
  }

  static final class Biquad {
    float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f, a1 = 0.0f, a2 = 0.0f;
    float x1 = 0.0f, x2 = 0.0f, y1 = 0.0f, y2 = 0.0f;

    void setHighPass(final float freq, final float sampleRate, final float q) {
      final var w0 = 2.0 * Math.PI * freq / sampleRate;
      final var cos = Math.cos(w0);
      final var alpha = Math.sin(w0) / (2.0 * q);
      final var a0 = 1.0 + alpha;
      this.b0 = (float) ((1.0 + cos) / (2.0 * a0));
      this.b1 = (float) (-(1.0 + cos) / a0);
      this.b2 = (float) ((1.0 + cos) / (2.0 * a0));
      this.a1 = (float) ((-2.0 * cos) / a0);
      this.a2 = (float) ((1.0 - alpha) / a0);
    }

    void setLowPass(final float freq, final float sampleRate, final float q) {
      final var w0 = 2.0 * Math.PI * freq / sampleRate;
      final var cos = Math.cos(w0);
      final var alpha = Math.sin(w0) / (2.0 * q);
      final var a0 = 1.0 + alpha;
      this.b0 = (float) ((1.0 - cos) / (2.0 * a0));
      this.b1 = (float) ((1.0 - cos) / a0);
      this.b2 = (float) ((1.0 - cos) / (2.0 * a0));
      this.a1 = (float) ((-2.0 * cos) / a0);
      this.a2 = (float) ((1.0 - alpha) / a0);
    }

    void setPeaking(final float freq, final float sampleRate, final float gainDb, final float q) {
      final var w0 = 2.0 * Math.PI * freq / sampleRate;
      final var cos = Math.cos(w0);
      final var A = Math.pow(10.0, gainDb / 40.0);
      final var alpha = Math.sin(w0) / (2.0 * q);
      final var a0 = 1.0 + alpha / A;
      this.b0 = (float) ((1.0 + alpha * A) / a0);
      this.b1 = (float) ((-2.0 * cos) / a0);
      this.b2 = (float) ((1.0 - alpha * A) / a0);
      this.a1 = (float) ((-2.0 * cos) / a0);
      this.a2 = (float) ((1.0 - alpha / A) / a0);
    }

    float process(final float in) {
      final var out = this.b0 * in + this.b1 * this.x1 + this.b2 * this.x2 - this.a1 * this.y1 - this.a2 * this.y2;
      this.x2 = this.x1;
      this.x1 = in;
      this.y2 = this.y1;
      this.y1 = out;
      return out;
    }

    void reset() {
      this.x1 = this.x2 = this.y1 = this.y2 = 0.0f;
    }
  }

}


