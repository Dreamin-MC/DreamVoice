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
    final RadioVoiceFilter.Biquad hp = new RadioVoiceFilter.Biquad();
    final RadioVoiceFilter.Biquad lp = new RadioVoiceFilter.Biquad();
    final RadioVoiceFilter.Biquad peak = new RadioVoiceFilter.Biquad();
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

}
