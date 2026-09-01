package fr.dreamin.dreamvoice.api.speaker.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

public interface VoiceSpeakerService {

  VoicechatServerApi getAPI();

  void init(final @NotNull VoicechatServerApi api);

  Collection<Speaker> getSpeakers();

  @Nullable Speaker getSpeaker(final @NotNull UUID uuid);

  @Nullable Speaker getSpeaker(final @NotNull String name);

  void register(final @NotNull Speaker speaker);

  void unregister(final @NotNull UUID uuid);
  void unregister(final @NotNull Speaker speaker);

  void unregisterAll();

  VolumeCategory getVolumeCategory();

  void playRecording(final @NotNull Speaker speaker, final @NotNull VoiceRecording recording);

  void playSound(final @NotNull Speaker speaker, final @NotNull short[] pcm);
  void playSound(final @NotNull Speaker speaker, final @NotNull short[] pcm, final boolean loop);

  void playSoundFile(final @NotNull Speaker speaker, final @NotNull String fileName, final boolean loop);

  void playSoundUrl(final @NotNull Speaker speaker, final @NotNull String url, final boolean loop);

  void stopSound(final @NotNull Speaker speaker);

  void save();

  void load();

  void save(final @NotNull UUID uuid);

}


