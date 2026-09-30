package fr.dreamin.dreamvoice.common.projection.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.projection.model.VoiceProjection;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.projection.storage.ProjectionsPersistence;
import fr.dreamin.dreamvoice.common.utils.audio.AudioLimiter;
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
 * Platform-independent implementation of {@link VoiceProjectionService} managing body anchors,
 * remote acoustic projection with 3D spatialization, bidirectional listening, and persistence.
 */
public final class VoiceProjectionServiceImpl implements VoiceProjectionService {

  private static final long INACTIVITY_TIMEOUT_MS = 30000L;
  private static final String CATEGORY_ID = "proj_volume";
  private static final String CATEGORY_NAME = "Projection";
  private static final String CATEGORY_DESC = "Volume for body anchor voice projections and camera listening";

  private final @NotNull VoicePlatform platform;
  private @NotNull VoicechatServerApi api;
  private VolumeCategory volumeCategory;

  private final @NotNull Map<UUID, VoiceProjection> projections = new ConcurrentHashMap<>();
  private final @NotNull Map<String, LocationalAudioChannel> locationalAudioChannels = new ConcurrentHashMap<>();
  private final @NotNull Map<String, StaticAudioChannel> staticAudioChannels = new ConcurrentHashMap<>();
  private final @NotNull Map<String, Long> lastChannelActivity = new ConcurrentHashMap<>();
  private final @NotNull Map<String, OpusEncoder> streamEncoders = new ConcurrentHashMap<>();

  private boolean voiceServiceMissingLogged = false;

  public VoiceProjectionServiceImpl(final @NotNull VoicePlatform platform) {
    this.platform = platform;
    this.platform.runTimer(this::cleanupChannels, 600L, 600L);
  }

  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;

    this.volumeCategory = api.volumeCategoryBuilder()
      .setId(CATEGORY_ID)
      .setName(CATEGORY_NAME)
      .setDescription(CATEGORY_DESC)
      .build();

