package fr.dreamin.dreamvoice.api.player.model;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract lifecycle controller attached to a {@link VPlayer}.
 */
@RequiredArgsConstructor
public abstract class PlayerManager {

  protected final @NotNull VPlayer vPlayer;

  /**
   * Called when the manager is attached to the player.
   */
  public abstract void init();

  /**
   * Called when the manager is detached or the player disconnects.
   */
  public abstract void close();

}
