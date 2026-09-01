package fr.dreamin.dreamvoice.api.player.model;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import fr.dreamin.dreamvoice.api.player.event.PlayerStateChangeEvent;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static fr.dreamin.dreamvoice.api.player.model.PlayerState.ALIVE;

/**
 * Wrapper object representing an active player connected to Simple Voice Chat.
 * Manages player state, attached managers, and mute toggles.
 */
@Getter
@Setter
public final class VPlayer {

  private final @NotNull UUID uuid;
  private final VoicechatConnection client;
  private @NotNull PlayerState state;

  private final @NotNull Map<Class<? extends PlayerManager>, PlayerManager> managers = new HashMap<>();

  private boolean forceMute = false;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VPlayer(final @NotNull Player player, final VoicechatConnection client) {
    this.uuid = player.getUniqueId();
    this.client = client;
    this.state = ALIVE;
  }

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  /**
   * Updates the voice state of the player, firing a {@link PlayerStateChangeEvent}.
   *
   * @param state the new player state
   */
  public void setState(final @NotNull PlayerState state) {
    final var event = new PlayerStateChangeEvent(this, this.state, state);
    event.callEvent();

    if (!event.isCancelled())
      this.state = event.getNewState();
  }

  /**
   * Safely executes an action on the online Bukkit Player object.
   *
   * @param consumer the action to execute
   */
  public void consumePlayer(final @NotNull Consumer<Player> consumer) {
    final var player = getBukkitPlayer();
    if (player != null)
      consumer.accept(player);
  }

  /**
   * Retrieves the Bukkit Player object associated with this wrapper.
   *
   * @return the online {@link Player}, or {@code null} if offline
   */
  public @Nullable Player getBukkitPlayer() {
    return Bukkit.getPlayer(this.uuid);
  }

  /**
   * Checks whether the underlying Bukkit player is currently online.
   *
   * @return {@code true} if the player is online
   */
  public boolean isOnline() {
    final var p = getBukkitPlayer();
    return p != null && p.isOnline();
  }

  // ###############################################################
  // ----------------------- MANAGER METHODS -----------------------
  // ###############################################################

  /**
   * Retrieves a manager of the specified class attached to this player.
   *
   * @param managerClass the manager class
   * @param <T>          the manager type
   * @return the manager instance, or {@code null} if not present
   */
  @SuppressWarnings("unchecked")
  public @Nullable <T extends PlayerManager> T getManager(final @NotNull Class<T> managerClass) {
    return (T) this.managers.get(managerClass);
  }

  /**
   * Safely consumes a manager if present.
   *
   * @param managerClass the manager class
   * @param consumer     the action to perform
   * @param <T>          the manager type
   */
  public <T extends PlayerManager> void consumeManager(
    final @NotNull Class<T> managerClass,
    final @NotNull Consumer<T> consumer
  ) {
    final var manager = getManager(managerClass);
    if (manager != null)
      consumer.accept(manager);
  }

  /**
   * Attaches and initializes a manager instance.
   *
   * @param manager the manager instance
   */
  public void addManager(final @NotNull PlayerManager manager) {
    manager.init();
    this.managers.put(manager.getClass(), manager);
  }

  /**
   * Checks whether this player has a manager of the given class attached.
   *
   * @param managerClass the manager class
   * @return {@code true} if present
   */
  public boolean hasManager(final @NotNull Class<? extends PlayerManager> managerClass) {
    return this.managers.containsKey(managerClass);
  }

  /**
   * Removes and closes a manager from this player.
   *
   * @param managerClass the manager class to remove
   */
  public void removeManager(final @NotNull Class<? extends PlayerManager> managerClass) {
    if (!hasManager(managerClass))
      return;
    final var manager = this.managers.remove(managerClass);
    if (manager == null)
      return;

    manager.close();

    if (manager instanceof Listener listener)
      HandlerList.unregisterAll(listener);
  }

  /**
   * Cleans up and removes all managers attached to this player.
   */
  public void removeAllManager() {
    final var copy = new HashMap<>(this.managers);
    copy.keySet().forEach(this::removeManager);
  }

  /**
   * Removes all managers attached to this player except specified classes.
   *
   * @param excludeClass classes to retain
   */
  @SafeVarargs
  public final void removeAllManager(final @NotNull Class<? extends PlayerManager>... excludeClass) {
    final var excludeSet = Set.of(excludeClass);
    final var copy = new HashMap<>(this.managers);
    copy.keySet().forEach(managerClass -> {
      if (excludeSet.contains(managerClass))
        return;
      this.removeManager(managerClass);
    });
  }

}
