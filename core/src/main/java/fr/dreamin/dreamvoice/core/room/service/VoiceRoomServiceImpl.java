package fr.dreamin.dreamvoice.core.room.service;

import fr.dreamin.dreamapi.api.config.Configurations;
import fr.dreamin.dreamapi.api.cuboid.Cuboid;
import fr.dreamin.dreamapi.core.cuboid.event.CuboidPlayerEnterEvent;
import fr.dreamin.dreamapi.core.cuboid.event.CuboidPlayerLeaveEvent;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.room.model.AcousticRoom;
import fr.dreamin.dreamvoice.api.room.model.RoomPreset;
import fr.dreamin.dreamvoice.api.room.service.VoiceRoomService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.room.config.VoiceRoomConfig;
import fr.dreamin.dreamvoice.core.room.data.VoiceRoomPersistence;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link VoiceRoomService} managing acoustic rooms, presets,
 * soundproofing boundary checks, and automated persistence.
 */
public final class VoiceRoomServiceImpl implements VoiceRoomService, Listener {

  private final @NotNull DreamVoice plugin;
  private final @NotNull File moduleDir;
  private final @NotNull File configFile;

  private @NotNull VoiceRoomConfig config = new VoiceRoomConfig();
  private final Map<String, RoomPreset> presets = new ConcurrentHashMap<>();
  private final Map<String, AcousticRoom> rooms = new ConcurrentHashMap<>();
  private final Map<UUID, String> playerCurrentRoom = new ConcurrentHashMap<>();

  public VoiceRoomServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;
    this.moduleDir = new File(plugin.getDataFolder(), "modules/room");
    this.configFile = new File(this.moduleDir, "config.json");

    if (!this.moduleDir.exists())
      this.moduleDir.mkdirs();

    reload();
    VoiceRoomPersistence.load(this, this.moduleDir);

    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  @Override
  public void registerRoom(final @NotNull AcousticRoom room) {
    this.rooms.put(room.getId().toLowerCase(), room);
  }

  @Override
  public void unregisterRoom(final @NotNull String roomId) {
    final var removed = this.rooms.remove(roomId.toLowerCase());
    if (removed != null) {
      this.playerCurrentRoom.entrySet().removeIf(e -> e.getValue().equalsIgnoreCase(roomId));
    }
  }

  @Override
  public @NotNull Optional<AcousticRoom> getRoom(final @NotNull String roomId) {
    return Optional.ofNullable(this.rooms.get(roomId.toLowerCase()));
  }

  @Override
  public @NotNull Collection<AcousticRoom> getRooms() {
    return Collections.unmodifiableCollection(this.rooms.values());
  }

  @Override
  public @NotNull Optional<AcousticRoom> getRoomAt(final @NotNull Location location) {
    for (final var room : this.rooms.values()) {
      for (final var cuboid : room.getCuboids()) {
        if (cuboid.isLocationIn(location))
          return Optional.of(room);
      }
    }
    return Optional.empty();
  }

  @Override
  public @NotNull Optional<AcousticRoom> getPlayerRoom(final @NotNull UUID playerUuid) {
    final var roomId = this.playerCurrentRoom.get(playerUuid);
    if (roomId != null) {
      final var room = this.rooms.get(roomId.toLowerCase());
      if (room != null)
        return Optional.of(room);
    }

    final var player = Bukkit.getPlayer(playerUuid);
    if (player != null && player.isOnline()) {
      final var room = getRoomAt(player.getLocation());
      room.ifPresent(r -> this.playerCurrentRoom.put(playerUuid, r.getId()));
      return room;
    }

    return Optional.empty();
  }

  @Override
  public void registerPreset(final @NotNull RoomPreset preset) {
    this.presets.put(preset.getId().toLowerCase(), preset);
  }

  @Override
  public @NotNull Optional<RoomPreset> getPreset(final @NotNull String presetId) {
    return Optional.ofNullable(this.presets.get(presetId.toLowerCase()));
  }

  @Override
  public @NotNull Collection<RoomPreset> getPresets() {
    return Collections.unmodifiableCollection(this.presets.values());
  }

  @Override
  public int getEffectiveIsolation(final @NotNull AcousticRoom room) {
    if (room.getIsolationPctOverride() != null)
      return Math.max(0, Math.min(100, room.getIsolationPctOverride()));

    final var preset = this.presets.get(room.getPresetId().toLowerCase());
    if (preset != null)
      return Math.max(0, Math.min(100, preset.getIsolationPct()));

    return 100;
  }

