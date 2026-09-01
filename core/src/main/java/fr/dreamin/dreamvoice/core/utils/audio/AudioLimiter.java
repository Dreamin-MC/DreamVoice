package fr.dreamin.dreamvoice.core.utils.audio;

/**
 * High performance soft-knee limiter and anti-clipping processor.
 * Prevents harsh digital clipping when multiple voice streams or filters combine.
 */
public final class AudioLimiter {

  private static final float THRESHOLD = 0.85f; // 85% full scale (-1.4 dB)
  private static final float MAX_VAL = 32767.0f;

  public static short[] process(final short[] pcm) {
    if (pcm == null || pcm.length == 0)
      return pcm;

    for (int i = 0; i < pcm.length; i++) {
      final var sample = pcm[i];
      final var norm = sample / MAX_VAL;
      final var abs = Math.abs(norm);

      if (abs > THRESHOLD) {
        final var excess = abs - THRESHOLD;
        final var compressed = THRESHOLD + (1.0f - THRESHOLD) * (float) Math.tanh(excess / (1.0f - THRESHOLD));
        final var sign = norm < 0 ? -1.0f : 1.0f;
        pcm[i] = (short) Math.clamp(Math.round(sign * compressed * MAX_VAL), Short.MIN_VALUE, Short.MAX_VALUE);
      }
    }

    return pcm;
  }

}
