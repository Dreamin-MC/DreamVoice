package fr.dreamin.dreamvoice.api.recording.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;

public interface VoiceRecordingService {

  void init(final @NotNull VoicechatServerApi api);

  VoicechatServerApi getAPI();

  Collection<VoiceRecording> getVoiceRecordings();

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

}
