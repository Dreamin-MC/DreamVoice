package fr.dreamin.dreamvoice.core.filter.impl;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RobotVoiceFilter implements VoiceFilter {

  private static final double MOD_FREQ = 80.0; // 80 Hz metallic carrier
  private static final double SAMPLE_RATE = 48000.0;
  private static final double TWO_PI = 2.0 * Math.PI;

  private final Map<UUID, Double> phases = new ConcurrentHashMap<>();

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
  public short[] process(final @NotNull short[] samples, final @Nullable VPlayer player) {
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);
    var phase = this.phases.getOrDefault(uuid, 0.0);

    final var output = new short[samples.length];
    final var phaseIncrement = TWO_PI * MOD_FREQ / SAMPLE_RATE;

    for (int i = 0; i < samples.length; i++) {
      final var carrier = Math.sin(phase) + 0.5 * Math.sin(phase * 2.0);
      phase += phaseIncrement;
      if (phase >= TWO_PI)
        phase -= TWO_PI;

      final var dry = (double) samples[i];
      final var modulated = dry * carrier * 0.85;

      var val = modulated / 20000.0;
      if (val > 1.0)
        val = 1.0;
      else if (val < -1.0)
        val = -1.0;
      else
        val = val - (val * val * val) / 3.0;

      final var mixed = (dry * 0.20) + (val * 24000.0 * 0.80);
      output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(mixed)));
    }

    this.phases.put(uuid, phase);
    return output;
  }

  @Override
  public void resetState(final @NotNull UUID playerUuid) {
    this.phases.remove(playerUuid);
  }

}

