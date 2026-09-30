package fr.dreamin.dreamvoice.common.wiretap.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.api.wiretap.event.WiretapRegisterEvent;
import fr.dreamin.dreamvoice.api.wiretap.event.WiretapRemoveEvent;
import fr.dreamin.dreamvoice.api.wiretap.event.WiretapSubscribeEvent;
import fr.dreamin.dreamvoice.api.wiretap.event.WiretapUnsubscribeEvent;
import fr.dreamin.dreamvoice.api.wiretap.model.VoiceWiretap;
import fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.recording.storage.VoiceRecordingPersistence;
import fr.dreamin.dreamvoice.common.utils.audio.AudioLimiter;
import fr.dreamin.dreamvoice.common.wiretap.storage.WiretapsPersistence;
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
 * Platform-independent implementation of {@link VoiceWiretapService} managing spy microphones,
 * mobile bug tracking, covert eavesdropping streams, and direct cassette recording.
 */
public final class VoiceWiretapServiceImpl implements VoiceWiretapService {

  private static final long CLEANUP_INTERVAL_TICKS = 600L;
  private static final long INACTIVITY_TIMEOUT_MS = 30000L;
  private static final String CATEGORY_ID = "wiretap_vol";
  private static final String CATEGORY_NAME = "Wiretap";
  private static final String CATEGORY_DESC = "Volume for wiretap audio monitoring and surveillance";

  private final @NotNull VoicePlatform platform;
  private @NotNull VoicechatServerApi api;
  private VolumeCategory volumeCategory;

  private final Map<UUID, VoiceWiretap> wiretaps = new ConcurrentHashMap<>();
  private final Map<String, VoiceWiretap> wiretapsByName = new ConcurrentHashMap<>();
  private final Map<String, StaticAudioChannel> channels = new ConcurrentHashMap<>();
  private final Map<String, Long> lastChannelActivity = new ConcurrentHashMap<>();
  private final Map<String, OpusEncoder> streamEncoders = new ConcurrentHashMap<>();
  private final Map<String, Long> lastEncoderActivity = new ConcurrentHashMap<>();

  private boolean voiceServiceMissingLogged = false;

