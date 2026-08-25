package fr.dreamin.dreamvoice.core.utils;

import fr.dreamin.dreamvoice.core.DreamVoice;
import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class RawUtils {

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  public static byte[] mp3toPcm48Hz(final @NotNull byte[] mp3Data) throws Exception {
    final var tempMp3 = Files.createTempFile("audio", ".mp3");
    try {
      Files.write(tempMp3, mp3Data);

      try (final var ais = AudioSystem.getAudioInputStream(tempMp3.toFile())) {
        final var targetFormat = new AudioFormat(48000f, 16, 1, true, false);
        try (final var convertedAis = AudioSystem.getAudioInputStream(targetFormat, ais)) {
          final var buffer = new byte[4096];
          final var out = new ByteArrayOutputStream();
          int bytesRead;
          while ((bytesRead = convertedAis.read(buffer)) != -1)
            out.write(buffer, 0, bytesRead);
          return out.toByteArray();
        }
      }
    } catch (Exception e) {
      return urlToPcm48HzFFmpeg(mp3Data);
    } finally {
      Files.delete(tempMp3);
    }
  }

  private static byte[] urlToPcm48HzFFmpeg(final @NotNull byte[] mp3Data) throws Exception {
    final var tempMp3 = Files.createTempFile("ffmpeg_in", ".mp3");
    final var tempPcm = Files.createTempFile("ffmpeg_out", ".pcm");

    try {
      Files.write(tempMp3, mp3Data);

      final var ffmpeg = new File(DreamVoice.getInstance().getDataFolder(), "ffmpeg.exe").getAbsolutePath();

      final var pb = new ProcessBuilder(
        ffmpeg, "-y", "-i", tempMp3.toString(),
        "-ar", "48000", "-ac", "1", "-f", "s16le", tempPcm.toString()
      );
      pb.redirectErrorStream(true);

      final var process = pb.start();
      final var finished = process.waitFor(10, TimeUnit.SECONDS);

      if (!finished || process.exitValue() != 0) {
        final var error = new BufferedReader(new InputStreamReader(process.getInputStream())).lines().collect(Collectors.joining("\n"));
        throw new IOException("FFmpeg failed: " + error);
      }

      return Files.readAllBytes(tempPcm);
    } finally {
      Files.deleteIfExists(tempMp3);
      Files.deleteIfExists(tempPcm);
    }
  }

  public static byte[] urlToPcm48Hz(final @NotNull String url) throws Exception {
    final var request = HttpRequest.newBuilder(URI.create(url)).build();
    final var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

    if (response.statusCode() != 200)
      throw new IOException("HTTP error " + response.statusCode());

    return mp3toPcm48Hz(response.body());
  }

  public static byte[] generateBeep(final double frequency, final int durationMs) {
    final var sampleRate = 48000;
    final var samples = sampleRate * durationMs / 1000;
    final var buffer = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN);

    for (int i = 0; i < samples; i++) {
      final var t = i / (double) sampleRate;
      final var sample = (short) (Math.sin(2 * Math.PI * frequency * t) * 16000);
      buffer.putShort(sample);
    }

    return buffer.array();
  }

  public static short[] bytesToShorts(final @NotNull byte[] pcmBytes) {
    if (pcmBytes.length < 2)
      return new short[0];

    final var shorts = new short[pcmBytes.length / 2];
    ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
    return shorts;
  }

  public static short[] fileToShorts48Hz(final @NotNull File file) throws Exception {
    final var bytes = Files.readAllBytes(file.toPath());
    final var pcm = mp3toPcm48Hz(bytes);
    return bytesToShorts(pcm);
  }

  public static short[] urlToShorts48Hz(final @NotNull String url) throws Exception {
    final var pcm = urlToPcm48Hz(url);
    return bytesToShorts(pcm);
  }

  public static byte[] oggToPcm48Hz(final @NotNull byte[] oggPath) throws Exception {
    return mp3toPcm48Hz(oggPath);
  }

}


