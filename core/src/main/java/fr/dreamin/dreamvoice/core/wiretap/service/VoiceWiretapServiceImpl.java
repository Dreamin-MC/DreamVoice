package fr.dreamin.dreamvoice.core.wiretap.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.recording.storage.VoiceRecordingPersistence;
import fr.dreamin.dreamvoice.core.utils.audio.AudioLimiter;
import fr.dreamin.dreamvoice.core.utils.raycast.VoiceRayCast;
import fr.dreamin.dreamvoice.core.wiretap.storage.WiretapsPersistence;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of {@link VoiceWiretapService} managing spy microphones,
 * mobile bug tracking, covert eavesdropping streams, and direct cassette recording.
 */
public final class VoiceWiretapServiceImpl implements VoiceWiretapService, Listener {

  // ###############################################################
  // ----------------------- STATIC FIELDS -------------------------
  // ###############################################################

  private static final long CLEANUP_INTERVAL_TICKS = 600L;
  private static final long INACTIVITY_TIMEOUT_MS = 30000L;
  private static final String CATEGORY_ID = "wiretap_vol";
  private static final String CATEGORY_NAME = "Wiretap / Bug";
  private static final String CATEGORY_DESC = "Volume for hidden wiretaps and listening bugs";

  // ###############################################################
  // --------------------- INSTANCE FIELDS -------------------------
  // ###############################################################

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;
  private VolumeCategory volumeCategory;

