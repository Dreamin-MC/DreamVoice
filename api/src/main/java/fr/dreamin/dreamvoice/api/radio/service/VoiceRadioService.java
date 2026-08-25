package fr.dreamin.dreamvoice.api.radio.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamvoice.api.radio.model.RadioChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

public interface VoiceRadioService {

  void init(final @NotNull VoicechatServerApi api);

  @NotNull Collection<RadioChannel> getChannels();

  @NotNull RadioChannel getOrCreateChannel(final @NotNull String name);

  @Nullable RadioChannel getChannel(final @NotNull String name);

  @Nullable RadioChannel getChannelOfPlayer(final @NotNull UUID playerUuid);

  void joinChannel(final @NotNull UUID playerUuid, final @NotNull String channelName);

  void leaveChannel(final @NotNull UUID playerUuid);

  void removeChannel(final @NotNull String name);

}
