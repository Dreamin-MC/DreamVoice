package fr.dreamin.dreamvoice.api.wiretap.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

public interface VoiceWiretapService {

  void init(final @NotNull VoicechatServerApi api);

  VoicechatServerApi getAPI();

  @NotNull VoiceWiretap createWiretap(final @NotNull String name, final @NotNull Location location);

  @NotNull VoiceWiretap createWiretap(final @NotNull String name, final @NotNull Entity entity);

  void attachToEntity(final @NotNull String name, final @NotNull Entity entity);

  void detachFromEntity(final @NotNull String name);


  void register(final @NotNull VoiceWiretap wiretap);

  void removeWiretap(final @NotNull String name);

  void removeWiretap(final @NotNull UUID uuid);

  @Nullable VoiceWiretap getWiretap(final @NotNull String name);

  @Nullable VoiceWiretap getWiretap(final @NotNull UUID uuid);

  @NotNull Collection<VoiceWiretap> getWiretaps();

  void addListener(final @NotNull String name, final @NotNull UUID playerUuid);

  default void addListener(final @NotNull String name, final @NotNull Player player) {
    addListener(name, player.getUniqueId());
  }

  void removeListener(final @NotNull String name, final @NotNull UUID playerUuid);

  default void removeListener(final @NotNull String name, final @NotNull Player player) {
    removeListener(name, player.getUniqueId());
  }

  void removeListenerFromAll(final @NotNull UUID playerUuid);

  @Nullable VoiceRecording startRecording(final @NotNull String name);

  @Nullable VoiceRecording stopRecording(final @NotNull String name);

}
