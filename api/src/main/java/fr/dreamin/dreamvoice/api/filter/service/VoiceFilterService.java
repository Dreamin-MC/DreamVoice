package fr.dreamin.dreamvoice.api.filter.service;

import fr.dreamin.dreamvoice.api.filter.annotation.AutoVoiceFilter;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Service managing DSP voice filters, active player filter stacks, and filter chains.
 */
public interface VoiceFilterService {

  /**
   * Registers a custom DSP voice filter into the registry.
   *
   * @param filter the filter instance to register
   */
  void registerFilter(final @NotNull VoiceFilter filter);

  /**
   * Unregisters a DSP voice filter by its ID.
   *
   * @param filterId the identifier of the filter
   */
  void unregisterFilter(final @NotNull String filterId);

  /**
   * Retrieves a registered voice filter by its ID.
   *
   * @param filterId the identifier of the filter
   * @return the {@link VoiceFilter}, or {@code null} if not found
   */
  @Nullable VoiceFilter getFilter(final @NotNull String filterId);

  /**
   * Returns all currently registered voice filters.
   *
   * @return collection of available voice filters
   */
  @NotNull Collection<VoiceFilter> getAvailableFilters();

  /**
   * Applies an active filter to a player's outgoing voice stream.
   *
   * @param playerUuid the UUID of the player
   * @param filterId   the identifier of the filter
   */
  void addFilter(final @NotNull UUID playerUuid, final @NotNull String filterId);

  /**
   * Removes an active filter from a player.
   *
   * @param playerUuid the UUID of the player
   * @param filterId   the identifier of the filter
   */
  void removeFilter(final @NotNull UUID playerUuid, final @NotNull String filterId);

  /**
   * Clears all active filters from a player.
   *
   * @param playerUuid the UUID of the player
   */
  void clearFilters(final @NotNull UUID playerUuid);

  /**
   * Retrieves all active filters attached to a player, ordered by priority.
   *
   * @param playerUuid the UUID of the player
   * @return list of active {@link VoiceFilter}s
   */
  @NotNull List<VoiceFilter> getActiveFilters(final @NotNull UUID playerUuid);

  /**
   * Checks whether a player has any active filters (including environmental ones).
   *
   * @param playerUuid the UUID of the player
   * @return {@code true} if at least one filter is active
   */
  boolean hasActiveFilters(final @NotNull UUID playerUuid);

  /**
   * Checks whether a player has any manually configured filters (excluding auto-environmental filters).
   *
   * @param playerUuid the UUID of the player
   * @return {@code true} if at least one explicit filter is attached
   */
  boolean hasExplicitFilters(final @NotNull UUID playerUuid);

  /**
   * Checks whether auto-environmental filtering (underwater, caves, etc.) is enabled for a player.
   *
   * @param playerUuid the UUID of the player
   * @return {@code true} if auto-environment is enabled
   */
  boolean isAutoEnvironmentEnabled(final @NotNull UUID playerUuid);

  /**
   * Toggles auto-environmental filtering for a player.
   *
   * @param playerUuid the UUID of the player
   * @param enabled    whether auto-environment should be enabled
   */
  void setAutoEnvironmentEnabled(final @NotNull UUID playerUuid, final boolean enabled);

  /**
   * Automatically discovers, instantiates, and registers all {@link VoiceFilter} classes
   * annotated with {@link AutoVoiceFilter} from the given collection.
   *
   * @param classes collection of classes to scan
   * @return list of successfully instantiated and registered filters
   */
  @NotNull List<VoiceFilter> registerAnnotatedFilters(final @NotNull Collection<Class<?>> classes);

  /**
   * Reloads all filters from the configuration/file filter directory.
   */
  void reloadFilters();

  /**
   * Returns the directory where file-based filters (.yml, .json, .java) are located.
   *
   * @return directory file
   */
  @NotNull java.io.File getFilterDirectory();

  /**
   * Loads or compiles a voice filter from a file (.yml, .json, or .java).
   *
   * @param file the source file
   * @return the loaded voice filter, or null if loading failed
   */
  @Nullable VoiceFilter loadFilterFromFile(final @NotNull java.io.File file);

  /**
   * Exports an existing registered filter to disk in the specified format (yml, json, java).
   *
   * @param filterId the identifier of the filter to export
   * @param format   target format ("yml", "json", "java")
   * @return the exported File on disk, or null if export failed or filter was not found
   */
  @Nullable java.io.File exportFilter(final @NotNull String filterId, final @NotNull String format);

  /**
   * Passes raw PCM samples through the player's active filter chain and returns processed audio.
   *
   * @param playerUuid the UUID of the player
   * @param samples    raw 16-bit 48kHz audio samples
   * @return processed audio samples
   */
  short[] applyFilters(final @NotNull UUID playerUuid, final short @NotNull [] samples);

}
