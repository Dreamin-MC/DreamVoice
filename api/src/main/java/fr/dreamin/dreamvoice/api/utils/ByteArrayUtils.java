package fr.dreamin.dreamvoice.api.utils;

public final class ByteArrayUtils {

  public static byte[] concat(final byte[] a, final byte[] b) {
    final var result = new byte[a.length + b.length];
    System.arraycopy(a, 0, result, 0, a.length);
    System.arraycopy(b, 0, result, a.length, b.length);
    return result;
  }

}