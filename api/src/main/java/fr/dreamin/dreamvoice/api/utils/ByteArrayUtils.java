package fr.dreamin.dreamvoice.api.utils;

/**
 * Utility methods for raw byte array manipulations.
 */
public final class ByteArrayUtils {

  private ByteArrayUtils() {
  }

  /**
   * Concatenates two byte arrays into a single array.
   *
   * @param a first array
   * @param b second array
   * @return concatenated array
   */
  public static byte[] concat(final byte[] a, final byte[] b) {
    final var result = new byte[a.length + b.length];
    System.arraycopy(a, 0, result, 0, a.length);
    System.arraycopy(b, 0, result, a.length, b.length);
    return result;
  }

}