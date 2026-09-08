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
 * DSP audio filter simulating a robotic synthesizer carrier ring modulator.
 * Uses a normalized metallic carrier and balanced mix to prevent harsh distortion and clipping.
 */
public final class RobotVoiceFilter implements VoiceFilter {

  private static final double MOD_FREQ = 80.0; // 80 Hz metallic carrier
  private static final double SAMPLE_RATE = 48000.0;
  private static final double TWO_PI = 2.0 * Math.PI;
  private static final double PHASE_INCREMENT = TWO_PI * MOD_FREQ / SAMPLE_RATE;

  private final Map<UUID, Double> phases = new ConcurrentHashMap<>();

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public @NotNull String getId() {
    return "robot";
  }

  @Override
  public @NotNull String getName() {
    return "Robot";
  }

  @Override
  public int getPriority() {
    return 30;
  }

  @Override
  public short[] process(final short @NonNull [] samples, final @Nullable VPlayer player) {
    if (samples.length == 0)
      return samples;

    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    var phase = this.phases.getOrDefault(uuid, 0.0);

    final var output = new short[samples.length];

    for (int i = 0; i < samples.length; i++) {
      // Normalized metallic carrier (strictly peak <= 1.0)
      final var carrier = (0.70 * Math.sin(phase)) + (0.30 * Math.sin(phase * 2.0));
      phase += PHASE_INCREMENT;
      if (phase >= TWO_PI)
        phase -= TWO_PI;

      final var dry = (double) samples[i];
      final var modulated = dry * carrier;

      var norm = modulated / 24000.0;
      if (norm > 1.0)
        norm = 1.0;
      else if (norm < -1.0)
        norm = -1.0;
      else
        norm = norm - (norm * norm * norm) / 3.0;

      final var mixed = (dry * 0.25) + (norm * 22000.0 * 0.75);
      output[i] = (short) Math.clamp(Math.round(mixed), Short.MIN_VALUE, Short.MAX_VALUE);
    }

    this.phases.put(uuid, phase);
    return output;
  }

  @Override
  public void resetState(final @NotNull UUID playerUuid) {
    this.phases.remove(playerUuid);
  }

}
