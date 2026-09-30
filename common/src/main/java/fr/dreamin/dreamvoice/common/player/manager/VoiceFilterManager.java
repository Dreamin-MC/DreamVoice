package fr.dreamin.dreamvoice.common.player.manager;

import fr.dreamin.dreamvoice.api.player.model.PlayerManager;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public final class VoiceFilterManager extends PlayerManager {

  private final @NotNull Set<String> activeFilterIds = ConcurrentHashMap.newKeySet();
  private boolean autoEnvironment = false;

  public VoiceFilterManager(final @NotNull VPlayer gamePlayer) {
    super(gamePlayer);
  }

  @Override
  public void init() {
  }

  @Override
  public void close() {
    this.activeFilterIds.clear();
  }

  public void addFilter(final @NotNull String filterId) {
    this.activeFilterIds.add(filterId.toLowerCase());
  }

  public void removeFilter(final @NotNull String filterId) {
    this.activeFilterIds.remove(filterId.toLowerCase());
  }

  public void clearFilters() {
    this.activeFilterIds.clear();
  }

  public boolean hasFilter(final @NotNull String filterId) {
    return this.activeFilterIds.contains(filterId.toLowerCase());
  }

  public @NotNull Set<String> getActiveFilterIds() {
    return Collections.unmodifiableSet(this.activeFilterIds);
  }

}
