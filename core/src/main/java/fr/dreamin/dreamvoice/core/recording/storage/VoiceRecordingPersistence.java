package fr.dreamin.dreamvoice.core.recording.storage;

import fr.dreamin.dreamvoice.api.recording.model.TimedAudioFrame;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VoiceRecordingPersistence {

  private static final byte[] MAGIC = "DVREC1".getBytes(StandardCharsets.UTF_8);

  public static void save(final @NotNull VoiceRecording recording, final @NotNull File targetDir) {
    if (!targetDir.exists())
      targetDir.mkdirs();

    final var file = new File(targetDir, recording.getUuid() + ".dv");

    try (final var fos = new FileOutputStream(file);
         final var dos = new DataOutputStream(fos)) {

      dos.write(MAGIC);
      dos.writeLong(recording.getUuid().getMostSignificantBits());
      dos.writeLong(recording.getUuid().getLeastSignificantBits());
      dos.writeLong(recording.getSpeakerUUID().getMostSignificantBits());
      dos.writeLong(recording.getSpeakerUUID().getLeastSignificantBits());

      final var duration = recording.getDuration();
      final var durMs = duration != null ? duration.toMillis() : 0L;
      dos.writeLong(durMs);

      final var frames = recording.getAudioFrames();
      dos.writeInt(frames.size());

      for (final var frame : frames) {
        dos.writeLong(frame.timestampMs());
        dos.writeInt(frame.data().length);
        dos.write(frame.data());
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static @Nullable VoiceRecording load(final @NotNull File file) {
    if (!file.exists() || !file.getName().endsWith(".dv"))
      return null;

    try (final var fis = new FileInputStream(file);
         final var dis = new DataInputStream(fis)) {

      final var magic = new byte[6];
      dis.readFully(magic);
      if (!new String(magic, StandardCharsets.UTF_8).equals("DVREC1"))
        return null;

      final var recUuid = new UUID(dis.readLong(), dis.readLong());
      final var speakerUuid = new UUID(dis.readLong(), dis.readLong());
      final var durMs = dis.readLong();
      final var frameCount = dis.readInt();

      final var recording = new VoiceRecording(speakerUuid);
      // Reflection or field reconstruction
      final var fieldUuid = VoiceRecording.class.getDeclaredField("uuid");
      fieldUuid.setAccessible(true);
      fieldUuid.set(recording, recUuid);

      final var fieldStart = VoiceRecording.class.getDeclaredField("startTime");
      fieldStart.setAccessible(true);
      fieldStart.set(recording, Instant.now().minusMillis(durMs));

      final var fieldDuration = VoiceRecording.class.getDeclaredField("duration");
      fieldDuration.setAccessible(true);
      fieldDuration.set(recording, Duration.ofMillis(durMs));

      for (int i = 0; i < frameCount; i++) {
        final var ts = dis.readLong();
        final var len = dis.readInt();
        final var data = new byte[len];
        dis.readFully(data);
        recording.getAudioFrames().add(new TimedAudioFrame(ts, data));
      }


      return recording;
    } catch (Exception e) {
      return null;
    }
  }

  public static List<VoiceRecording> loadAll(final @NotNull File targetDir) {
    final var list = new ArrayList<VoiceRecording>();
    if (!targetDir.exists())
      return list;

    final var files = targetDir.listFiles((dir, name) -> name.endsWith(".dv"));
    if (files == null)
      return list;

    for (final var f : files) {
      final var rec = load(f);
      if (rec != null)
        list.add(rec);
    }

    return list;
  }

}
