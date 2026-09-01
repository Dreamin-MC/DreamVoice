package fr.dreamin.dreamvoice.api.recording.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

  @NotNull ItemStack linkItem(final @NotNull ItemStack item, final @NotNull VoiceRecording recording);
  @NotNull ItemStack linkItem(final @NotNull ItemStack item, final @NotNull UUID recordingUuid);

  @NotNull ItemStack createCassette(final @NotNull VoiceRecording recording);

  CompletableFuture<VoiceRecording> createRecordingFromPcm(final @NotNull short[] pcm, final @NotNull UUID speakerUuid);

  CompletableFuture<VoiceRecording> createRecordingFromFile(final @NotNull File file, final @Nullable String name);

  CompletableFuture<VoiceRecording> createRecordingFromFile(final @NotNull String fileName);

  CompletableFuture<VoiceRecording> createRecordingFromUrl(final @NotNull String url, final @Nullable String name);

  @Nullable VoiceRecording sliceRecording(final @NotNull UUID recordingUuid, final @NotNull Instant timestamp, final @NotNull Duration duration);

  @Nullable VoiceRecording sliceRecording(final @NotNull UUID recordingUuid, final long startOffsetMs, final long durationMs);

  @Nullable VoiceRecording sliceLastRecording(final @NotNull UUID recordingUuid, final @NotNull Duration duration);

  @Nullable VoiceRecording sliceLastRecording(final @NotNull UUID recordingUuid, final long durationMs);

}


