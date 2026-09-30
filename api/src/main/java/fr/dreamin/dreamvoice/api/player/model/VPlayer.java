package fr.dreamin.dreamvoice.api.player.model;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import fr.dreamin.dreamvoice.api.player.event.PlayerStateChangeEvent;
import lombok.Getter;
import lombok.Setter;
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
  private final @Nullable VoicechatConnection client;
  private @NotNull PlayerState state;

  private final @NotNull Map<Class<? extends PlayerManager>, PlayerManager> managers = new HashMap<>();

  private boolean forceMute = false;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VPlayer(final @NotNull UUID uuid, final @Nullable VoicechatConnection client) {
    this.uuid = uuid;
    this.client = client;
    this.state = ALIVE;
  }

  public VPlayer(final @NotNull UUID uuid) {
    this(uuid, null);
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
    if (!event.callEvent())
      return;

    this.state = event.getNewState();
  }

  // ###############################################################
  // ----------------------- MANAGER METHODS -----------------------
  // ###############################################################

  @SuppressWarnings("unchecked")
  public @Nullable <T extends PlayerManager> T getManager(final @NotNull Class<T> managerClass) {
    return (T) this.managers.get(managerClass);
  }

  public <T extends PlayerManager> void consumeManager(
    final @NotNull Class<T> managerClass,
    final @NotNull Consumer<T> consumer
  ) {
    final var manager = getManager(managerClass);
    if (manager != null)
      consumer.accept(manager);
  }

  public void addManager(final @NotNull PlayerManager manager) {
    manager.init();
    this.managers.put(manager.getClass(), manager);
  }

  public boolean hasManager(final @NotNull Class<? extends PlayerManager> managerClass) {
    return this.managers.containsKey(managerClass);
  }

  public void removeManager(final @NotNull Class<? extends PlayerManager> managerClass) {
    if (!hasManager(managerClass))
      return;
    final var manager = this.managers.remove(managerClass);
    if (manager == null)
      return;

    manager.close();
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
      if (excludeSet.contains(managerClass))
        return;
      this.removeManager(managerClass);
    });
  }

}
