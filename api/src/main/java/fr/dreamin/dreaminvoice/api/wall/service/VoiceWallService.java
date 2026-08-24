package fr.dreamin.dreaminvoice.api.wall.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import org.jetbrains.annotations.NotNull;

public interface VoiceWallService {

  void init(final @NotNull VoicechatServerApi api);

  boolean isEnable();
  void setEnable(final boolean value);

  boolean isDebug();
  void setDebug(final boolean value);

}
