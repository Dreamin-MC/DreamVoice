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
 */
public final class MegaphoneVoiceFilter implements VoiceFilter {

  private static final int SLAP_DELAY = 1920; // 40ms slapback echo
  private static final int BUFFER_SIZE = 4096;

  // Bandpass 700Hz to 3200Hz
  private static final float HP_ALPHA = 0.915f;
  private static final float LP_ALPHA = 0.340f;

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
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    final var state = this.states.computeIfAbsent(uuid, _ -> new MegaphoneState());

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      final var input = (float) samples[i];

      // High-pass (cut low rumble)
      final var hp = HP_ALPHA * (state.prevHp + input - state.prevInput);
      state.prevInput = input;
      state.prevHp = hp;

      // Low-pass (cut high treble)
      state.prevLp = state.prevLp + LP_ALPHA * (hp - state.prevLp);

      // Horn overdrive distortion
      var x = state.prevLp / 16000.0f;
      if (x > 1.0f)
        x = 1.0f;
      else if (x < -1.0f)
        x = -1.0f;
      else
        x = x - (x * x * x) / 3.0f;

      final var distorted = x * 22000.0f;

      // Slapback echo (megaphone horn acoustic reflection)
      final var echo = state.getEcho();
      state.writeEcho(distorted);

      final var result = (distorted * 0.85f) + (echo * 0.40f);
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
    float prevInput = 0.0f;
    float prevHp = 0.0f;
    float prevLp = 0.0f;
    final float[] delay = new float[BUFFER_SIZE];
    int writeIndex = 0;

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
