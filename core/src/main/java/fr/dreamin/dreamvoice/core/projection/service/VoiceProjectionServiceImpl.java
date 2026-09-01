package fr.dreamin.dreamvoice.core.projection.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link VoiceProjectionService} managing body anchors,
 * remote acoustic projection, bidirectional listening, and persistence.
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
  private final @NotNull Map<String, StaticAudioChannel> playerAudioChannels = new ConcurrentHashMap<>();
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

  // ###############################################################
  // ------------------- PUBLIC SERVICE METHODS --------------------
  // ###############################################################

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
    final var uidStr = playerUuid.toString();
    this.playerAudioChannels.keySet().removeIf(k -> k.contains(uidStr));
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
    this.playerAudioChannels.clear();
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
  // ------------------- PRIVATE HELPER METHODS --------------------
  // ###############################################################

  private void cleanupIdleChannels() {
    final var now = System.currentTimeMillis();
    this.lastChannelActivity.entrySet().removeIf(entry -> {
      if (now - entry.getValue() > INACTIVITY_TIMEOUT_MS) {
        this.playerAudioChannels.remove(entry.getKey());
        return true;
      }
      return false;
    });
  }

  private @Nullable StaticAudioChannel getOrCreateChannel(final @NotNull String streamKey, final @NotNull VoicechatConnection conn) {
    return this.playerAudioChannels.computeIfAbsent(streamKey, id -> {
      final var ch = this.api.createStaticAudioChannel(UUID.randomUUID());
      if (ch != null) {
        ch.addTarget(conn);
        if (this.volumeCategory != null)
          ch.setCategory(this.volumeCategory.getId());
      }
      return ch;
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

    for (final var listener : Bukkit.getOnlinePlayers()) {
      if (listener.getUniqueId().equals(senderUuid) || !listener.getWorld().equals(anchorWorld))
        continue;

      final var dist = anchorLoc.distance(listener.getLocation());
      if (dist > proj.getDistance())
        continue;

      final var listenerConn = this.api.getConnectionOf(listener.getUniqueId());
      if (listenerConn == null)
        continue;

      var totalDbLoss = 0.0;
      if (proj.isApplyVoiceWall() && wallService != null && wallService.isEnable()) {
        final var ray = VoiceRayCast.check(anchorLoc, listener);
        if (ray.isBlocked())
          totalDbLoss = ray.totalAttenuation();
      }

      if (totalDbLoss >= 99.0)
        continue;

      transmitProjectedPacket(
        senderUuid,
        listener.getUniqueId(),
        listenerConn,
        rawOpus,
        proj.getFilterId(),
        dist,
        proj.getDistance(),
        totalDbLoss,
        voiceService,
        wallService,
        filterService,
        "proj_out:" + senderUuid + ":" + listener.getUniqueId()
      );
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
      if (ray.isBlocked())
        totalDbLoss = ray.totalAttenuation();
    }

    if (totalDbLoss >= 99.0)
      return;

    transmitProjectedPacket(
      senderUuid,
      proj.getPlayerUuid(),
      ownerConn,
      rawOpus,
      null,
      dist,
      proj.getDistance(),
      totalDbLoss,
      voiceService,
      wallService,
      filterService,
      "proj_in:" + senderUuid + ":" + proj.getPlayerUuid()
    );
  }

  private void transmitProjectedPacket(
    final @NotNull UUID senderUuid,
    final @NotNull UUID receiverUuid,
    final @NotNull VoicechatConnection receiverConn,
    final byte[] rawOpus,
    final @Nullable String customFilterId,
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
      final var encoder = voiceService.getEncoder(senderUuid);
      final var pcm = decoder.decode(rawOpus);
      if (pcm == null || pcm.length == 0)
        return;

      var processed = pcm;
      if (customFilterId != null && filterService != null && !customFilterId.equalsIgnoreCase("none")) {
        final var filter = filterService.getFilter(customFilterId);
        if (filter != null)
          processed = filter.process(processed, null);
      } else if (filterService != null && filterService.hasActiveFilters(senderUuid))
        processed = filterService.applyFilters(senderUuid, processed);

      final var distRatio = Math.min(1.0, dist / maxDist);
      final var distGain = (float) Math.max(0.05, 1.0 - (distRatio * 0.85));
      final var wallGain = (float) Math.pow(10.0, -totalDbLoss / 20.0);
      final var combinedGain = wallGain * distGain;

      for (int i = 0; i < processed.length; i++)
        processed[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(processed[i] * combinedGain)));

      if (wallService != null && wallService.isAirDampingEnabled() && dist > 5.0) {
        final var alpha = Math.max(0.10f, 1.0f - (float) (dist - 5.0) * 0.038f);
        var smooth = (float) processed[0];
        for (int i = 0; i < processed.length; i++) {
          smooth = smooth + alpha * (processed[i] - smooth);
          processed[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(smooth)));
        }
      }

      processed = AudioLimiter.process(processed);

      final var newOpus = encoder.encode(processed);
      final var ch = getOrCreateChannel(streamKey, receiverConn);
      if (ch != null) {
        ch.send(newOpus);
        this.lastChannelActivity.put(streamKey, System.currentTimeMillis());
      }
    } catch (Exception e) {
      this.plugin.getLogger().warning("Failed to transmit projected packet (sender=" + senderUuid + ", receiver=" + receiverUuid + "): " + e.getMessage());
    }
  }

  // ###############################################################
  // ---------------------- EVENT LISTENERS ------------------------
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
