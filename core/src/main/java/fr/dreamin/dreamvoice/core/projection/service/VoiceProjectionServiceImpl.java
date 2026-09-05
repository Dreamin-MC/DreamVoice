package fr.dreamin.dreamvoice.core.projection.service;

import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.projection.model.VoiceProjection;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.projection.storage.ProjectionsPersistence;
import fr.dreamin.dreamvoice.core.utils.audio.AudioLimiter;
import fr.dreamin.dreamvoice.core.utils.raycast.VoiceRayCast;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link VoiceProjectionService} managing body anchors,
 * remote acoustic projection with 3D spatialization, bidirectional listening, and persistence.
 */
public final class VoiceProjectionServiceImpl implements VoiceProjectionService, Listener {

  // ###############################################################
  // ----------------------- STATIC FIELDS -------------------------
  // ###############################################################

  private static final long CLEANUP_INTERVAL_TICKS = 600L;
  private static final long INACTIVITY_TIMEOUT_MS = 30000L;
  private static final String CATEGORY_ID = "proj_volume";
  private static final String CATEGORY_NAME = "Projection / Body Anchor";
  private static final String CATEGORY_DESC = "Volume for body anchor voice projections and camera listening";

  // ###############################################################
  // --------------------- INSTANCE FIELDS -------------------------
  // ###############################################################

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;
  private VolumeCategory volumeCategory;

  private final @NotNull Map<UUID, VoiceProjection> projections = new ConcurrentHashMap<>();
  private final @NotNull Map<String, LocationalAudioChannel> locationalAudioChannels = new ConcurrentHashMap<>();
  private final @NotNull Map<String, StaticAudioChannel> staticAudioChannels = new ConcurrentHashMap<>();
  private final @NotNull Map<String, OpusEncoder> streamEncoders = new ConcurrentHashMap<>();
  private final @NotNull Map<String, Long> lastChannelActivity = new ConcurrentHashMap<>();

