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
 * DSP audio filter simulating a muffled voice effect (behind doors, masks, walls) with simple low-pass attenuation.
 */
public final class MuffledVoiceFilter implements VoiceFilter {

  // Low-pass ~1200Hz at 48kHz
  private static final float LP_ALPHA = 0.145f;
  private final Map<UUID, Float> previousOutputs = new ConcurrentHashMap<>();

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public @NotNull String getId() {
    return "muffled";
  }

  @Override
  public @NotNull String getName() {
    return "Muffled";
  }

  @Override
  public int getPriority() {
    return 5;
  }

  @Override
  public short[] process(final short @NonNull [] samples, final @Nullable VPlayer player) {
    final var output = new short[samples.length];
    final var uuid = player != null ? player.getUuid() : new UUID(0, 0);

    var prev = this.previousOutputs.getOrDefault(uuid, 0.0f);

    for (int i = 0; i < samples.length; i++) {
      final var input = samples[i] * 0.85f;
      prev = prev + LP_ALPHA * (input - prev);
      output[i] = (short) Math.clamp(Math.round(prev), Short.MIN_VALUE, Short.MAX_VALUE);
    }

    this.previousOutputs.put(uuid, prev);
    return output;
  }

  @Override
  public void resetState(final @NotNull UUID playerUuid) {
    this.previousOutputs.remove(playerUuid);
  }

}
