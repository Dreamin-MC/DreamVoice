package fr.dreamin.dreaminvoice.api.player.model;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import fr.dreamin.dreaminvoice.api.player.event.PlayerStateChangeEvent;
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

import static fr.dreamin.dreaminvoice.api.player.model.PlayerState.ALIVE;

@Getter @Setter
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

  public void setState(final @NotNull PlayerState state) {
    final var event = new PlayerStateChangeEvent(this, this.state, state);
    event.callEvent();

    if (!event.isCancelled())
      this.state = event.getNewState();
  }

  /**
   * Consumes the Player object if it is online.
   *
   * @param consumer the consumer to accept the Player object
   */
  public void consumePlayer(final @NotNull Consumer<Player> consumer) {
    final var player = getBukkitPlayer();
    if (player != null)
      consumer.accept(player);
  }

  /**
   * Retrieves the Bukkit Player object associated with this GamePlayer instance. If the player is
   * currently online, it directly returns the Player object. If the player has a DisconnectManager
   * and it has a valid NPC entity that is a Player, the NPC Player is returned. Otherwise, null
   * is returned.
   *
   * @return the Player object if online, an NPC Player if applicable, or null if no valid
   *         Player is available.
   */
  public @Nullable Player getBukkitPlayer() {
    return Bukkit.getPlayer(this.uuid);
  }

  /**
   * Determines if the player associated with this GamePlayer instance is currently online.
   *
   * @return true if the player is online, false otherwise.
   */
  public boolean isOnline() {
    return getBukkitPlayer() != null && getBukkitPlayer().isOnline();
  }

  // ###############################################################
  // ----------------------- MANAGER METHODS -----------------------
  // ###############################################################

  public @Nullable <T extends PlayerManager> T getManager(Class<T> managerClass) {
    return (T) this.managers.get(managerClass);
  }

  public <T extends PlayerManager> void consumeManager(
    final @NotNull Class<T> managerClass,
    final @NotNull Consumer<T> consumer
  ) {
    final var manager = getManager(managerClass);
    if (manager != null) consumer.accept(manager);
  }

  public <T extends PlayerManager> void addManager(final @NotNull PlayerManager manager) {
    manager.init();

    this.managers.put(manager.getClass(), manager);
  }

  public boolean hasManager(Class<? extends PlayerManager> managerClass) {
    return this.managers.containsKey(managerClass);
  }

  public void removeManager(Class<? extends PlayerManager> managerClass) {
    if (!hasManager(managerClass)) return;
    final var manager = this.managers.remove(managerClass);

    manager.close();

    if (Listener.class.isAssignableFrom(managerClass))
      HandlerList.unregisterAll((Listener) manager);
  }

  public void removeAllManager() {
    final var copy = new HashMap<>(this.managers);
    copy.keySet().forEach(this::removeManager);
  }

  @SafeVarargs
  public final void removeAllManager(final @NotNull Class<? extends PlayerManager>... excludeClass) {
    final var excludeSet = Set.of(excludeClass);
    final var copy = new HashMap<>(this.managers);
    copy.keySet().forEach(managerClass -> {
      if (excludeSet.contains(managerClass)) return;
      this.removeManager(managerClass);
    });
  }

}
