package fr.dreamin.dreamvoice.core.filter.service;

import fr.dreamin.dreamvoice.api.filter.model.VoiceFilter;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.filter.impl.AlienVoiceFilter;
import fr.dreamin.dreamvoice.core.filter.impl.CaveVoiceFilter;
import fr.dreamin.dreamvoice.core.filter.impl.DeepVoiceFilter;
import fr.dreamin.dreamvoice.core.filter.impl.DisguiseVoiceFilter;
import fr.dreamin.dreamvoice.core.filter.impl.GasmaskVoiceFilter;
import fr.dreamin.dreamvoice.core.filter.impl.GhostVoiceFilter;
import fr.dreamin.dreamvoice.core.filter.impl.HeliumVoiceFilter;
import fr.dreamin.dreamvoice.core.filter.impl.MegaphoneVoiceFilter;
import fr.dreamin.dreamvoice.core.filter.impl.MuffledVoiceFilter;
import fr.dreamin.dreamvoice.core.filter.impl.RadioVoiceFilter;
import fr.dreamin.dreamvoice.core.filter.impl.RobotVoiceFilter;
import fr.dreamin.dreamvoice.core.filter.impl.TelephoneVoiceFilter;
import fr.dreamin.dreamvoice.core.filter.impl.UnderwaterVoiceFilter;
import fr.dreamin.dreamvoice.core.player.manager.VoiceFilterManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

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

  private final @NotNull PlayerService playerService;

  private final Map<String, VoiceFilter> registeredFilters = new ConcurrentHashMap<>();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceFilterServiceImpl(final @NotNull DreamVoice plugin, final @NotNull PlayerService playerService) {
    this.playerService = playerService;

    registerDefaults();
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

    vPlayer.consumeManager(VoiceFilterManager.class, m -> m.addFilter(filterId));
  }

  @Override
  public void removeFilter(final @NotNull UUID playerUuid, final @NotNull String filterId) {
    final var vPlayer = this.playerService.getPlayer(playerUuid);
    if (vPlayer == null)
      return;

    vPlayer.consumeManager(VoiceFilterManager.class, m -> m.removeFilter(filterId));
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
    final @NotNull fr.dreamin.dreamvoice.api.player.model.VPlayer vPlayer,
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

  private void registerDefaults() {
    registerFilter(new UnderwaterVoiceFilter());
    registerFilter(new CaveVoiceFilter());
    registerFilter(new RadioVoiceFilter());
    registerFilter(new MuffledVoiceFilter());
    registerFilter(new RobotVoiceFilter());
    registerFilter(new HeliumVoiceFilter());
    registerFilter(new DeepVoiceFilter());
    registerFilter(new MegaphoneVoiceFilter());
    registerFilter(new GhostVoiceFilter());
    registerFilter(new GasmaskVoiceFilter());
    registerFilter(new TelephoneVoiceFilter());
    registerFilter(new AlienVoiceFilter());
    registerFilter(new DisguiseVoiceFilter());
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
