package fr.dreamin.dreamvoice.api.projection.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamvoice.api.projection.model.VoiceProjection;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

public interface VoiceProjectionService {

  void init(final @NotNull VoicechatServerApi api);

  VoicechatServerApi getAPI();

  @NotNull VoiceProjection createProjection(final @NotNull UUID playerUuid, final @NotNull Location anchorLocation);

  default @NotNull VoiceProjection createProjection(final @NotNull Player player, final @NotNull Location anchorLocation) {
    return createProjection(player.getUniqueId(), anchorLocation);
  }

  @NotNull VoiceProjection createProjection(final @NotNull UUID playerUuid, final @NotNull Entity anchorEntity);

  default @NotNull VoiceProjection createProjection(final @NotNull Player player, final @NotNull Entity anchorEntity) {
    return createProjection(player.getUniqueId(), anchorEntity);
  }


  void register(final @NotNull VoiceProjection projection);

  void removeProjection(final @NotNull UUID playerUuid);

  default void removeProjection(final @NotNull Player player) {
    removeProjection(player.getUniqueId());
  }

  default void removeProjection(final @NotNull VoiceProjection projection) {
    removeProjection(projection.getPlayerUuid());
  }

  @Nullable VoiceProjection getProjection(final @NotNull UUID playerUuid);

  default @Nullable VoiceProjection getProjection(final @NotNull Player player) {
    return getProjection(player.getUniqueId());
  }

  @Nullable VoiceProjection getProjectionById(final @NotNull UUID projectionId);

  boolean hasProjection(final @NotNull UUID playerUuid);

  default boolean hasProjection(final @NotNull Player player) {
    return hasProjection(player.getUniqueId());
  }

  @NotNull Collection<VoiceProjection> getProjections();

  void clearProjections();

  void updateLocation(final @NotNull UUID playerUuid, final @NotNull Location newLocation);

  void save();

  void load();

}