  public VoiceWiretapServiceImpl(final @NotNull VoicePlatform platform) {
    this.platform = platform;
    this.platform.runTimer(this::cleanupIdleChannels, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
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
  public @NotNull Collection<VoiceWiretap> getWiretaps() {
    return Collections.unmodifiableCollection(this.wiretaps.values());
  }

  @Override
  public @Nullable VoiceWiretap getWiretap(final @NotNull UUID uuid) {
    return this.wiretaps.get(uuid);
  }

  @Override
  public @Nullable VoiceWiretap getWiretap(final @NotNull String name) {
    return this.wiretapsByName.get(name.toLowerCase());
  }

  @Override
  public @NotNull VoiceWiretap createWiretap(final @NotNull String name, final @NotNull VoiceLocation location) {
    return createWiretap(name, location, null);
  }

  @Override
  public @NotNull VoiceWiretap createWiretap(final @NotNull String name, final @NotNull VoiceLocation location, final @Nullable UUID targetEntityUuid) {
    final var wt = new VoiceWiretap(name, location, targetEntityUuid);
    register(wt);
    return wt;
  }

  @Override
  public void register(final @NotNull VoiceWiretap wiretap) {
    this.wiretaps.put(wiretap.getUuid(), wiretap);
    this.wiretapsByName.put(wiretap.getName().toLowerCase(), wiretap);
    new WiretapRegisterEvent(wiretap).callEvent();
  }

  @Override
  public void unregister(final @NotNull UUID uuid) {
    final var wt = this.wiretaps.remove(uuid);
    if (wt != null) {
      this.wiretapsByName.remove(wt.getName().toLowerCase());
      if (wt.isRecording())
        stopRecording(wt.getName());
      new WiretapRemoveEvent(wt).callEvent();
    }
  }

  @Override
  public void unregister(final @NotNull String name) {
    final var wt = this.wiretapsByName.remove(name.toLowerCase());
    if (wt != null) {
      this.wiretaps.remove(wt.getUuid());
      if (wt.isRecording())
        stopRecording(wt.getName());
      new WiretapRemoveEvent(wt).callEvent();
    }
  }

  @Override
  public void attachToEntity(final @NotNull String name, final @NotNull UUID targetEntityUuid) {
    final var wt = getWiretap(name);
    if (wt != null)
      wt.setTargetEntityUuid(targetEntityUuid);
  }

  @Override
  public void detachFromEntity(final @NotNull String name) {
    final var wt = getWiretap(name);
    if (wt != null)
      wt.setTargetEntityUuid(null);
  }

  @Override
  public boolean addListener(final @NotNull String name, final @NotNull UUID playerUuid) {
    final var wt = getWiretap(name);
    if (wt == null)
      return false;

    wt.addListener(playerUuid);
    new WiretapSubscribeEvent(wt, playerUuid).callEvent();
    return true;
  }

  @Override
  public boolean removeListener(final @NotNull String name, final @NotNull UUID playerUuid) {
    final var wt = getWiretap(name);
    if (wt == null)
      return false;

    wt.removeListener(playerUuid);
    new WiretapUnsubscribeEvent(wt, playerUuid).callEvent();
    return true;
  }

  @Override
  public void clearListeners(final @NotNull String name) {
    final var wt = getWiretap(name);
    if (wt != null)
      wt.getListeners().clear();
  }

  @Override
  public void removeListenerFromAll(final @NotNull UUID playerUuid) {
    for (final var wt : this.wiretaps.values()) {
      if (wt.hasListener(playerUuid)) {
        wt.removeListener(playerUuid);
        new WiretapUnsubscribeEvent(wt, playerUuid).callEvent();
      }
    }
  }

  @Override
  public @Nullable VoiceRecording startRecording(final @NotNull String name) {
    final var wt = getWiretap(name);
    if (wt == null)
      return null;
    return wt.startRecording();
  }

  @Override
  public @Nullable VoiceRecording stopRecording(final @NotNull String name) {
    final var wt = getWiretap(name);
    if (wt == null)
      return null;
    final var rec = wt.stopRecording();
    if (rec != null) {
      final var recordingsDir = new File(this.platform.getDataDirectory().toFile(), "recordings");
      this.platform.runAsync(() -> VoiceRecordingPersistence.save(rec, recordingsDir));
    }
    return rec;
  }

  @Override
  public void save() {
    final var moduleDir = new File(this.platform.getDataDirectory().toFile(), "modules/wiretap");
    WiretapsPersistence.save(this, moduleDir, this.platform);
  }

  @Override
  public void load() {
    this.wiretaps.clear();
    this.wiretapsByName.clear();
    final var moduleDir = new File(this.platform.getDataDirectory().toFile(), "modules/wiretap");
    WiretapsPersistence.load(this, moduleDir, this.platform);
  }

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
    final @NotNull VoiceLocation emissionLoc,
    final @NotNull UUID senderUuid,
    final byte[] rawOpus,
    final @Nullable String extraFilterId,
    final @NotNull VoiceService voiceService,
    final @Nullable VoiceWallService wallService,
    final @Nullable VoiceFilterService filterService
  ) {
    final var wtLoc = wiretap.getLocation();
    if (!wtLoc.world().equals(emissionLoc.world()))
      return;

    final var dist = emissionLoc.distance(wtLoc);
    if (dist > wiretap.getDistance())
      return;

    var totalDbLoss = 0.0;
    if (wiretap.isApplyVoiceWall() && wallService != null && wallService.isEnable()) {
      if (!this.platform.hasLineOfSight(emissionLoc, wtLoc))
        totalDbLoss = this.platform.computeAcousticOcclusion(emissionLoc, wtLoc, null);
    }

    if (totalDbLoss >= 99.0)
      return;

    final var distRatio = Math.min(1.0, dist / wiretap.getDistance());
    final var distGain = (float) Math.max(0.05, 1.0 - (distRatio * 0.85));

    try {
      final var encoderKey = wiretap.getUuid() + ":" + senderUuid;
      final var decoder = voiceService.getDecoder(senderUuid);
      final var encoder = this.streamEncoders.computeIfAbsent(encoderKey, _ -> this.api.createEncoder());
      final var now = System.currentTimeMillis();
      final var lastTime = this.lastEncoderActivity.put(encoderKey, now);
      if (lastTime != null && (now - lastTime > 400L))
        encoder.resetState();

      final var pcm = decoder.decode(rawOpus);
      if (pcm == null || pcm.length == 0)
        return;

      var processed = pcm.clone();
      final var filterId = wiretap.getFilterId();
      if (filterId != null && filterService != null && !filterId.equalsIgnoreCase("none")) {
        final var filter = filterService.getFilter(filterId);
        if (filter != null)
          processed = filter.process(processed, null);
      } else if (extraFilterId != null && filterService != null && !extraFilterId.equalsIgnoreCase("none")) {
        final var filter = filterService.getFilter(extraFilterId);
        if (filter != null)
          processed = filter.process(processed, null);
      } else if (filterService != null && filterService.hasActiveFilters(senderUuid))
        processed = filterService.applyFilters(senderUuid, processed);

      final var wallGain = (float) Math.pow(10.0, -totalDbLoss / 20.0);
      final var combinedGain = wallGain * distGain;
      for (int i = 0; i < processed.length; i++)
        processed[i] = (short) Math.clamp(Math.round(processed[i] * combinedGain), Short.MIN_VALUE, Short.MAX_VALUE);

      processed = AudioLimiter.process(processed);
      final var finalOpus = encoder.encode(processed);

      if (wiretap.isRecording() && wiretap.getActiveRecording() != null)
        wiretap.getActiveRecording().addAudio(finalOpus);

      broadcastWiretapToListeners(wiretap, senderUuid, finalOpus, wiretap.getListeners());

    } catch (Exception e) {
      this.platform.logWarning("Error capturing wiretap audio (wiretap=" + wiretap.getName() + "): " + e.getMessage());
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
      final var listenerConn = this.api.getConnectionOf(listenerUuid);
      if (listenerConn == null)
        continue;

      final var streamKey = wiretap.getUuid() + ":" + senderUuid + ":" + listenerUuid;
      var ch = this.channels.get(streamKey);
      if (ch == null || ch.isClosed()) {
        final var channelUuid = UUID.nameUUIDFromBytes(streamKey.getBytes(StandardCharsets.UTF_8));
        ch = this.api.createStaticAudioChannel(channelUuid);
        if (ch != null) {
          ch.addTarget(listenerConn);
          if (this.volumeCategory != null)
            ch.setCategory(this.volumeCategory.getId());
          this.channels.put(streamKey, ch);
        }
      }

      if (ch != null) {
        ch.send(opusData);
        this.lastChannelActivity.put(streamKey, now);
      }
    }
  }

  public void onMicrophone(final @NotNull MicrophonePacketEvent event) {
    if (this.wiretaps.isEmpty())
      return;

    final var sender = event.getSenderConnection();
    if (sender == null)
      return;

    final var senderUuid = sender.getPlayer().getUuid();
    final var emissionLoc = this.platform.getPlayerEyeLocation(senderUuid).orElse(null);
    if (emissionLoc == null)
      return;

    final var rawOpus = event.getPacket().getOpusEncodedData();
    if (rawOpus == null || rawOpus.length == 0)
      return;

    final var common = DreamVoiceCommon.getInstance();
    final var voiceService = common != null ? common.getService(VoiceService.class) : null;
    final var wallService = common != null ? common.getService(VoiceWallService.class) : null;
    final var filterService = common != null ? common.getService(VoiceFilterService.class) : null;
    if (voiceService == null) {
      if (!this.voiceServiceMissingLogged) {
        this.voiceServiceMissingLogged = true;
        this.platform.logWarning("VoiceService is unavailable. Wiretap audio processing is skipped.");
      }
      return;
    }

    final var projectionService = common.getService(VoiceProjectionService.class);
    VoiceLocation anchorLoc = null;
    if (projectionService != null) {
      final var proj = projectionService.getProjection(senderUuid);
      if (proj != null && proj.isEmitVoiceAtAnchor())
        anchorLoc = proj.getAnchorLocation();
    }

    for (final var wt : this.wiretaps.values()) {
      if (!wt.isEnabled())
        continue;

      processSingleWiretapCapture(wt, emissionLoc, senderUuid, rawOpus, null, voiceService, wallService, filterService);

      if (anchorLoc != null)
        processSingleWiretapCapture(wt, anchorLoc, senderUuid, rawOpus, null, voiceService, wallService, filterService);
    }
  }

  public void onPlayerDisconnect(final @NotNull UUID uuid) {
    for (final var wt : this.wiretaps.values())
      wt.removeListener(uuid);

    final var uidStr = uuid.toString();
    this.channels.keySet().removeIf(k -> k.contains(uidStr));
    this.lastChannelActivity.keySet().removeIf(k -> k.contains(uidStr));
    this.streamEncoders.entrySet().removeIf(e -> {
      if (e.getKey().contains(uidStr)) {
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