  @Override
  public double calculateRoomAttenuationDb(final @NotNull UUID senderUuid, final @NotNull UUID receiverUuid) {
    final var senderRoom = getPlayerRoom(senderUuid).orElse(null);
    final var receiverRoom = getPlayerRoom(receiverUuid).orElse(null);

    if (senderRoom == null && receiverRoom == null)
      return 0.0;

    if (senderRoom != null && receiverRoom != null && senderRoom.getId().equalsIgnoreCase(receiverRoom.getId()))
      return 0.0;

    var totalLoss = 0.0;

    if (senderRoom != null) {
      final var isolation = getEffectiveIsolation(senderRoom);
      if (isolation >= 100)
        return 100.0;
      totalLoss += (isolation / 100.0) * 35.0;
    }

    if (receiverRoom != null) {
      final var isolation = getEffectiveIsolation(receiverRoom);
      if (isolation >= 100)
        return 100.0;
      totalLoss += (isolation / 100.0) * 35.0;
    }

    return Math.min(100.0, totalLoss);
  }

  @Override
  public void reload() {
    if (!this.configFile.exists())
      this.plugin.saveResource("modules/room/config.json", false);

    try {
      this.config = Configurations.loadJson(this.configFile, VoiceRoomConfig.class);
    } catch (final Exception e) {
      this.plugin.getLogger().warning("[VoiceRoom] Failed to load room config: " + e.getMessage());
    }
    if (this.config == null)
      this.config = new VoiceRoomConfig();

    this.presets.clear();
    if (this.config.getPresets() != null) {
      for (final var entry : this.config.getPresets().entrySet()) {
        entry.getValue().setId(entry.getKey());
        this.presets.put(entry.getKey().toLowerCase(), entry.getValue());
      }
    }
  }

  @Override
  public void save() {
    VoiceRoomPersistence.save(this, this.moduleDir);
  }

  // ###############################################################
  // ---------------------- EVENT LISTENERS ------------------------
  // ###############################################################

  @EventHandler
  private void onPlayerEnterCuboid(final @NotNull CuboidPlayerEnterEvent event) {
    final var player = event.getPlayer();
    final var cuboid = event.getCuboid();

    for (final var room : this.rooms.values()) {
      if (room.getCuboids().contains(cuboid)) {
        this.playerCurrentRoom.put(player.getUniqueId(), room.getId());
        attachRoomFilters(player.getUniqueId(), room);
        break;
      }
    }
  }

  @EventHandler
  private void onPlayerLeaveCuboid(final @NotNull CuboidPlayerLeaveEvent event) {
    final var player = event.getPlayer();
    final var cuboid = event.getCuboid();

    final var currentRoomId = this.playerCurrentRoom.get(player.getUniqueId());
    if (currentRoomId == null)
      return;

    final var room = this.rooms.get(currentRoomId.toLowerCase());
    if (room != null && room.getCuboids().contains(cuboid)) {
      final var newRoom = getRoomAt(player.getLocation());
      if (newRoom.isPresent()) {
        this.playerCurrentRoom.put(player.getUniqueId(), newRoom.get().getId());
        detachRoomFilters(player.getUniqueId(), room);
        attachRoomFilters(player.getUniqueId(), newRoom.get());
      } else {
        this.playerCurrentRoom.remove(player.getUniqueId());
        detachRoomFilters(player.getUniqueId(), room);
      }
    }
  }

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    final var uuid = event.getPlayer().getUniqueId();
    final var roomId = this.playerCurrentRoom.remove(uuid);
    if (roomId != null) {
      final var room = this.rooms.get(roomId.toLowerCase());
      if (room != null)
        detachRoomFilters(uuid, room);
    }
  }

  private void attachRoomFilters(final @NotNull UUID playerUuid, final @NotNull AcousticRoom room) {
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    if (filterService == null)
      return;

    final var filters = new ArrayList<String>();
    final var preset = this.presets.get(room.getPresetId().toLowerCase());
    if (preset != null && preset.getFilters() != null)
      filters.addAll(preset.getFilters());

    if (room.getAdditionalFilters() != null)
      filters.addAll(room.getAdditionalFilters());

    for (final var filterId : filters)
      filterService.addFilter(playerUuid, filterId);
  }

  private void detachRoomFilters(final @NotNull UUID playerUuid, final @NotNull AcousticRoom room) {
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    if (filterService == null)
      return;

    final var filters = new ArrayList<String>();
    final var preset = this.presets.get(room.getPresetId().toLowerCase());
    if (preset != null && preset.getFilters() != null)
      filters.addAll(preset.getFilters());

    if (room.getAdditionalFilters() != null)
      filters.addAll(room.getAdditionalFilters());

    for (final var filterId : filters)
      filterService.removeFilter(playerUuid, filterId);
  }

}