    api.registerVolumeCategory(this.volumeCategory);
  }

  @Override
  public VoicechatServerApi getAPI() {
    return this.api;
  }

  @Override
  public VolumeCategory getVolumeCategory() {
    return this.volumeCategory;
  }

  @Override
  public void register(final @NotNull VoiceProjection projection) {
    this.projections.put(projection.getPlayerUuid(), projection);
  }

  @Override
  public void registerProjection(final @NotNull VoiceProjection projection) {
    register(projection);
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
  public void updateLocation(final @NotNull UUID playerUuid, final @NotNull VoiceLocation newLocation) {
    final var proj = getProjection(playerUuid);
    if (proj != null)
      proj.setAnchorLocation(newLocation);
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
  public @NotNull VoiceProjection createProjection(final @NotNull UUID playerUuid, final @NotNull VoiceLocation anchorLocation) {
    return createProjection(playerUuid, anchorLocation, null);
  }

  @Override
  public @NotNull VoiceProjection createProjection(final @NotNull UUID playerUuid, final @NotNull VoiceLocation anchorLocation, final @Nullable UUID anchorEntityUuid) {
    final var proj = new VoiceProjection(playerUuid, anchorLocation, anchorEntityUuid);
    register(proj);
    return proj;
  }

  @Override
  public void save() {
    final var moduleDir = new File(this.platform.getDataDirectory().toFile(), "modules/projection");
    ProjectionsPersistence.save(this, moduleDir, this.platform);
  }

  @Override
  public void load() {
    final var moduleDir = new File(this.platform.getDataDirectory().toFile(), "modules/projection");
    clearProjections();
    ProjectionsPersistence.load(this, moduleDir, this.platform);
  }

  private void cleanupChannels() {
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

    final var isWallEnabled = proj.isApplyVoiceWall() && wallService != null && wallService.isEnable();
    final var maxDist = (float) proj.getDistance();

    final var customFilterId = proj.getFilterId();
    final var hasCustomFilter = customFilterId != null && !customFilterId.equalsIgnoreCase("none");
    final var hasActiveFilters = filterService != null && filterService.hasActiveFilters(senderUuid);
    final var hasFilters = hasCustomFilter || hasActiveFilters;

    final var now = System.currentTimeMillis();
    short[] basePcm = null;
    short[] filteredPcm = null;
    byte[] defaultFilteredOpus = null;

    for (final var listenerUuid : this.platform.getOnlinePlayers()) {
      if (listenerUuid.equals(senderUuid))
        continue;

      final var listenerLoc = this.platform.getPlayerLocation(listenerUuid).orElse(null);
      if (listenerLoc == null || !listenerLoc.world().equals(anchorLoc.world()))
        continue;

      final var dist = anchorLoc.distance(listenerLoc);
      if (dist > maxDist)
        continue;

      final var listenerConn = this.api.getConnectionOf(listenerUuid);
      if (listenerConn == null)
        continue;

      var totalDbLoss = 0.0;
      if (isWallEnabled) {
        if (!this.platform.hasLineOfSight(anchorLoc, listenerLoc))
          totalDbLoss = this.platform.computeAcousticOcclusion(anchorLoc, listenerLoc, null);
      }

      if (totalDbLoss >= 99.0)
        continue;

      final var streamKey = "proj_loc:" + proj.getUuid() + ":" + senderUuid + ":" + listenerUuid;
      byte[] audioToSend = rawOpus;

      if (hasFilters || totalDbLoss > 0.001) {
        if (basePcm == null) {
          try {
            final var decoder = voiceService.getDecoder(senderUuid);
            basePcm = decoder.decode(rawOpus);
          } catch (Exception e) {
            this.platform.logWarning("Failed to decode projected packet: " + e.getMessage());
            return;
          }
          if (basePcm == null || basePcm.length == 0)
            return;
        }

        if (hasFilters && filteredPcm == null) {
          filteredPcm = basePcm.clone();
          if (hasCustomFilter && filterService != null) {
            final var filter = filterService.getFilter(customFilterId);
            if (filter != null)
              filteredPcm = filter.process(filteredPcm, null);
          } else if (hasActiveFilters && filterService != null)
            filteredPcm = filterService.applyFilters(senderUuid, filteredPcm);
        }

        var encoder = this.streamEncoders.get(streamKey);
        if (encoder == null || encoder.isClosed()) {
          encoder = this.api.createEncoder();
          this.streamEncoders.put(streamKey, encoder);
        }

        final var lastTime = this.lastChannelActivity.get(streamKey);
        if (lastTime != null && (now - lastTime > 400L))
          encoder.resetState();

        if (totalDbLoss > 0.001) {
          final var sourcePcm = filteredPcm != null ? filteredPcm : basePcm;
          final var processed = sourcePcm.clone();
          final var wallGain = (float) Math.pow(10.0, -totalDbLoss / 20.0);
          for (int i = 0; i < processed.length; i++)
            processed[i] = (short) Math.clamp(Math.round(processed[i] * wallGain), Short.MIN_VALUE, Short.MAX_VALUE);

          final var limited = AudioLimiter.process(processed);
          audioToSend = encoder.encode(limited);
        } else {
          if (defaultFilteredOpus == null && filteredPcm != null) {
            final var limited = AudioLimiter.process(filteredPcm.clone());
            defaultFilteredOpus = encoder.encode(limited);
          }
          audioToSend = defaultFilteredOpus != null ? defaultFilteredOpus : rawOpus;
        }
      }

      var ch = this.locationalAudioChannels.get(streamKey);
      if (ch == null || ch.isClosed()) {
        final var sLevel = this.platform.getServerLevel(anchorLoc.world());
        if (sLevel != null) {
          final var pos = this.api.createPosition(anchorLoc.x(), anchorLoc.y(), anchorLoc.z());
          final var channelUuid = UUID.nameUUIDFromBytes(streamKey.getBytes(StandardCharsets.UTF_8));
          ch = this.api.createLocationalAudioChannel(channelUuid, sLevel, pos);
          if (ch != null) {
            ch.setFilter(sp -> sp.getUuid().equals(listenerUuid));
            ch.setDistance(maxDist);
            if (this.volumeCategory != null)
              ch.setCategory(this.volumeCategory.getId());
            this.locationalAudioChannels.put(streamKey, ch);
          }
        }
      }

      if (ch != null) {
        ch.updateLocation(this.api.createPosition(anchorLoc.x(), anchorLoc.y(), anchorLoc.z()));
        ch.send(audioToSend);
        this.lastChannelActivity.put(streamKey, now);
      }
    }
  }

  private void routeAnchorEnvironmentToOwner(
    final @NotNull VoiceProjection proj,
    final @NotNull UUID senderUuid,
    final byte[] rawOpus,
    final @NotNull VoiceService voiceService,
    final @Nullable VoiceWallService wallService,
    final @Nullable VoiceFilterService filterService
  ) {
    final var anchorLoc = proj.getAnchorLocation();
    final var senderLoc = this.platform.getPlayerLocation(senderUuid).orElse(null);
    if (senderLoc == null || !senderLoc.world().equals(anchorLoc.world()))
      return;

    final var dist = senderLoc.distance(anchorLoc);
    if (dist > proj.getDistance())
      return;

    final var ownerConn = this.api.getConnectionOf(proj.getPlayerUuid());
    if (ownerConn == null)
      return;

    var totalDbLoss = 0.0;
    if (proj.isApplyVoiceWall() && wallService != null && wallService.isEnable()) {
      final var senderEye = this.platform.getPlayerEyeLocation(senderUuid).orElse(senderLoc);
      if (!this.platform.hasLineOfSight(senderEye, anchorLoc))
        totalDbLoss = this.platform.computeAcousticOcclusion(senderEye, anchorLoc, null);
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

      processed = AudioLimiter.process(processed);

      final var now = System.currentTimeMillis();
      var encoder = this.streamEncoders.get(streamKey);
      if (encoder == null || encoder.isClosed()) {
        encoder = this.api.createEncoder();
        this.streamEncoders.put(streamKey, encoder);
      }

      final var lastTime = this.lastChannelActivity.get(streamKey);
      if (lastTime != null && (now - lastTime > 400L))
        encoder.resetState();

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
        this.lastChannelActivity.put(streamKey, now);
      }

    } catch (Exception e) {
      this.platform.logWarning("Failed to transmit static projected packet (stream=" + streamKey + "): " + e.getMessage());
    }
  }

  public void onMicrophone(final @NotNull MicrophonePacketEvent event) {
    final var senderConn = event.getSenderConnection();
    if (senderConn == null)
      return;

    final var senderUuid = senderConn.getPlayer().getUuid();
    final var rawOpus = event.getPacket().getOpusEncodedData();
    if (rawOpus == null || rawOpus.length == 0)
      return;

    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;

    final var voiceService = common.getService(VoiceService.class);
    final var wallService = common.getService(VoiceWallService.class);
    final var filterService = common.getService(VoiceFilterService.class);
    if (voiceService == null) {
      if (!this.voiceServiceMissingLogged) {
        this.voiceServiceMissingLogged = true;
        this.platform.logWarning("VoiceService is unavailable. Projection audio processing is skipped.");
      }
      return;
    }

    final var ownerProjection = this.projections.get(senderUuid);
    if (ownerProjection != null && ownerProjection.isEmitVoiceAtAnchor())
      routeOwnerVoiceToAnchorListeners(ownerProjection, senderUuid, rawOpus, voiceService, wallService, filterService);

    for (final var proj : this.projections.values()) {
      if (proj.getPlayerUuid().equals(senderUuid) || !proj.isHearAnchorEnvironment())
        continue;
      routeAnchorEnvironmentToOwner(proj, senderUuid, rawOpus, voiceService, wallService, filterService);
    }
  }

}
