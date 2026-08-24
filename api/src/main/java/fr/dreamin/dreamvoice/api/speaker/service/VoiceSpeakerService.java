package fr.dreamin.dreamvoice.api.speaker.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;

public interface VoiceSpeakerService {

  VoicechatServerApi getAPI();

  void init(final @NotNull VoicechatServerApi api);

  Collection<Speaker> getSpeakers();

  void register(final @NotNull Speaker speaker);

  void unregister(final @NotNull UUID uuid);
  void unregister(final @NotNull Speaker speaker);

  void unregisterAll();

  VolumeCategory getVolumeCategory();

}
