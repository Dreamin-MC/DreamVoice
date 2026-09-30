package fr.dreamin.dreamvoice.common.room.service;

import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.room.model.AcousticRoom;
import fr.dreamin.dreamvoice.api.room.model.RoomPreset;
import fr.dreamin.dreamvoice.api.room.service.VoiceRoomService;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.room.config.VoiceRoomConfig;
import fr.dreamin.dreamvoice.common.room.data.VoiceRoomPersistence;
import fr.dreamin.dreamvoice.common.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link VoiceRoomService} managing acoustic rooms, presets,
 * soundproofing boundary checks, and automated persistence.
 */
public final class VoiceRoomServiceImpl implements VoiceRoomService {

  private final @NotNull VoicePlatform platform;
  private final @NotNull File moduleDir;
  private final @NotNull File configFile;

  private @NotNull VoiceRoomConfig config = new VoiceRoomConfig();
  private final Map<String, RoomPreset> presets = new ConcurrentHashMap<>();
  private final Map<String, AcousticRoom> rooms = new ConcurrentHashMap<>();
  private final Map<UUID, String> playerCurrentRoom = new ConcurrentHashMap<>();

  public VoiceRoomServiceImpl(final @NotNull VoicePlatform platform) {
    this.platform = platform;
    this.moduleDir = platform.getDataDirectory().resolve("modules/room").toFile();
    this.configFile = new File(this.moduleDir, "config.json");

    if (!this.moduleDir.exists())
      this.moduleDir.mkdirs();

    reload();
    VoiceRoomPersistence.load(this, this.moduleDir, this.platform);

    this.platform.runTimer(this::checkPlayerRooms, 10L, 10L);
  }

  @Override
  public void registerRoom(final @NotNull AcousticRoom room) {
    this.rooms.put(room.getId().toLowerCase(), room);
  }

  @Override
  public void unregisterRoom(final @NotNull String roomId) {
    final var removed = this.rooms.remove(roomId.toLowerCase());
    if (removed != null)
      this.playerCurrentRoom.entrySet().removeIf(e -> e.getValue().equalsIgnoreCase(roomId));
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
  public @NotNull Optional<AcousticRoom> getRoomAt(final @NotNull VoiceLocation location) {
    for (final var room : this.rooms.values())
      for (final var cuboid : room.getCuboids())
        if (cuboid.contains(location))
          return Optional.of(room);
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

    final var loc = this.platform.getPlayerLocation(playerUuid);
    if (loc.isPresent()) {
      final var room = getRoomAt(loc.get());
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
      final var iso = getEffectiveIsolation(senderRoom);
      if (iso >= 100)
        return 100.0;
      totalLoss += (iso / 100.0) * 60.0;
    }

    if (receiverRoom != null) {
      final var iso = getEffectiveIsolation(receiverRoom);
      if (iso >= 100)
        return 100.0;
      totalLoss += (iso / 100.0) * 60.0;
    }

    return Math.min(100.0, totalLoss);
  }

  @Override
  public void reload() {
    this.presets.clear();

    if (this.configFile.exists()) {
      try {
        final var loaded = JsonUtils.load(this.configFile, VoiceRoomConfig.class);
        if (loaded != null)
          this.config = loaded;
      } catch (final Exception e) {
        this.platform.logError("[DreamVoice] Error loading modules/room/config.json", e);
      }
    } else
      createDefaultConfig();

    if (this.config.getPresets() != null)
      this.presets.putAll(this.config.getPresets());
  }

  @Override
  public void save() {
    VoiceRoomPersistence.save(this, this.moduleDir, this.platform);
  }

  private void checkPlayerRooms() {
    final var filterService = DreamVoiceCommon.getInstance() != null ? DreamVoiceCommon.getInstance().getFilterService() : null;

    for (final var playerUuid : this.platform.getOnlinePlayers()) {
      final var locOpt = this.platform.getPlayerLocation(playerUuid);
      if (locOpt.isEmpty())
        continue;

      final var newRoom = getRoomAt(locOpt.get()).orElse(null);
      final var oldRoomId = this.playerCurrentRoom.get(playerUuid);

      if (newRoom != null) {
        if (oldRoomId == null || !oldRoomId.equalsIgnoreCase(newRoom.getId())) {
          // Changed room or entered
          if (oldRoomId != null) {
            final var oldRoom = this.rooms.get(oldRoomId.toLowerCase());
            if (oldRoom != null && filterService != null)
              for (final var filter : oldRoom.getAdditionalFilters())
                filterService.removeFilter(playerUuid, filter);
          }

          this.playerCurrentRoom.put(playerUuid, newRoom.getId());
          if (filterService != null)
            for (final var filter : newRoom.getAdditionalFilters())
              filterService.addFilter(playerUuid, filter);
        }
      } else if (oldRoomId != null) {
        // Exited room
        this.playerCurrentRoom.remove(playerUuid);
        final var oldRoom = this.rooms.get(oldRoomId.toLowerCase());
        if (oldRoom != null && filterService != null)
          for (final var filter : oldRoom.getAdditionalFilters())
            filterService.removeFilter(playerUuid, filter);
      }
    }
  }

  private void createDefaultConfig() {
    this.config = new VoiceRoomConfig();
    this.config.setEnabled(true);

    this.presets.put("default", RoomPreset.builder().id("default").name("Standard Room").isolationPct(100).build());
    this.presets.put("glass", RoomPreset.builder().id("glass").name("Glass Booth").isolationPct(60).build());
    this.presets.put("vent", RoomPreset.builder().id("vent").name("Ventilation Shaft").isolationPct(30).build());
    this.presets.put("vault", RoomPreset.builder().id("vault").name("Heavy Vault").isolationPct(100).build());

    this.config.setPresets(this.presets);

    try {
      JsonUtils.save(this.configFile, this.config);
    } catch (final Exception e) {
      this.platform.logError("[DreamVoice] Error writing default modules/room/config.json", e);
    }
  }

}
