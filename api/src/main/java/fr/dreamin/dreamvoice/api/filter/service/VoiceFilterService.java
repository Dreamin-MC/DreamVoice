package fr.dreamin.dreamvoice.api.filter.service;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VoiceFilterService {

  void registerFilter(final @NotNull VoiceFilter filter);

  void unregisterFilter(final @NotNull String filterId);

  @Nullable VoiceFilter getFilter(final @NotNull String filterId);

  @NotNull Collection<VoiceFilter> getAvailableFilters();

  void addFilter(final @NotNull UUID playerUuid, final @NotNull String filterId);

  void removeFilter(final @NotNull UUID playerUuid, final @NotNull String filterId);

  void clearFilters(final @NotNull UUID playerUuid);

  @NotNull List<VoiceFilter> getActiveFilters(final @NotNull UUID playerUuid);

  boolean hasActiveFilters(final @NotNull UUID playerUuid);

  boolean isAutoEnvironmentEnabled(final @NotNull UUID playerUuid);

  void setAutoEnvironmentEnabled(final @NotNull UUID playerUuid, final boolean enabled);

  short[] applyFilters(final @NotNull UUID playerUuid, final @NotNull short[] samples);

}
