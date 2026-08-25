package fr.dreamin.dreamvoice.api.recording.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

public interface VoiceRecordingService {

  void init(final @NotNull VoicechatServerApi api);

  VoicechatServerApi getAPI();

  Collection<VoiceRecording> getVoiceRecordings();

  @Nullable VoiceRecording getVoiceRecording(final @NotNull UUID uuid);

  void register(final @NotNull VoiceRecording voiceRecording);
  void unregister(final @NotNull VoiceRecording voiceRecording);
  void unregister(final @NotNull UUID uuid);

  void unregisterAll();

  void playRecordingTo(@NotNull VoicechatConnection connection, @NotNull VoiceRecording recording);

  default void playRecordingTo(@NotNull Collection<VoicechatConnection> connections, @NotNull VoiceRecording recording) {
    connections.forEach(conn -> playRecordingTo(conn, recording));
  }

  VoiceRecording startRecording(final @NotNull UUID uuid);

  void stopRecording(final @NotNull UUID uuid);

  @NotNull org.bukkit.inventory.ItemStack linkItem(final @NotNull org.bukkit.inventory.ItemStack item, final @NotNull VoiceRecording recording);
  @NotNull org.bukkit.inventory.ItemStack linkItem(final @NotNull org.bukkit.inventory.ItemStack item, final @NotNull UUID recordingUuid);

  @NotNull org.bukkit.inventory.ItemStack createCassette(final @NotNull VoiceRecording recording);

  java.util.concurrent.CompletableFuture<VoiceRecording> createRecordingFromPcm(final @NotNull short[] pcm, final @NotNull UUID speakerUuid);

  java.util.concurrent.CompletableFuture<VoiceRecording> createRecordingFromFile(final @NotNull java.io.File file, final @Nullable String name);

  java.util.concurrent.CompletableFuture<VoiceRecording> createRecordingFromFile(final @NotNull String fileName);

  java.util.concurrent.CompletableFuture<VoiceRecording> createRecordingFromUrl(final @NotNull String url, final @Nullable String name);

  @Nullable VoiceRecording sliceRecording(final @NotNull UUID recordingUuid, final @NotNull java.time.Instant timestamp, final @NotNull java.time.Duration duration);

  @Nullable VoiceRecording sliceRecording(final @NotNull UUID recordingUuid, final long startOffsetMs, final long durationMs);

  @Nullable VoiceRecording sliceLastRecording(final @NotNull UUID recordingUuid, final @NotNull java.time.Duration duration);

  @Nullable VoiceRecording sliceLastRecording(final @NotNull UUID recordingUuid, final long durationMs);

}



