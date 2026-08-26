package fr.dreamin.dreamvoice.core.projection.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.api.projection.model.VoiceProjection;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.utils.raycast.VoiceRayCast;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoiceProjectionServiceImpl implements VoiceProjectionService, Listener {

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;
  private VolumeCategory volumeCategory;

  private final @NotNull Map<UUID, VoiceProjection> projections = new ConcurrentHashMap<>();
  private final @NotNull Map<String, StaticAudioChannel> playerAudioChannels = new ConcurrentHashMap<>();
  private final @NotNull Map<String, Long> lastChannelActivity = new ConcurrentHashMap<>();

  public VoiceProjectionServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;
    Bukkit.getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupIdleChannels, 600L, 600L);
  }

  private void cleanupIdleChannels() {
    final var now = System.currentTimeMillis();
    this.lastChannelActivity.entrySet().removeIf(entry -> {
      if (now - entry.getValue() > 30000L) {
        this.playerAudioChannels.remove(entry.getKey());
        return true;
      }
      return false;
    });
  }


  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;

    this.volumeCategory = this.api.volumeCategoryBuilder()
      .setId("proj_volume")
      .setName("Projection / Body Anchor")
      .setDescription("Volume for body anchor voice projections and camera listening")
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
  public @NotNull VoiceProjection createProjection(final @NotNull UUID playerUuid, final @NotNull org.bukkit.entity.Entity anchorEntity) {
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
    if (proj != null) {
      proj.setAnchorLocation(newLocation);
    }
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

    // =========================================================================
    // Case 1: The speaker HAS a Projection (Player is speaking from camera/view)
    // -> Emit voice at Body Anchor location
    // =========================================================================
    final var ownerProjection = this.projections.get(senderUuid);
    if (ownerProjection != null && ownerProjection.isEmitVoiceAtAnchor()) {
      final var anchorLoc = ownerProjection.getAnchorLocation();
      final var anchorWorld = anchorLoc.getWorld();

      if (anchorWorld != null) {
        for (final var listener : Bukkit.getOnlinePlayers()) {
          if (listener.getUniqueId().equals(senderUuid))
            continue;
          if (!listener.getWorld().equals(anchorWorld))
            continue;

          final var dist = anchorLoc.distance(listener.getLocation());
          if (dist > ownerProjection.getDistance())
            continue;

          final var listenerConn = this.api.getConnectionOf(listener.getUniqueId());
          if (listenerConn == null)
            continue;

          // Raycast attenuation from Anchor to Listener
          var totalDbLoss = 0.0;
          if (ownerProjection.isApplyVoiceWall() && wallService != null && wallService.isEnable()) {
            final var ray = VoiceRayCast.check(anchorLoc, listener);
            if (ray.isBlocked())
              totalDbLoss = ray.totalAttenuation();
          }

          if (totalDbLoss >= 99.0)
            continue; // completely blocked

          // Distance falloff attenuation
          final var distRatio = Math.min(1.0, dist / ownerProjection.getDistance());
          final var distGain = (float) Math.max(0.05, 1.0 - (distRatio * 0.85));

          try {
            final var decoder = voiceService.getDecoder(senderUuid);
            final var encoder = voiceService.getEncoder(senderUuid);
            final var pcm = decoder.decode(rawOpus);
            if (pcm != null && pcm.length > 0) {
              var processed = pcm;

              // Apply filter if specified on projection or player
              final var filterId = ownerProjection.getFilterId();
              if (filterId != null && filterService != null && !filterId.equalsIgnoreCase("none")) {
                final var filter = filterService.getFilter(filterId);
                if (filter != null)
                  processed = filter.process(processed, null);
              } else if (filterService != null && filterService.hasActiveFilters(senderUuid)) {
                processed = filterService.applyFilters(senderUuid, processed);
              }

              // Apply gain
              final var wallGain = (float) Math.pow(10.0, -totalDbLoss / 20.0);
              final var combinedGain = wallGain * distGain;
              for (int i = 0; i < processed.length; i++) {
                processed[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(processed[i] * combinedGain)));
              }

              // Air damping
              if (wallService != null && wallService.isAirDampingEnabled() && dist > 5.0) {
                final var alpha = Math.max(0.10f, 1.0f - (float) (dist - 5.0) * 0.038f);
                var smooth = (float) processed[0];
                for (int i = 0; i < processed.length; i++) {
                  smooth = smooth + alpha * (processed[i] - smooth);
                  processed[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(smooth)));
                }
              }

              // Soft limiter
              processed = fr.dreamin.dreamvoice.core.utils.audio.AudioLimiter.process(processed);

              final var newOpus = encoder.encode(processed);
              final var streamKey = "proj_out:" + senderUuid + ":" + listener.getUniqueId();
              final var ch = getOrCreateChannel(streamKey, listenerConn);
              if (ch != null) {
                ch.send(newOpus);
                this.lastChannelActivity.put(streamKey, System.currentTimeMillis());
              }
            }
          } catch (Exception ignored) {
          }
        }
      }
    }

    // =========================================================================
    // Case 2: Another player is speaking near a Projection's Body Anchor
    // -> Transmit sound to the Projection owner (in remote camera/drone view)
    // =========================================================================
    for (final var proj : this.projections.values()) {
      if (proj.getPlayerUuid().equals(senderUuid))
        continue; // handled above
      if (!proj.isHearAnchorEnvironment())
        continue;

      final var anchorLoc = proj.getAnchorLocation();
      if (!senderPlayer.getWorld().equals(anchorLoc.getWorld()))
        continue;

      final var dist = senderPlayer.getLocation().distance(anchorLoc);
      if (dist > proj.getDistance())
        continue;

      final var ownerConn = this.api.getConnectionOf(proj.getPlayerUuid());
      if (ownerConn == null)
        continue;

      // Raycast attenuation between Speaker and Anchor
      var totalDbLoss = 0.0;
      if (proj.isApplyVoiceWall() && wallService != null && wallService.isEnable()) {
        final var ray = VoiceRayCast.check(senderPlayer.getEyeLocation(), anchorLoc);
        if (ray.isBlocked())
          totalDbLoss = ray.totalAttenuation();
      }

      if (totalDbLoss >= 99.0)
        continue;

      final var distRatio = Math.min(1.0, dist / proj.getDistance());
      final var distGain = (float) Math.max(0.05, 1.0 - (distRatio * 0.85));

      try {
        final var decoder = voiceService.getDecoder(senderUuid);
        final var encoder = voiceService.getEncoder(senderUuid);
        final var pcm = decoder.decode(rawOpus);
        if (pcm != null && pcm.length > 0) {
          var processed = pcm;

          if (filterService != null && filterService.hasActiveFilters(senderUuid))
            processed = filterService.applyFilters(senderUuid, processed);

          final var wallGain = (float) Math.pow(10.0, -totalDbLoss / 20.0);
          final var combinedGain = wallGain * distGain;
          for (int i = 0; i < processed.length; i++) {
            processed[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(processed[i] * combinedGain)));
          }

          if (wallService != null && wallService.isAirDampingEnabled() && dist > 5.0) {
            final var alpha = Math.max(0.10f, 1.0f - (float) (dist - 5.0) * 0.038f);
            var smooth = (float) processed[0];
            for (int i = 0; i < processed.length; i++) {
              smooth = smooth + alpha * (processed[i] - smooth);
              processed[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(smooth)));
            }
          }

          // Soft limiter
          processed = fr.dreamin.dreamvoice.core.utils.audio.AudioLimiter.process(processed);

          final var newOpus = encoder.encode(processed);
          final var streamKey = "proj_in:" + senderUuid + ":" + proj.getPlayerUuid();
          final var ch = getOrCreateChannel(streamKey, ownerConn);
          if (ch != null) {
            ch.send(newOpus);
            this.lastChannelActivity.put(streamKey, System.currentTimeMillis());
          }
        }
      } catch (Exception ignored) {
      }
    }

  }

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    removeProjection(event.getPlayer().getUniqueId());
  }


}