  private final @NotNull Map<String, VoiceWiretap> wiretaps = new ConcurrentHashMap<>();
  private final @NotNull Map<String, StaticAudioChannel> channels = new ConcurrentHashMap<>();
  private final @NotNull Map<String, Long> lastChannelActivity = new ConcurrentHashMap<>();
  private final @NotNull Map<String, OpusEncoder> streamEncoders = new ConcurrentHashMap<>();
  private final @NotNull Map<String, Long> lastEncoderActivity = new ConcurrentHashMap<>();
  private boolean recordingServiceMissingLogged = false;
  private boolean voiceServiceMissingLogged = false;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceWiretapServiceImpl(final @NotNull DreamVoice plugin) {
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
  public @NotNull VoiceWiretap createWiretap(final @NotNull String name, final @NotNull Location location) {
    final var wiretap = new VoiceWiretap(name, location);
    register(wiretap);
    return wiretap;
  }

  @Override
  public @NotNull VoiceWiretap createWiretap(final @NotNull String name, final @NotNull Entity entity) {
    final var wiretap = new VoiceWiretap(name, entity);
    register(wiretap);
    return wiretap;
  }

  @Override
  public void attachToEntity(final @NotNull String name, final @NotNull Entity entity) {
    final var wt = getWiretap(name);
    if (wt != null)
      wt.setTargetEntity(entity);
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
      this.lastEncoderActivity.keySet().removeIf(k -> k.contains(idStr));
      this.streamEncoders.entrySet().removeIf(e -> {
        if (e.getKey().contains(idStr)) {
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
    this.lastEncoderActivity.keySet().removeIf(k -> k.contains(idStr));
    this.streamEncoders.entrySet().removeIf(e -> {
      if (e.getKey().contains(idStr)) {
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

  @Override
  public @Nullable VoiceRecording startRecording(final @NotNull String name) {
    final var wt = getWiretap(name);
    if (wt == null)
      return null;
    final var rec = wt.startRecording();
    final var recService = DreamVoice.getService(VoiceRecordingService.class);
    if (recService != null)
      recService.register(rec);
    else if (!this.recordingServiceMissingLogged) {
      this.recordingServiceMissingLogged = true;
      this.plugin.getLogger().warning("VoiceRecordingService is unavailable. Wiretap recordings won't be registered globally.");
    }
    return rec;
  }

  @Override
  public @Nullable VoiceRecording stopRecording(final @NotNull String name) {
    final var wt = getWiretap(name);
    if (wt == null)
      return null;
    final var rec = wt.stopRecording();
    if (rec != null) {
      final var recordingsDir = new File(this.plugin.getDataFolder(), "recordings");
      VoiceRecordingPersistence.save(rec, recordingsDir);
    }
    return rec;
  }

  @Override
  public void save() {
    WiretapsPersistence.save(this, new File(this.plugin.getDataFolder(), "data"));
  }

  @Override
  public void load() {
    this.wiretaps.clear();
    WiretapsPersistence.load(this, new File(this.plugin.getDataFolder(), "data"));
  }

  // ###############################################################
  // ------------------- PRIVATE HELPER METHODS --------------------
  // ###############################################################

  private void cleanupIdleChannels() {
    final var now = System.currentTimeMillis();
    this.lastChannelActivity.entrySet().removeIf(entry -> {
      if (now - entry.getValue() > INACTIVITY_TIMEOUT_MS) {
        this.channels.remove(entry.getKey());
        return true;
      }
      return false;
    });
    this.lastEncoderActivity.entrySet().removeIf(entry -> {
      if (now - entry.getValue() > INACTIVITY_TIMEOUT_MS) {
        final var enc = this.streamEncoders.remove(entry.getKey());
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

  private void processSingleWiretapCapture(
    final @NotNull VoiceWiretap wiretap,
    final @NotNull Player senderPlayer,
    final @NotNull UUID senderUuid,
    final byte[] rawOpus,
    final @NotNull VoiceService voiceService,
    final @Nullable VoiceWallService wallService,
    final @Nullable VoiceFilterService filterService
  ) {
    final var wtLoc = wiretap.getLocation();
    final var wtWorld = wtLoc.getWorld();
    if (wtWorld == null || !wtWorld.equals(senderPlayer.getWorld()))
      return;

    final var dist = senderPlayer.getLocation().distance(wtLoc);
    if (dist > wiretap.getDistance())
      return;

    var totalDbLoss = 0.0;
    if (wiretap.isApplyVoiceWall() && wallService != null && wallService.isEnable()) {
      final var ray = VoiceRayCast.check(senderPlayer.getEyeLocation(), wtLoc);
      if (ray.isBlocked())
        totalDbLoss = ray.totalAttenuation();
    }

    if (totalDbLoss >= 99.0)
      return;

    final var distRatio = Math.min(1.0, dist / wiretap.getDistance());
    final var distGain = (float) Math.max(0.05, 1.0 - (distRatio * 0.85));

    try {
      final var encoderKey = wiretap.getUuid() + ":" + senderUuid;
      final var decoder = voiceService.getDecoder(senderUuid);
      final var encoder = this.streamEncoders.computeIfAbsent(encoderKey, _ -> this.api.createEncoder());
      this.lastEncoderActivity.put(encoderKey, System.currentTimeMillis());

      final var pcm = decoder.decode(rawOpus);
      if (pcm == null || pcm.length == 0)
        return;

      var processed = pcm.clone();
      final var filterId = wiretap.getFilterId();
      if (filterId != null && filterService != null && !filterId.equalsIgnoreCase("none")) {
        final var filter = filterService.getFilter(filterId);
        if (filter != null)
          processed = filter.process(processed, null);
      } else if (filterService != null && filterService.hasActiveFilters(senderUuid))
        processed = filterService.applyFilters(senderUuid, processed);

      final var wallGain = (float) Math.pow(10.0, -totalDbLoss / 20.0);
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
      final var finalOpus = encoder.encode(processed);

      if (wiretap.isRecording() && wiretap.getActiveRecording() != null)
        wiretap.getActiveRecording().addAudio(finalOpus);

      broadcastWiretapToListeners(wiretap, senderUuid, finalOpus, wiretap.getListeners());

    } catch (Exception e) {
      this.plugin.getLogger().warning("Failed to process wiretap audio (wiretap=" + wiretap.getName() + ", sender=" + senderUuid + "): " + e.getMessage());
    }
  }

  private void broadcastWiretapToListeners(
    final @NotNull VoiceWiretap wiretap,
    final @NotNull UUID senderUuid,
    final byte[] opusData,
    final @NotNull Set<UUID> listeners
  ) {
    if (listeners.isEmpty())
      return;

    final var now = System.currentTimeMillis();
    for (final var listenerUuid : listeners) {
      if (listenerUuid.equals(senderUuid))
        continue;

      final var conn = this.api.getConnectionOf(listenerUuid);
      if (conn == null)
        continue;

      final var streamKey = wiretap.getUuid() + ":" + senderUuid + ":" + listenerUuid;
      final var ch = this.channels.computeIfAbsent(streamKey, k -> {
        final var channelId = UUID.nameUUIDFromBytes(streamKey.getBytes(StandardCharsets.UTF_8));
        final var sc = this.api.createStaticAudioChannel(channelId);
        if (sc != null) {
          sc.addTarget(conn);
          if (this.volumeCategory != null)
            sc.setCategory(this.volumeCategory.getId());
        }
        return sc;
      });

      if (ch != null) {
        ch.send(opusData);
        this.lastChannelActivity.put(streamKey, now);
      }
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
        this.plugin.getLogger().warning("VoiceService is unavailable. Wiretap audio processing is skipped.");
      }
      return;
    }

    for (final var wiretap : this.wiretaps.values())
      processSingleWiretapCapture(wiretap, senderPlayer, senderUuid, rawOpus, voiceService, wallService, filterService);
  }

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    removeListenerFromAll(event.getPlayer().getUniqueId());
  }

}