  private boolean voiceServiceMissingLogged = false;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceProjectionServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;
    Bukkit.getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupIdleChannels, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
  }

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;

    this.volumeCategory = this.api.volumeCategoryBuilder()
      .setId(CATEGORY_ID)
      .setName(CATEGORY_NAME)
      .setDescription(CATEGORY_DESC)
      .build();

    this.api.registerVolumeCategory(this.volumeCategory);
  }

  @Override
  public VoicechatServerApi getAPI() {
    return this.api;
  }

  @Override
  public @NotNull VoiceProjection createProjection(final @NotNull UUID playerUuid, final @NotNull Location anchorLocation) {
    final var proj = new VoiceProjection(playerUuid, anchorLocation);
    register(proj);
    return proj;
  }

  @Override
  public @NotNull VoiceProjection createProjection(final @NotNull UUID playerUuid, final @NotNull Entity anchorEntity) {
    final var proj = new VoiceProjection(playerUuid, anchorEntity);
    register(proj);
    return proj;
  }

  @Override
  public void register(final @NotNull VoiceProjection projection) {
    this.projections.put(projection.getPlayerUuid(), projection);
  }

  @Override
  public void removeProjection(final @NotNull UUID playerUuid) {
    this.projections.remove(playerUuid);
    cleanupStreamsMatching(playerUuid.toString());
  }

  @Override
  public @Nullable VoiceProjection getProjection(final @NotNull UUID playerUuid) {
    return this.projections.get(playerUuid);
  }

  @Override
  public @Nullable VoiceProjection getProjectionById(final @NotNull UUID projectionId) {
    return this.projections.values().stream()
      .filter(p -> p.getUuid().equals(projectionId))
      .findFirst()
      .orElse(null);
  }

  @Override
  public boolean hasProjection(final @NotNull UUID playerUuid) {
    return this.projections.containsKey(playerUuid);
  }

  @Override
  public @NotNull Collection<VoiceProjection> getProjections() {
    return Collections.unmodifiableCollection(this.projections.values());
  }

  @Override
  public void clearProjections() {
    this.projections.clear();
    this.locationalAudioChannels.clear();
    this.staticAudioChannels.clear();
    this.lastChannelActivity.clear();
    this.streamEncoders.values().forEach(enc -> {
      if (!enc.isClosed()) {
        try {
          enc.close();
        } catch (Throwable ignored) {}
      }
    });
    this.streamEncoders.clear();
  }

  @Override
  public void updateLocation(final @NotNull UUID playerUuid, final @NotNull Location newLocation) {
    final var proj = this.projections.get(playerUuid);
    if (proj != null)
      proj.setAnchorLocation(newLocation);
  }

  @Override
  public void save() {
    ProjectionsPersistence.save(this, new File(this.plugin.getDataFolder(), "data"));
  }

  @Override
  public void load() {
    clearProjections();
    ProjectionsPersistence.load(this, new File(this.plugin.getDataFolder(), "data"));
  }

  // ###############################################################
  // ----------------------- PRIVATE METHODS -----------------------
  // ###############################################################

  private void cleanupIdleChannels() {
    final var now = System.currentTimeMillis();
    this.lastChannelActivity.entrySet().removeIf(entry -> {
      if (now - entry.getValue() > INACTIVITY_TIMEOUT_MS) {
        final var key = entry.getKey();
        this.locationalAudioChannels.remove(key);
        this.staticAudioChannels.remove(key);
        final var enc = this.streamEncoders.remove(key);
        if (enc != null && !enc.isClosed()) {
          try {
            enc.close();
          } catch (Throwable ignored) {}
        }
        return true;
      }
      return false;
    });
  }

  private void cleanupStreamsMatching(final @NotNull String substring) {
    this.locationalAudioChannels.keySet().removeIf(k -> k.contains(substring));
    this.staticAudioChannels.keySet().removeIf(k -> k.contains(substring));
    this.lastChannelActivity.keySet().removeIf(k -> k.contains(substring));
    this.streamEncoders.entrySet().removeIf(e -> {
      if (e.getKey().contains(substring)) {
        if (!e.getValue().isClosed()) {
          try {
            e.getValue().close();
          } catch (Throwable ignored) {}
        }
        return true;
      }
      return false;
    });
  }

  private void routeOwnerVoiceToAnchorListeners(
    final @NotNull VoiceProjection proj,
    final @NotNull UUID senderUuid,
    final byte[] rawOpus,
    final @NotNull VoiceService voiceService,
    final @Nullable VoiceWallService wallService,
    final @Nullable VoiceFilterService filterService
  ) {
    final var anchorLoc = proj.getAnchorLocation();
    final var anchorWorld = anchorLoc.getWorld();
    if (anchorWorld == null)
      return;

    final var isWallEnabled = proj.isApplyVoiceWall() && wallService != null && wallService.isEnable();
    final var maxDist = (float) proj.getDistance();

    for (final var listener : Bukkit.getOnlinePlayers()) {
      if (listener.getUniqueId().equals(senderUuid) || !listener.getWorld().equals(anchorWorld))
        continue;

      final var dist = anchorLoc.distance(listener.getLocation());
      if (dist > maxDist)
        continue;

      final var listenerConn = this.api.getConnectionOf(listener.getUniqueId());
      if (listenerConn == null)
        continue;

      var totalDbLoss = 0.0;
      if (isWallEnabled) {
        final var ray = VoiceRayCast.check(anchorLoc, listener);
        if (!ray.lineOfSight())
          totalDbLoss = ray.totalAttenuation();
      }

      if (totalDbLoss >= 99.0)
        continue;

      transmitProjectedLocationalPacket(
        proj,
        senderUuid,
        listener,
        rawOpus,
        dist,
        maxDist,
        totalDbLoss,
        voiceService,
        wallService,
        filterService
      );
    }
  }

  private void transmitProjectedLocationalPacket(
    final @NotNull VoiceProjection proj,
    final @NotNull UUID senderUuid,
    final @NotNull Player listener,
    final byte[] rawOpus,
    final double dist,
    final float maxDist,
    final double totalDbLoss,
    final @NotNull VoiceService voiceService,
    final @Nullable VoiceWallService wallService,
    final @Nullable VoiceFilterService filterService
  ) {
    final var streamKey = "proj_loc:" + proj.getUuid() + ":" + senderUuid + ":" + listener.getUniqueId();
    try {
      final var decoder = voiceService.getDecoder(senderUuid);
      final var pcm = decoder.decode(rawOpus);
      if (pcm == null || pcm.length == 0)
        return;

      var processed = pcm.clone();
      final var customFilterId = proj.getFilterId();
      if (customFilterId != null && filterService != null && !customFilterId.equalsIgnoreCase("none")) {
        final var filter = filterService.getFilter(customFilterId);
        if (filter != null)
          processed = filter.process(processed, null);
      } else if (filterService != null && filterService.hasActiveFilters(senderUuid)) {
        processed = filterService.applyFilters(senderUuid, processed);
      }

      if (totalDbLoss > 0.0) {
        final var wallGain = (float) Math.pow(10.0, -totalDbLoss / 20.0);
        for (int i = 0; i < processed.length; i++)
          processed[i] = (short) Math.clamp(Math.round(processed[i] * wallGain), Short.MIN_VALUE, Short.MAX_VALUE);
      }

      if (wallService != null && wallService.isAirDampingEnabled() && dist > 5.0) {
        final var alpha = Math.max(0.10f, 1.0f - (float) (dist - 5.0) * 0.038f);
        var smooth = (float) processed[0];
        for (int i = 0; i < processed.length; i++) {
          smooth = smooth + alpha * (processed[i] - smooth);
          processed[i] = (short) Math.clamp(Math.round(smooth), Short.MIN_VALUE, Short.MAX_VALUE);
        }
      }

      processed = AudioLimiter.process(processed);

      var encoder = this.streamEncoders.get(streamKey);
      if (encoder == null || encoder.isClosed()) {
        encoder = this.api.createEncoder();
        this.streamEncoders.put(streamKey, encoder);
      }

      final var newOpus = encoder.encode(processed);

      var ch = this.locationalAudioChannels.get(streamKey);
      if (ch == null || ch.isClosed()) {
        final var loc = proj.getAnchorLocation();
        final var sLevel = this.api.fromServerLevel(loc.getWorld());
        final var pos = this.api.createPosition(loc.getX(), loc.getY(), loc.getZ());
        final var channelUuid = UUID.nameUUIDFromBytes(streamKey.getBytes(StandardCharsets.UTF_8));
        ch = this.api.createLocationalAudioChannel(channelUuid, sLevel, pos);
        if (ch != null) {
          final var targetUuid = listener.getUniqueId();
          ch.setFilter(sp -> sp.getUuid().equals(targetUuid));
          ch.setDistance(maxDist);
          if (this.volumeCategory != null)
            ch.setCategory(this.volumeCategory.getId());
          this.locationalAudioChannels.put(streamKey, ch);
        }
      }

      if (ch != null) {
        final var loc = proj.getAnchorLocation();
        ch.updateLocation(this.api.createPosition(loc.getX(), loc.getY(), loc.getZ()));
        ch.send(newOpus);
        this.lastChannelActivity.put(streamKey, System.currentTimeMillis());
      }
    } catch (Exception e) {
      this.plugin.getLogger().warning("Failed to transmit locational projected packet (stream=" + streamKey + "): " + e.getMessage());
    }
  }

  private void routeAnchorEnvironmentToOwner(
    final @NotNull VoiceProjection proj,
    final @NotNull Player senderPlayer,
    final @NotNull UUID senderUuid,
    final byte[] rawOpus,
    final @NotNull VoiceService voiceService,
    final @Nullable VoiceWallService wallService,
    final @Nullable VoiceFilterService filterService
  ) {
    final var anchorLoc = proj.getAnchorLocation();
    if (!senderPlayer.getWorld().equals(anchorLoc.getWorld()))
      return;

    final var dist = senderPlayer.getLocation().distance(anchorLoc);
    if (dist > proj.getDistance())
      return;

    final var ownerConn = this.api.getConnectionOf(proj.getPlayerUuid());
    if (ownerConn == null)
      return;

    var totalDbLoss = 0.0;
    if (proj.isApplyVoiceWall() && wallService != null && wallService.isEnable()) {
      final var ray = VoiceRayCast.check(senderPlayer.getEyeLocation(), anchorLoc);
      if (!ray.lineOfSight())
        totalDbLoss = ray.totalAttenuation();
    }

    if (totalDbLoss >= 99.0)
      return;

    transmitProjectedStaticPacket(
      senderUuid,
      proj.getPlayerUuid(),
      ownerConn,
      rawOpus,
      dist,
      proj.getDistance(),
      totalDbLoss,
      voiceService,
      wallService,
      filterService,
      "proj_in:" + senderUuid + ":" + proj.getPlayerUuid()
    );
  }

  private void transmitProjectedStaticPacket(
    final @NotNull UUID senderUuid,
    final @NotNull UUID receiverUuid,
    final @NotNull VoicechatConnection receiverConn,
    final byte[] rawOpus,
    final double dist,
    final double maxDist,
    final double totalDbLoss,
    final @NotNull VoiceService voiceService,
    final @Nullable VoiceWallService wallService,
    final @Nullable VoiceFilterService filterService,
    final @NotNull String streamKey
  ) {
    try {
      final var decoder = voiceService.getDecoder(senderUuid);
      final var pcm = decoder.decode(rawOpus);
      if (pcm == null || pcm.length == 0)
        return;

      var processed = pcm.clone();
      if (filterService != null && filterService.hasActiveFilters(senderUuid))
        processed = filterService.applyFilters(senderUuid, processed);

      final var distRatio = Math.min(1.0, dist / maxDist);
      final var distGain = (float) Math.max(0.05, 1.0 - (distRatio * 0.85));
      final var wallGain = totalDbLoss > 0.0 ? (float) Math.pow(10.0, -totalDbLoss / 20.0) : 1.0f;
      final var combinedGain = wallGain * distGain;

      for (int i = 0; i < processed.length; i++)
        processed[i] = (short) Math.clamp(Math.round(processed[i] * combinedGain), Short.MIN_VALUE, Short.MAX_VALUE);

      if (wallService != null && wallService.isAirDampingEnabled() && dist > 5.0) {
        final var alpha = Math.max(0.10f, 1.0f - (float) (dist - 5.0) * 0.038f);
        var smooth = (float) processed[0];
        for (int i = 0; i < processed.length; i++) {
          smooth = smooth + alpha * (processed[i] - smooth);
          processed[i] = (short) Math.clamp(Math.round(smooth), Short.MIN_VALUE, Short.MAX_VALUE);
        }
      }

      processed = AudioLimiter.process(processed);

      var encoder = this.streamEncoders.get(streamKey);
      if (encoder == null || encoder.isClosed()) {
        encoder = this.api.createEncoder();
        this.streamEncoders.put(streamKey, encoder);
      }

      final var newOpus = encoder.encode(processed);

      var ch = this.staticAudioChannels.get(streamKey);
      if (ch == null || ch.isClosed()) {
        final var channelUuid = UUID.nameUUIDFromBytes(streamKey.getBytes(StandardCharsets.UTF_8));
        ch = this.api.createStaticAudioChannel(channelUuid);
        if (ch != null) {
          ch.addTarget(receiverConn);
          if (this.volumeCategory != null)
            ch.setCategory(this.volumeCategory.getId());
          this.staticAudioChannels.put(streamKey, ch);
        }
      }

      if (ch != null) {
        ch.send(newOpus);
        this.lastChannelActivity.put(streamKey, System.currentTimeMillis());
      }
    } catch (Exception e) {
      this.plugin.getLogger().warning("Failed to transmit static projected packet (stream=" + streamKey + "): " + e.getMessage());
    }
  }

  // ###############################################################
  // ---------------------- LISTENER METHODS -----------------------
  // ###############################################################

  @EventHandler
  private void onMicrophone(final @NotNull MicrophonePacketEvent event) {
    final var senderConn = event.getSender();
    if (senderConn == null)
      return;

    final var senderUuid = senderConn.getPlayer().getUuid();
    final var senderPlayer = Bukkit.getPlayer(senderUuid);
    if (senderPlayer == null)
      return;

    final var rawOpus = event.getPacket().getOpusEncodedData();
    if (rawOpus == null || rawOpus.length == 0)
      return;

    final var voiceService = DreamVoice.getService(VoiceService.class);
    final var wallService = DreamVoice.getService(VoiceWallService.class);
    final var filterService = DreamVoice.getService(VoiceFilterService.class);
    if (voiceService == null) {
      if (!this.voiceServiceMissingLogged) {
        this.voiceServiceMissingLogged = true;
        this.plugin.getLogger().warning("VoiceService is unavailable. Projection audio processing is skipped.");
      }
      return;
    }

    final var ownerProjection = this.projections.get(senderUuid);
    if (ownerProjection != null && ownerProjection.isEmitVoiceAtAnchor())
      routeOwnerVoiceToAnchorListeners(ownerProjection, senderUuid, rawOpus, voiceService, wallService, filterService);

    for (final var proj : this.projections.values()) {
      if (proj.getPlayerUuid().equals(senderUuid) || !proj.isHearAnchorEnvironment())
        continue;
      routeAnchorEnvironmentToOwner(proj, senderPlayer, senderUuid, rawOpus, voiceService, wallService, filterService);
    }
  }

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    removeProjection(event.getPlayer().getUniqueId());
  }

}
