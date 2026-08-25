package fr.dreamin.dreamvoice.core.player.service;

import fr.dreamin.dreamapi.api.annotations.Inject;
import fr.dreamin.dreamapi.api.logger.DreamLogger;
import fr.dreamin.dreamapi.api.services.DreamService;
import fr.dreamin.dreamvoice.api.player.model.PlayerManager;
import fr.dreamin.dreamvoice.api.player.model.PlayerState;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.player.manager.VoiceFilterManager;
import fr.dreamin.dreamvoice.core.player.manager.VoiceWallManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerServiceImpl implements PlayerService, Listener {

  private final @NotNull DreamVoice plugin;

  private final @NotNull Map<UUID, VPlayer> players = new HashMap<>();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public PlayerServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;
    Bukkit.getPluginManager().registerEvents(this, this.plugin);
  }

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public Collection<VPlayer> getPlayers() {
    return this.players.values();
  }

  @Override
  public @Nullable VPlayer getPlayer(final @NotNull Player player) {
    return this.players.values().stream()
      .filter(v -> v.getUuid().equals(player.getUniqueId()))
      .findFirst()
      .orElse(null);
  }

  @Override
  public @Nullable VPlayer getPlayer(final @NotNull UUID uuid) {
    return this.players.values().stream()
      .filter(v -> v.getUuid().equals(uuid))
      .findFirst()
      .orElse(null);
  }

  @Override
  public void addPlayer(final @NotNull VPlayer vPlayer) {
    addManager(vPlayer, VoiceWallManager.class, VoiceFilterManager.class);
    this.players.putIfAbsent(vPlayer.getUuid(), vPlayer);
  }


  @Override
  public void removePlayer(final @NotNull VPlayer vPlayer) {
    vPlayer.removeAllManager();
    this.players.remove(vPlayer.getUuid());
  }

  @Override
  public void setState(final @NotNull PlayerState state, final @NotNull Player player) {
    final var vPlayer = getPlayer(player);
    if (vPlayer == null)
      return;

    vPlayer.setState(state);
  }

  @Override
  public void setState(final @NotNull PlayerState state, final @NotNull UUID uuid) {
    final var vPlayer = getPlayer(uuid);
    if (vPlayer == null)
      return;

    vPlayer.setState(state);
  }

  @Override
  public boolean isState(final @NotNull UUID uuid, final @NotNull PlayerState state) {
    final var player = getPlayer(uuid);
    if (player == null)
      return false;

    return player.getState() == state;
  }

  @Override
  public @NotNull PlayerManager createManagerInstance(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz) {
    try {
      final var ctor = resolveConstructor(clazz);
      final var args = resolveConstructorArgs(ctor, vPlayer);

      final var instance = ctor.newInstance(args);

      if (instance instanceof Listener listener)
        Bukkit.getPluginManager().registerEvents(listener, DreamVoice.getInstance());

      return (PlayerManager) instance;
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void addManager(final @NotNull VPlayer vPlayer, final @NotNull Class<? extends PlayerManager> clazz) {
    final var manager = createManagerInstance(vPlayer, clazz);
    vPlayer.addManager(manager);
  }

  @Override
  public void addManager(final @NotNull VPlayer vPlayer, final @NonNull @NotNull Class<? extends PlayerManager>... classes) {
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

  // ###############################################################
  // ----------------------- PRIVATE METHODS -----------------------
  // ###############################################################

  private Constructor<?> resolveConstructor(final @NotNull Class<?> clazz) {
    final var constructors = clazz.getConstructors();

    for (final var c : constructors) {
      if (c.isAnnotationPresent(Inject.class)) {
        c.setAccessible(true);
        return c;
      }
    }

    if (clazz.isAnnotationPresent(Inject.class)) {
      var best = constructors[0];
      for (final var c : constructors) {
        if (c.getParameterCount() > best.getParameterCount())
          best = c;
      }

      best.setAccessible(true);
      return best;
    }

    for (final var c : constructors) {
      var compatible = true;

      for (final var param : c.getParameterTypes()) {
        if (!Plugin.class.isAssignableFrom(param)
          && param != DreamLogger.class
          && !DreamService.class.isAssignableFrom(param)) {
          compatible = false;
          break;
        }
      }

      if (compatible) {
        c.setAccessible(true);
        return c;
      }
    }

    // 4) Fallback: no-arg constructor
    try {
      final var c = clazz.getDeclaredConstructor();
      c.setAccessible(true);
      return c;
    } catch (Exception e) {
      throw new RuntimeException("[DreamService] No suitable constructor found for " + clazz.getName());
    }
  }

  private Object[] resolveConstructorArgs(final @NotNull Constructor<?> constructor, final @NotNull VPlayer vPlayer) {
    final var params = constructor.getParameterTypes();
    final var args = new Object[params.length];

    for (int i = 0; i < params.length; i++) {
      final var param = params[i];

      // Inject Plugin
      if (Plugin.class.isAssignableFrom(param)) {
        args[i] = this.plugin;
        continue;
      }

      if (VPlayer.class.isAssignableFrom(param)) {
        args[i] = vPlayer;
        continue;
      }

      if (param == DreamLogger.class) {
        args[i] = null;
        continue;
      }

      // Inject DreamService
      var resolved = false;
      for (final var service : DreamVoice.getInstance().getServiceManager().getAllLoadedServices().values()) {
        if (param.isAssignableFrom(service.getClass())) {
          args[i] = service;
          resolved = true;
          break;
        }
      }

      if (!resolved) {
        throw new RuntimeException(
          String.format("[DreamService] Unable to resolve dependency: %s for constructor %s", param.getName(), constructor)
        );
      }
    }

    return args;
  }

  // ###############################################################
  // ---------------------- LISTENER METHODS -----------------------
  // ###############################################################

  @EventHandler(ignoreCancelled = true)
  private void onPlayerJoin(final @NotNull PlayerJoinEvent event) {
    final var player = event.getPlayer();

    Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
      if (!player.isOnline())
        return;

      final var service = DreamVoice.getService(VoiceService.class);
      final var client = service.getAPI().getConnectionOf(player.getUniqueId());

      final var vPlayer = new VPlayer(player, client);

      addPlayer(vPlayer);
    }, 10);
  }

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    final var vPlayer = getPlayer(event.getPlayer());
    if (vPlayer == null)
      return;

    removePlayer(vPlayer);
  }

}


