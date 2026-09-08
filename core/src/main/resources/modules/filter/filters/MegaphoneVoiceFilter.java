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
 * DSP audio filter simulating a bullhorn / megaphone horn overdrive with slapback reflection.
 * Features 2nd-order horn bandpass, acoustic horn resonance peaking, soft overdrive, and normalized slapback.
 */
public final class MegaphoneVoiceFilter implements VoiceFilter {

  private static final int SLAP_DELAY = 1920; // 40ms slapback echo at 48kHz
  private static final int BUFFER_SIZE = 4096;
  private static final float SAMPLE_RATE = 48000.0f;

  private final Map<UUID, MegaphoneState> states = new ConcurrentHashMap<>();

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public @NotNull String getId() {
    return "megaphone";
  }

  @Override
  public @NotNull String getName() {
    return "Megaphone";
  }

  @Override
  public int getPriority() {
    return 25;
  }

  @Override
  public short[] process(final short @NonNull [] samples, final @Nullable VPlayer player) {
    if (samples.length == 0)
      return samples;

    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, _ -> new MegaphoneState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      final var input = (float) samples[i];

      // 1. Horn acoustic bandpass (600Hz - 3400Hz)
      final var hp = state.hp.process(input);
      final var lp = state.lp.process(hp);

      // 2. Flared megaphone horn bell resonance (+5 dB at 1100 Hz)
      final var bell = state.peak.process(lp);

      // 3. Horn driver overdrive
      var x = bell / 16000.0f;
      x = (float) Math.tanh(x * 1.4f) * 0.85f;
      final var distorted = x * 22000.0f;

      // 4. Slapback reflection (horn acoustic bounce)
      final var echo = state.getEcho();
      state.writeEcho(distorted);

      final var result = (distorted * 0.75f) + (echo * 0.25f);
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

  private static final class MegaphoneState {
    final Biquad hp = new Biquad();
    final Biquad lp = new Biquad();
    final Biquad peak = new Biquad();
    final float[] delay = new float[BUFFER_SIZE];
    int writeIndex = 0;

    MegaphoneState() {
      this.hp.setHighPass(600.0f, SAMPLE_RATE, 0.707f);
      this.lp.setLowPass(3400.0f, SAMPLE_RATE, 0.707f);
      this.peak.setPeaking(1100.0f, SAMPLE_RATE, 5.0f, 1.4f);
    }

    void writeEcho(final float sample) {
      this.delay[this.writeIndex] = sample;
      this.writeIndex = (this.writeIndex + 1) % BUFFER_SIZE;
    }

    float getEcho() {
      var readIndex = this.writeIndex - MegaphoneVoiceFilter.SLAP_DELAY;
      if (readIndex < 0)
        readIndex += BUFFER_SIZE;
      return this.delay[readIndex];
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


