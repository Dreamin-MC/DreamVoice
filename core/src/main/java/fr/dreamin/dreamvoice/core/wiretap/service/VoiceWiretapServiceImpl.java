package fr.dreamin.dreamvoice.core.wiretap.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.utils.audio.AudioLimiter;
import fr.dreamin.dreamvoice.core.utils.raycast.VoiceRayCast;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

public final class VoiceWiretapServiceImpl implements VoiceWiretapService, Listener {

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;
  private VolumeCategory volumeCategory;

  private final @NotNull Map<String, VoiceWiretap> wiretaps = new ConcurrentHashMap<>();
  private final @NotNull Map<String, StaticAudioChannel> channels = new ConcurrentHashMap<>();
  private final @NotNull Map<String, Long> lastChannelActivity = new ConcurrentHashMap<>();

  public VoiceWiretapServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;
    Bukkit.getPluginManager().registerEvents(this, plugin);

    // Periodic GC cleaner for idle channels (every 30 seconds)
    Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupIdleChannels, 600L, 600L);
  }

  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;

    this.volumeCategory = this.api.volumeCategoryBuilder()
      .setId("wiretap_vol")
      .setName("Wiretap / Bug")
      .setDescription("Volume for hidden wiretaps and listening bugs")
      .build();

    this.api.registerVolumeCategory(this.volumeCategory);
  }

  @Override
  public VoicechatServerApi getAPI() {
    return this.api;
  }

  @Override
  public @NotNull VoiceWiretap createWiretap(final @NotNull String name, final @NotNull Location location) {
    final var wiretap = new VoiceWiretap(name, location);
    register(wiretap);
    return wiretap;
  }

  @Override
  public @NotNull VoiceWiretap createWiretap(final @NotNull String name, final @NotNull org.bukkit.entity.Entity entity) {
    final var wiretap = new VoiceWiretap(name, entity);
    register(wiretap);
    return wiretap;
  }

  @Override
  public void attachToEntity(final @NotNull String name, final @NotNull org.bukkit.entity.Entity entity) {
    final var wt = getWiretap(name);
    if (wt != null) {
      wt.setTargetEntity(entity);
    }
  }

  @Override
  public void detachFromEntity(final @NotNull String name) {
    final var wt = getWiretap(name);
    if (wt != null) {
      wt.setLocation(wt.getLocation());
      wt.setTargetEntity(null);
    }
  }


  @Override
  public void register(final @NotNull VoiceWiretap wiretap) {
    this.wiretaps.put(wiretap.getName().toLowerCase(), wiretap);
  }

  @Override
  public void removeWiretap(final @NotNull String name) {
    final var wt = this.wiretaps.remove(name.toLowerCase());
    if (wt != null) {
      wt.stopRecording();
      final var idStr = wt.getUuid().toString();
      this.channels.keySet().removeIf(k -> k.contains(idStr));
      this.lastChannelActivity.keySet().removeIf(k -> k.contains(idStr));
    }
  }

  @Override
  public void removeWiretap(final @NotNull UUID uuid) {
    this.wiretaps.values().removeIf(w -> w.getUuid().equals(uuid));
  }

  @Override
  public @Nullable VoiceWiretap getWiretap(final @NotNull String name) {
    return this.wiretaps.get(name.toLowerCase());
  }

  @Override
  public @Nullable VoiceWiretap getWiretap(final @NotNull UUID uuid) {
    return this.wiretaps.values().stream()
      .filter(w -> w.getUuid().equals(uuid))
      .findFirst()
      .orElse(null);
  }

  @Override
  public @NotNull Collection<VoiceWiretap> getWiretaps() {
    return Collections.unmodifiableCollection(this.wiretaps.values());
  }

  @Override
  public void addListener(final @NotNull String name, final @NotNull UUID playerUuid) {
    final var wt = getWiretap(name);
    if (wt != null)
      wt.addListener(playerUuid);
  }

  @Override
  public void removeListener(final @NotNull String name, final @NotNull UUID playerUuid) {
    final var wt = getWiretap(name);
    if (wt != null)
      wt.removeListener(playerUuid);
  }

  @Override
  public void removeListenerFromAll(final @NotNull UUID playerUuid) {
    this.wiretaps.values().forEach(w -> w.removeListener(playerUuid));
    final var idStr = playerUuid.toString();
    this.channels.keySet().removeIf(k -> k.contains(idStr));
    this.lastChannelActivity.keySet().removeIf(k -> k.contains(idStr));
  }

  @Override
  public @Nullable VoiceRecording startRecording(final @NotNull String name) {
    final var wt = getWiretap(name);
    if (wt == null)
      return null;
    final var rec = wt.startRecording();
    final var recService = DreamVoice.getService(VoiceRecordingService.class);
    if (recService != null)
      recService.register(rec);
    return rec;
  }

  @Override
  public @Nullable VoiceRecording stopRecording(final @NotNull String name) {
    final var wt = getWiretap(name);
    if (wt == null)
      return null;
    final var rec = wt.stopRecording();
    if (rec != null) {
      final var recordingsDir = new java.io.File(this.plugin.getDataFolder(), "recordings");
      fr.dreamin.dreamvoice.core.recording.storage.VoiceRecordingPersistence.save(rec, recordingsDir);
    }
    return rec;
  }

  private void cleanupIdleChannels() {
    final var now = System.currentTimeMillis();
    this.lastChannelActivity.entrySet().removeIf(entry -> {
      if (now - entry.getValue() > 30000L) {
        this.channels.remove(entry.getKey());
        return true;
      }
      return false;
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

    for (final var wiretap : this.wiretaps.values()) {
      final var wtLoc = wiretap.getLocation();
      final var wtWorld = wtLoc.getWorld();
      if (wtWorld == null || !wtWorld.equals(senderPlayer.getWorld()))
        continue;

      final var dist = senderPlayer.getLocation().distance(wtLoc);
      if (dist > wiretap.getDistance())
        continue;

      var totalDbLoss = 0.0;
      if (wiretap.isApplyVoiceWall() && wallService != null && wallService.isEnable()) {
        final var ray = VoiceRayCast.check(senderPlayer.getEyeLocation(), wtLoc);
        if (ray.isBlocked())
          totalDbLoss = ray.totalAttenuation();
      }

      if (totalDbLoss >= 99.0)
        continue;

      final var distRatio = Math.min(1.0, dist / wiretap.getDistance());
      final var distGain = (float) Math.max(0.05, 1.0 - (distRatio * 0.85));

      try {
        final var decoder = voiceService.getDecoder(senderUuid);
        final var encoder = voiceService.getEncoder(senderUuid);
        final var pcm = decoder.decode(rawOpus);
        if (pcm != null && pcm.length > 0) {
          var processed = pcm.clone();

          // Apply wiretap filter or speaker filter
          final var filterId = wiretap.getFilterId();
          if (filterId != null && filterService != null && !filterId.equalsIgnoreCase("none")) {
            final var filter = filterService.getFilter(filterId);
            if (filter != null)
              processed = filter.process(processed, null);
          } else if (filterService != null && filterService.hasActiveFilters(senderUuid)) {
            processed = filterService.applyFilters(senderUuid, processed);
          }

          // Gain & attenuation
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
          processed = AudioLimiter.process(processed);

          final var finalOpus = encoder.encode(processed);

          // If recording, add frame
          if (wiretap.isRecording() && wiretap.getActiveRecording() != null) {
            wiretap.getActiveRecording().addAudio(finalOpus);
          }

          // Broadcast to live listeners
          final var listeners = wiretap.getListeners();
          if (!listeners.isEmpty()) {
            final var now = System.currentTimeMillis();
            for (final var listenerUuid : listeners) {
              if (listenerUuid.equals(senderUuid))
                continue;

              final var conn = this.api.getConnectionOf(listenerUuid);
              if (conn == null)
                continue;

              final var streamKey = wiretap.getUuid() + ":" + senderUuid + ":" + listenerUuid;
              final var ch = this.channels.computeIfAbsent(streamKey, k -> {
                final var sc = this.api.createStaticAudioChannel(UUID.randomUUID());
                if (sc != null) {
                  sc.addTarget(conn);
                  if (this.volumeCategory != null)
                    sc.setCategory(this.volumeCategory.getId());
                }
                return sc;
              });

              if (ch != null) {
                ch.send(finalOpus);
                this.lastChannelActivity.put(streamKey, now);
              }
            }
          }
        }
      } catch (Exception ignored) {
      }
    }
  }

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    removeListenerFromAll(event.getPlayer().getUniqueId());
  }

}
