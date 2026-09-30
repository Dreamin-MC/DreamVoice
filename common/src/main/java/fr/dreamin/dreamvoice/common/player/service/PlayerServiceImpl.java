package fr.dreamin.dreamvoice.common.player.service;

import fr.dreamin.dreamvoice.api.player.model.PlayerManager;
import fr.dreamin.dreamvoice.api.player.model.PlayerState;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.player.manager.VoiceFilterManager;
import fr.dreamin.dreamvoice.common.player.manager.VoiceWallManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link PlayerService} managing voice player wrappers ({@link VPlayer}),
 * state transitions, and attached managers.
 */
public final class PlayerServiceImpl implements PlayerService {

  private final @NotNull VoicePlatform platform;
  private final @NotNull Map<UUID, VPlayer> players = new ConcurrentHashMap<>();

  public PlayerServiceImpl(final @NotNull VoicePlatform platform) {
    this.platform = platform;
  }

  @Override
  public Collection<VPlayer> getPlayers() {
    return this.players.values();
  }

  @Override
  public @NotNull VPlayer getPlayer(final @NotNull UUID uuid) {
    return this.players.computeIfAbsent(uuid, id -> {
      final var vPlayer = new VPlayer(id);
      addManager(vPlayer, VoiceFilterManager.class, VoiceWallManager.class);
      return vPlayer;
    });
  }

  @Override
  public void addPlayer(final @NotNull VPlayer vPlayer) {
    this.players.put(vPlayer.getUuid(), vPlayer);
    addManager(vPlayer, VoiceFilterManager.class, VoiceWallManager.class);
  }

  @Override
  public void removePlayer(final @NotNull VPlayer vPlayer) {
    vPlayer.removeAllManager();
    this.players.remove(vPlayer.getUuid());
  }

  public void removePlayer(final @NotNull UUID uuid) {
    final var vPlayer = this.players.remove(uuid);
    if (vPlayer != null)
      vPlayer.removeAllManager();
  }

  @Override
  public void setState(final @NotNull PlayerState state, final @NotNull UUID uuid) {
    final var vPlayer = getPlayer(uuid);
    vPlayer.setState(state);
  }

  @Override
  public boolean isState(final @NotNull UUID uuid, final @NotNull PlayerState state) {
    final var vPlayer = this.players.get(uuid);
    return vPlayer != null && vPlayer.getState() == state;
  }

  @Override
  public @NotNull PlayerManager createManagerInstance(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz) {
    try {
      final Constructor<?> constructor = clazz.getConstructor(VPlayer.class);
      final Object instance = constructor.newInstance(vPlayer);

      if (!(instance instanceof PlayerManager))
        throw new IllegalArgumentException("Class must extend PlayerManager");

      return (PlayerManager) instance;
    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
      this.platform.logError("Failed to instantiate PlayerManager " + clazz.getName(), e);
      throw new RuntimeException("Could not create manager instance for " + clazz.getName(), e);
    }
  }

  @Override
  public void addManager(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz) {
    if (vPlayer.hasManager(clazz))
      return;
    vPlayer.addManager(createManagerInstance(vPlayer, clazz));
  }

  @Override
  @SafeVarargs
  public final void addManager(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager>... classes) {
    for (final var clazz : classes)
      addManager(vPlayer, clazz);
  }

  @Override
  public @Nullable PlayerManager getManager(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz) {
    return vPlayer.getManager(clazz);
  }

  @Override
  public void removeManager(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz) {
    vPlayer.removeManager(clazz);
  }

}
