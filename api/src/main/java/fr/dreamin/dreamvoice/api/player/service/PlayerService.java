package fr.dreamin.dreamvoice.api.player.service;

import fr.dreamin.dreamvoice.api.player.model.PlayerManager;
import fr.dreamin.dreamvoice.api.player.model.PlayerState;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Service managing voice player wrappers ({@link VPlayer}), voice states, and attached managers.
 */
public interface PlayerService {

  /**
   * Returns all active wrapped voice players.
   *
   * @return collection of {@link VPlayer}s
   */
  Collection<VPlayer> getPlayers();

  /**
   * Retrieves a wrapped voice player from a Bukkit player.
   *
   * @param player the Bukkit player
   * @return the {@link VPlayer}, or {@code null} if not connected
   */
  @Nullable VPlayer getPlayer(final @NotNull Player player);

  /**
   * Retrieves a wrapped voice player by their UUID.
   *
   * @param uuid the player UUID
   * @return the {@link VPlayer}, or {@code null} if not connected
   */
  @Nullable VPlayer getPlayer(final @NotNull UUID uuid);

  /**
   * Registers a new wrapped voice player into the service.
   *
   * @param vPlayer the voice player wrapper
   */
  void addPlayer(final @NotNull VPlayer vPlayer);

  /**
   * Unregisters a wrapped voice player from the service.
   *
   * @param vPlayer the voice player wrapper
   */
  void removePlayer(final @NotNull VPlayer vPlayer);

  /**
   * Sets the voice state (ALIVE, DEAD, SPECTATE) of a player.
   *
   * @param state  the new {@link PlayerState}
   * @param player the Bukkit player
   */
  void setState(final @NotNull PlayerState state, final @NotNull Player player);

  /**
   * Sets the voice state of a player by their UUID.
   *
   * @param state the new {@link PlayerState}
   * @param uuid  the player UUID
   */
  void setState(final @NotNull PlayerState state, final @NotNull UUID uuid);

  /**
   * Checks whether a player matches a specific voice state.
   *
   * @param uuid  the player UUID
   * @param state the state to test
   * @return {@code true} if the player is in the given state
   */
  boolean isState(final @NotNull UUID uuid, final @NotNull PlayerState state);

  /**
   * Instantiates a new {@link PlayerManager} for a given player.
   *
   * @param vPlayer the voice player wrapper
   * @param clazz   the manager class
   * @return the created {@link PlayerManager} instance
   */
  @NotNull PlayerManager createManagerInstance(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz);

  /**
   * Attaches a manager to a player.
   *
   * @param vPlayer the voice player wrapper
   * @param clazz   the manager class
   */
  void addManager(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz);

  /**
   * Attaches multiple managers to a player.
   *
   * @param vPlayer the voice player wrapper
   * @param classes the manager classes
   */
  @SuppressWarnings("unchecked")
  void addManager(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager>... classes);

  /**
   * Retrieves an attached manager instance from a player.
   *
   * @param vPlayer the voice player wrapper
   * @param clazz   the manager class
   * @return the manager instance, or {@code null} if not attached
   */
  @Nullable PlayerManager getManager(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz);

  /**
   * Removes and cleans up an attached manager from a player.
   *
   * @param vPlayer the voice player wrapper
   * @param clazz   the manager class
   */
  void removeManager(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz);

}
