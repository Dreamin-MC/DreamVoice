package fr.dreamin.dreamvoice.core.filter.service;

import fr.dreamin.dreamvoice.api.filter.annotation.AutoVoiceFilter;
import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.filter.event.VoiceFilterApplyEvent;
import fr.dreamin.dreamvoice.api.filter.event.VoiceFilterRemoveEvent;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.filter.exporter.VoiceFilterExporter;
import fr.dreamin.dreamvoice.core.filter.loader.FileFilterLoader;
import fr.dreamin.dreamvoice.core.player.manager.VoiceFilterManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link VoiceFilterService} managing DSP audio filter registration,
 * active per-player filter chains, and automatic environmental effects (underwater, caves).
 */
public final class VoiceFilterServiceImpl implements VoiceFilterService, Listener {

  // ###############################################################
  // --------------------- INSTANCE FIELDS -------------------------
  // ###############################################################

  private final @NotNull DreamVoice plugin;
  private final @NotNull PlayerService playerService;
  private final @NotNull File filterDirectory;
  private final @NotNull FileFilterLoader fileFilterLoader;
  private final @NotNull VoiceFilterExporter filterExporter;

  private final Map<String, VoiceFilter> registeredFilters = new ConcurrentHashMap<>();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceFilterServiceImpl(final @NotNull DreamVoice plugin, final @NotNull PlayerService playerService) {
    this.plugin = plugin;
    this.playerService = playerService;
    this.filterDirectory = new File(plugin.getDataFolder(), "modules/filter/filters");
    this.fileFilterLoader = new FileFilterLoader(plugin, this.filterDirectory);
    this.filterExporter = new VoiceFilterExporter(plugin.getLogger(), this.filterDirectory);

    this.fileFilterLoader.extractDefaults();
    reloadFilters();

    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  // ###############################################################
  // ------------------- PUBLIC SERVICE METHODS --------------------
  // ###############################################################

  @Override
  public void registerFilter(final @NotNull VoiceFilter filter) {
    this.registeredFilters.put(filter.getId().toLowerCase(), filter);
  }

  @Override
  public void unregisterFilter(final @NotNull String filterId) {
    this.registeredFilters.remove(filterId.toLowerCase());
  }

  @Override
  public @Nullable VoiceFilter getFilter(final @NotNull String filterId) {
    return this.registeredFilters.get(filterId.toLowerCase());
  }

  @Override
  public @NotNull Collection<VoiceFilter> getAvailableFilters() {
    return Collections.unmodifiableCollection(this.registeredFilters.values());
  }

  @Override
  public void addFilter(final @NotNull UUID playerUuid, final @NotNull String filterId) {
    final var vPlayer = this.playerService.getPlayer(playerUuid);
    if (vPlayer == null)
      return;

    final var filter = getFilter(filterId);
    if (filter != null) {
      final var event = new VoiceFilterApplyEvent(playerUuid, filter);
      if (!event.callEvent())
        return;
    }

    vPlayer.consumeManager(VoiceFilterManager.class, m -> m.addFilter(filterId));
  }

  @Override
  public void removeFilter(final @NotNull UUID playerUuid, final @NotNull String filterId) {
    final var vPlayer = this.playerService.getPlayer(playerUuid);
    if (vPlayer == null)
      return;

    vPlayer.consumeManager(VoiceFilterManager.class, m -> m.removeFilter(filterId));
    final var filter = getFilter(filterId);
    if (filter != null)
      new VoiceFilterRemoveEvent(playerUuid, filter).callEvent();
  }

  @Override
  public void clearFilters(final @NotNull UUID playerUuid) {
    final var vPlayer = this.playerService.getPlayer(playerUuid);
    if (vPlayer == null)
      return;

    vPlayer.consumeManager(VoiceFilterManager.class, VoiceFilterManager::clearFilters);
    this.registeredFilters.values().forEach(f -> f.resetState(playerUuid));
  }

  @Override
  public @NotNull List<VoiceFilter> getActiveFilters(final @NotNull UUID playerUuid) {
    final var vPlayer = this.playerService.getPlayer(playerUuid);
    if (vPlayer == null)
      return Collections.emptyList();

    final var manager = vPlayer.getManager(VoiceFilterManager.class);
    if (manager == null)
      return Collections.emptyList();

    final var filters = new ArrayList<VoiceFilter>();

    for (final var filterId : manager.getActiveFilterIds()) {
      final var filter = this.registeredFilters.get(filterId);
      if (filter != null)
        filters.add(filter);
    }

    if (manager.isAutoEnvironment())
      resolveEnvironmentalFilters(vPlayer, manager, filters);

    filters.sort(Comparator.comparingInt(VoiceFilter::getPriority));
    return filters;
  }

  @Override
  public boolean hasActiveFilters(final @NotNull UUID playerUuid) {
    return !getActiveFilters(playerUuid).isEmpty();
  }

  @Override
  public boolean hasExplicitFilters(final @NotNull UUID playerUuid) {
    final var vPlayer = this.playerService.getPlayer(playerUuid);
    if (vPlayer == null)
      return false;

    final var manager = vPlayer.getManager(VoiceFilterManager.class);
    return manager != null && !manager.getActiveFilterIds().isEmpty();
  }

  @Override
  public boolean isAutoEnvironmentEnabled(final @NotNull UUID playerUuid) {
    final var vPlayer = this.playerService.getPlayer(playerUuid);
    if (vPlayer == null)
      return false;

    final var manager = vPlayer.getManager(VoiceFilterManager.class);
    return manager != null && manager.isAutoEnvironment();
  }

  @Override
  public void setAutoEnvironmentEnabled(final @NotNull UUID playerUuid, final boolean enabled) {
    final var vPlayer = this.playerService.getPlayer(playerUuid);
    if (vPlayer == null)
      return;

    vPlayer.consumeManager(VoiceFilterManager.class, m -> m.setAutoEnvironment(enabled));
  }

  @Override
  public @NotNull List<VoiceFilter> registerAnnotatedFilters(final @NotNull Collection<Class<?>> classes) {
    final var registered = new ArrayList<VoiceFilter>();

    for (final var clazz : classes) {
      if (!VoiceFilter.class.isAssignableFrom(clazz))
        continue;

      final var annotation = clazz.getAnnotation(AutoVoiceFilter.class);
      if (annotation == null || !annotation.enabled())
        continue;

      try {
        final Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        final var filter = (VoiceFilter) constructor.newInstance();
        registerFilter(filter);
        registered.add(filter);
        this.plugin.getLogger().info("[VoiceFilter] Auto-registered filter: " + filter.getId() + " (" + filter.getName() + ") from class " + clazz.getSimpleName());
      } catch (final NoSuchMethodException e) {
        this.plugin.getLogger().warning("[VoiceFilter] Class " + clazz.getName() + " has @AutoVoiceFilter but lacks a no-args constructor.");
      } catch (final Exception e) {
        this.plugin.getLogger().severe("[VoiceFilter] Failed to instantiate filter " + clazz.getName() + ": " + e.getMessage());
      }
    }

    return registered;
  }

  @Override
  public void reloadFilters() {
    final var loaded = this.fileFilterLoader.loadAll();
    for (final var filter : loaded)
      registerFilter(filter);

    this.plugin.getLogger().info("[VoiceFilter] Loaded " + loaded.size() + " custom filters from " + this.filterDirectory.getName());
  }

  @Override
  public @NotNull File getFilterDirectory() {
    return this.filterDirectory;
  }

  @Override
  public @Nullable VoiceFilter loadFilterFromFile(final @NotNull File file) {
    final var filter = this.fileFilterLoader.loadFromFile(file);
    if (filter != null)
      registerFilter(filter);
    return filter;
  }

  @Override
  public @Nullable File exportFilter(final @NotNull String filterId, final @NotNull String format) {
    final var filter = getFilter(filterId);
    if (filter == null) {
      this.plugin.getLogger().warning("[VoiceFilter] Cannot export filter '" + filterId + "': not found.");
      return null;
    }

    return this.filterExporter.exportFilter(filter, format, this.plugin.getClass().getClassLoader());
  }

  @Override
  public short[] applyFilters(final @NotNull UUID playerUuid, final short @NonNull [] samples) {
    final var vPlayer = this.playerService.getPlayer(playerUuid);
    final var activeFilters = getActiveFilters(playerUuid);

    if (activeFilters.isEmpty())
      return samples;

    var result = samples;
    for (final var filter : activeFilters)
      result = filter.process(result, vPlayer);

    return result;
  }

  // ###############################################################
  // ------------------- PRIVATE HELPER METHODS --------------------
  // ###############################################################

  private void resolveEnvironmentalFilters(
    final @NotNull VPlayer vPlayer,
    final @NotNull VoiceFilterManager manager,
    final @NotNull List<VoiceFilter> filters
  ) {
    final var bukkitPlayer = vPlayer.getBukkitPlayer();
    if (bukkitPlayer == null || !bukkitPlayer.isOnline())
      return;

    final var loc = bukkitPlayer.getEyeLocation();
    final var block = loc.getBlock();

    if (block.isLiquid() && !manager.hasFilter("underwater")) {
      final var underwater = this.registeredFilters.get("underwater");
      if (underwater != null)
        filters.add(underwater);
    } else if (loc.getY() < 55 && block.getLightFromSky() == 0 && !manager.hasFilter("cave")) {
      final var cave = this.registeredFilters.get("cave");
      if (cave != null)
        filters.add(cave);
    }
  }

  // ###############################################################
  // ---------------------- EVENT LISTENERS ------------------------
  // ###############################################################

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    final var uuid = event.getPlayer().getUniqueId();
    this.registeredFilters.values().forEach(f -> f.resetState(uuid));
  }

}
