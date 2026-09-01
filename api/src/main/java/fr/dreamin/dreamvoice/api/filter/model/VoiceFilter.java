package fr.dreamin.dreamvoice.api.filter.model;

import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Interface representing a real-time Digital Signal Processing (DSP) voice filter.
 * Operates directly on 16-bit 48kHz PCM audio samples.
 */
public interface VoiceFilter {

  /**
   * Unique identifier of the filter (e.g. "disguise", "robot", "phone").
   *
   * @return the filter ID
   */
  @NotNull String getId();

  /**
   * Human-readable display name of the filter.
   *
   * @return the filter name
   */
  @NotNull String getName();

  /**
   * Execution priority when multiple filters are chained (higher runs first).
   *
   * @return the priority integer
   */
  default int getPriority() {
    return 0;
  }

  /**
   * Processes an array of 16-bit PCM audio samples.
   *
   * @param samples raw 16-bit 48kHz audio PCM samples
   * @param player  optional target player context
   * @return modified audio samples
   */
  short[] process(final short @NotNull [] samples, final @Nullable VPlayer player);

  /**
   * Resets any stateful DSP history/buffers (e.g. delays, ring modulators) for a specific player.
   *
   * @param playerUuid the UUID of the player
   */
  default void resetState(final @NotNull UUID playerUuid) {
  }

}
