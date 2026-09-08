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

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  public static byte[] mp3toPcm48Hz(final byte @NotNull [] mp3Data) throws Exception {
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

  private static byte[] urlToPcm48HzFFmpeg(final byte @NotNull [] mp3Data) throws Exception {
    final var tempMp3 = Files.createTempFile("ffmpeg_in", ".mp3");
    final var tempPcm = Files.createTempFile("ffmpeg_out", ".pcm");

    try {
      Files.write(tempMp3, mp3Data);

      final var ffmpeg = resolveFfmpegBinary();

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

  public static short[] bytesToShorts(final byte @NotNull [] pcmBytes) {
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

  public static byte[] oggToPcm48Hz(final byte @NotNull [] oggPath) throws Exception {
    return mp3toPcm48Hz(oggPath);
  }

  public static void pcmToAudioFile(final short @NotNull [] pcm, final @NotNull File destination, final @NotNull String format) throws Exception {
    if (destination.getParentFile() != null && !destination.getParentFile().exists())
      destination.getParentFile().mkdirs();

    final var pcmBytes = new byte[pcm.length * 2];
    ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm);

    if (format.equalsIgnoreCase("wav")) {
      writeWavFile(pcmBytes, destination, 48000, 1, 16);
      return;
    }

    // Convert via FFmpeg if available
    final var tempPcm = Files.createTempFile("ffmpeg_pcm_in", ".pcm");
    try {
      Files.write(tempPcm, pcmBytes);

      final var ffmpegBin = resolveFfmpegBinary();

      final var pb = new ProcessBuilder(
        ffmpegBin, "-y",
        "-f", "s16le", "-ar", "48000", "-ac", "1", "-i", tempPcm.toString(),
        destination.getAbsolutePath()
      );
      pb.redirectErrorStream(true);

      Process process = null;
      try {
        process = pb.start();
      } catch (IOException e) {
        // Fallback: write standard WAV directly to destination
        writeWavFile(pcmBytes, destination, 48000, 1, 16);
        DreamVoice.getInstance().getLogger().warning("[DreamVoice] FFmpeg not found or not executable. Exported as WAV format.");
        return;
      }

      final var finished = process.waitFor(20, TimeUnit.SECONDS);

      if (!finished || process.exitValue() != 0 || !destination.exists() || destination.length() == 0) {
        // Fallback: write standard WAV directly to destination
        writeWavFile(pcmBytes, destination, 48000, 1, 16);
        DreamVoice.getInstance().getLogger().warning("[DreamVoice] FFmpeg conversion failed. Exported as WAV fallback.");
      }
    } finally {
      Files.deleteIfExists(tempPcm);
    }
  }

  public static void writeWavFile(
    final byte @NotNull [] pcmData,
    final @NotNull File outputFile,
    final int sampleRate,
    final int channels,
    final int bitsPerSample
  ) throws IOException {
    final var totalAudioLen = pcmData.length;
    final var totalDataLen = totalAudioLen + 36;
    final var byteRate = sampleRate * channels * bitsPerSample / 8;

    try (final var fos = new java.io.FileOutputStream(outputFile)) {
      final var header = new byte[44];

      // RIFF/WAVE header
      header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
      header[4] = (byte) (totalDataLen & 0xff);
      header[5] = (byte) ((totalDataLen >> 8) & 0xff);
      header[6] = (byte) ((totalDataLen >> 16) & 0xff);
      header[7] = (byte) ((totalDataLen >> 24) & 0xff);
      header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
      header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
      header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0; // 16 for PCM format chunk
      header[20] = 1; header[21] = 0; // PCM format
      header[22] = (byte) channels; header[23] = 0;
      header[24] = (byte) (sampleRate & 0xff);
      header[25] = (byte) ((sampleRate >> 8) & 0xff);
      header[26] = (byte) ((sampleRate >> 16) & 0xff);
      header[27] = (byte) ((sampleRate >> 24) & 0xff);
      header[28] = (byte) (byteRate & 0xff);
      header[29] = (byte) ((byteRate >> 8) & 0xff);
      header[30] = (byte) ((byteRate >> 16) & 0xff);
      header[31] = (byte) ((byteRate >> 24) & 0xff);
      header[32] = (byte) (channels * bitsPerSample / 8); header[33] = 0; // block align
      header[34] = (byte) bitsPerSample; header[35] = 0;
      header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
      header[40] = (byte) (totalAudioLen & 0xff);
      header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
      header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
      header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

      fos.write(header, 0, 44);
      fos.write(pcmData);
    }
  }

  public static @NotNull String resolveFfmpegBinary() {
    final var plugin = DreamVoice.getInstance();
    final var pluginFolder = plugin.getDataFolder();
    final var isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
    final var binaryName = isWindows ? "ffmpeg.exe" : "ffmpeg";
    final var localBinary = new File(pluginFolder, binaryName);

    // If binary not present in plugin folder, check if bundled in jar resources and extract it
    if (!localBinary.exists()) {
      try (final var in = plugin.getResource(binaryName)) {
        if (in != null) {
          if (!pluginFolder.exists())
            pluginFolder.mkdirs();
          Files.copy(in, localBinary.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          plugin.getLogger().info("[DreamVoice] Extracted bundled " + binaryName + " to " + localBinary.getAbsolutePath());
        }
      } catch (Throwable t) {
        plugin.getLogger().warning("[DreamVoice] Could not extract bundled " + binaryName + ": " + t.getMessage());
      }
    }

    if (localBinary.exists() && (!localBinary.getName().endsWith(".exe") || isWindows)) {
      if (!isWindows && !localBinary.canExecute()) {
        try {
          localBinary.setExecutable(true, false);
        } catch (Throwable ignored) {}
      }
      if (isWindows || localBinary.canExecute()) {
        return localBinary.getAbsolutePath();
      }
    }

    // Default to system PATH ffmpeg
    return "ffmpeg";
  }

}


