package fr.dreamin.dreaminvoice.api.player.service;

import fr.dreamin.dreaminvoice.api.player.model.PlayerManager;
import fr.dreamin.dreaminvoice.api.player.model.PlayerState;
import fr.dreamin.dreaminvoice.api.player.model.VPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

public interface PlayerService {

  Collection<VPlayer> getPlayers();

  @Nullable VPlayer getPlayer(final @NotNull Player player);
  @Nullable VPlayer getPlayer(final @NotNull UUID uuid);

  void addPlayer(final @NotNull VPlayer vPlayer);
  void removePlayer(final @NotNull VPlayer vPlayer);

  void setState(final @NotNull PlayerState state, final @NotNull Player player);
  void setState(final @NotNull PlayerState state, final @NotNull UUID uuid);

  boolean isState(final @NotNull UUID uuid, final @NotNull PlayerState state);

  @NotNull PlayerManager createManagerInstance(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz);
  void addManager(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz);
  void addManager(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager>... classes);
  @javax.annotation.Nullable
  PlayerManager getManager(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz);
  void removeManager(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz);

}
