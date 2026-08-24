package fr.dreamin.dreaminvoice.api.player.model;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public abstract class PlayerManager {
  protected final @NotNull VPlayer vPlayer;
  public abstract void init();
  public abstract void close();
}
