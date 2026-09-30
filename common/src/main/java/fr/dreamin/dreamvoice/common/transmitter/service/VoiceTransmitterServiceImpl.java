package fr.dreamin.dreamvoice.common.transmitter.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.transmitter.event.TransmitterToggleEvent;
import fr.dreamin.dreamvoice.api.transmitter.model.ReceiverConfig;
import fr.dreamin.dreamvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.transmitter.storage.TransmittersPersistence;
import fr.dreamin.dreamvoice.common.utils.audio.AudioLimiter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform-independent implementation of {@link VoiceTransmitterService} managing point-to-point transmitter routing,
 * per-receiver distance attenuation, and JSON persistence.
 */
public final class VoiceTransmitterServiceImpl implements VoiceTransmitterService {

  private static final long CLEANUP_INTERVAL_TICKS = 600L;
  private static final long INACTIVITY_TIMEOUT_MS = 30000L;
  private static final String CATEGORY_ID = "trans_volume";
  private static final String CATEGORY_NAME = "Transmitter";
  private static final String CATEGORY_DESC = "Transmitter Volume";

  private final @NotNull VoicePlatform platform;
  private @NotNull VoicechatServerApi api;
  private VolumeCategory volumeCategory;
  private boolean voiceServiceMissingLogged = false;

  private final Map<UUID, Map<UUID, ReceiverConfig>> transmitters = new ConcurrentHashMap<>();
  private final Map<String, StaticAudioChannel> receiverChannels = new ConcurrentHashMap<>();
  private final Map<String, Long> lastChannelActivity = new ConcurrentHashMap<>();
  private final Map<UUID, OpusEncoder> transmitterEncoders = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastTransmitterActivity = new ConcurrentHashMap<>();

  public VoiceTransmitterServiceImpl(final @NotNull VoicePlatform platform) {
    this.platform = platform;
    this.platform.runTimer(this::cleanupChannels, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
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
  public boolean isTransmitter(final @NotNull UUID uuid) {
    final var map = this.transmitters.get(uuid);
    return map != null && !map.isEmpty();
  }

  @Override
  public boolean isReceiver(final @NotNull UUID transmitterUuid, final @NotNull UUID receiverUuid) {
    final var map = this.transmitters.get(transmitterUuid);
    return map != null && map.containsKey(receiverUuid);
  }

  @Override
  public void createTransmitter(final @NotNull UUID uuid) {
    setTransmitter(uuid, true);
  }

  @Override
  public void removeTransmitter(final @NotNull UUID uuid) {
    setTransmitter(uuid, false);
  }

  @Override
  public void addReceiver(final @NotNull UUID transmitterUuid, final @NotNull ReceiverConfig config) {
    this.transmitters.computeIfAbsent(transmitterUuid, _ -> new ConcurrentHashMap<>()).put(config.getUuid(), config);
  }

  @Override
  public void addReceiver(final @NotNull UUID transmitter, final @NotNull UUID receiver) {
    addReceiver(transmitter, new ReceiverConfig(receiver, null));
  }

  @Override
  public void addReceiver(final @NotNull UUID transmitter, final @NotNull UUID receiver, final double maxDistance) {
    addReceiver(transmitter, new ReceiverConfig(receiver, maxDistance));
  }

  @Override
  public void addReceiverToAll(final @NotNull UUID receiver) {
    for (final var transmitter : this.transmitters.keySet())
      addReceiver(transmitter, receiver);
  }

  @Override
  public void addReceiverToAll(final @NotNull UUID receiver, final double maxDistance) {
    for (final var transmitter : this.transmitters.keySet())
      addReceiver(transmitter, receiver, maxDistance);
  }

  @Override
  public void removeReceiver(final @NotNull UUID transmitterUuid, final @NotNull UUID receiverUuid) {
    final var map = this.transmitters.get(transmitterUuid);
    if (map != null) {
      map.remove(receiverUuid);
      if (map.isEmpty())
        this.transmitters.remove(transmitterUuid);
    }
  }

  @Override
  public void removeReceiverFromAll(final @NotNull UUID receiverUuid) {
    for (final var entry : this.transmitters.entrySet()) {
      entry.getValue().remove(receiverUuid);
      if (entry.getValue().isEmpty())
        this.transmitters.remove(entry.getKey());
    }
  }

  @Override
  public void clearReceivers(final @NotNull UUID transmitterUuid) {
    this.transmitters.remove(transmitterUuid);
  }

  @Override
  public @NotNull Collection<ReceiverConfig> getReceivers(final @NotNull UUID transmitterUuid) {
    final var map = this.transmitters.get(transmitterUuid);
    if (map == null)
      return Collections.emptyList();
    return Collections.unmodifiableCollection(map.values());
  }

  @Override
  public @NotNull Map<UUID, ReceiverConfig> getReceiverMap(final @NotNull UUID transmitterUuid) {
    final var map = this.transmitters.get(transmitterUuid);
    if (map == null)
      return Collections.emptyMap();
    return Collections.unmodifiableMap(map);
  }

  @Override
  public @NotNull Map<UUID, Map<UUID, ReceiverConfig>> getTransmitters() {
    return Collections.unmodifiableMap(this.transmitters);
  }

  @Override
  public void clearTransmitters() {
    this.transmitters.clear();
    this.receiverChannels.clear();
    this.lastChannelActivity.clear();
    this.transmitterEncoders.values().forEach(enc -> {
      if (!enc.isClosed()) {
        try {
          enc.close();
        } catch (Throwable ignored) {}
      }
    });
    this.transmitterEncoders.clear();
  }

  @Override
  public boolean toggleTransmitter(final @NotNull UUID playerUuid) {
    final var currentlyActive = isTransmitter(playerUuid);
    final var newActive = !currentlyActive;

    if (!newActive)
      clearReceivers(playerUuid);

    new TransmitterToggleEvent(playerUuid, newActive).callEvent();
    return newActive;
  }

  @Override
  public void setTransmitter(final @NotNull UUID playerUuid, final boolean active) {
    final var currentlyActive = isTransmitter(playerUuid);
    if (currentlyActive == active)
      return;

    if (!active)
      clearReceivers(playerUuid);

    new TransmitterToggleEvent(playerUuid, active).callEvent();
  }

  @Override
  public void save() {
    final var moduleDir = new File(this.platform.getDataDirectory().toFile(), "modules/transmitter");
    TransmittersPersistence.save(this, moduleDir, this.platform);
  }

  @Override
  public void load() {
    final var moduleDir = new File(this.platform.getDataDirectory().toFile(), "modules/transmitter");
    clearTransmitters();
    TransmittersPersistence.load(this, moduleDir, this.platform);
  }

  private void cleanupChannels() {
    final var now = System.currentTimeMillis();
    this.lastChannelActivity.entrySet().removeIf(entry -> {
      if (now - entry.getValue() > INACTIVITY_TIMEOUT_MS) {
        this.receiverChannels.remove(entry.getKey());
        return true;
      }
      return false;
    });
    this.lastTransmitterActivity.entrySet().removeIf(entry -> {
      if (now - entry.getValue() > INACTIVITY_TIMEOUT_MS) {
        final var enc = this.transmitterEncoders.remove(entry.getKey());
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

  private byte[] filterTransmitterAudio(
    final @NotNull UUID senderUuid,
    final byte[] opusData,
    final @NotNull VoiceFilterService filterService
  ) {
    final var common = DreamVoiceCommon.getInstance();
    final var voiceService = common != null ? common.getService(VoiceService.class) : null;
    if (voiceService == null) {
      if (!this.voiceServiceMissingLogged) {
        this.voiceServiceMissingLogged = true;
        this.platform.logWarning("VoiceService is unavailable. Transmitter filters are skipped.");
      }
      return opusData;
    }

    try {
      final var decoder = voiceService.getDecoder(senderUuid);
      final var encoder = this.transmitterEncoders.computeIfAbsent(senderUuid, _ -> this.api.createEncoder());
      this.lastTransmitterActivity.put(senderUuid, System.currentTimeMillis());
      if (decoder != null) {
        final var pcm = decoder.decode(opusData);
        if (pcm != null && pcm.length > 0) {
          var filteredPcm = filterService.applyFilters(senderUuid, pcm);
          filteredPcm = AudioLimiter.process(filteredPcm);
          return encoder.encode(filteredPcm);
        }
      }
    } catch (Exception exception) {
      this.platform.logWarning("Failed to process transmitter voice filters (sender=" + senderUuid + "): " + exception.getMessage());
    }
    return opusData;
  }

  public void onMicrophone(final @NotNull MicrophonePacketEvent event) {
    final var senderConnection = event.getSenderConnection();
    if (senderConnection == null)
      return;

    final var senderUuid = senderConnection.getPlayer().getUuid();
    final var receivers = this.transmitters.get(senderUuid);
    if (receivers == null || receivers.isEmpty())
      return;

    final var senderLocation = this.platform.getPlayerLocation(senderUuid).orElse(null);
    if (senderLocation == null)
      return;

    var opusData = event.getPacket().getOpusEncodedData();
    final var common = DreamVoiceCommon.getInstance();
    final var filterService = common != null ? common.getService(VoiceFilterService.class) : null;

    if (filterService != null && filterService.hasActiveFilters(senderUuid))
      opusData = filterTransmitterAudio(senderUuid, opusData, filterService);

    final var finalOpus = opusData;
    final var now = System.currentTimeMillis();

    for (final var config : receivers.values()) {
      if (!this.platform.isPlayerOnline(config.getUuid()))
        continue;

      if (config.hasMaxDistance()) {
        final var receiverLoc = this.platform.getPlayerLocation(config.getUuid()).orElse(null);
        if (receiverLoc == null || !receiverLoc.world().equals(senderLocation.world()))
          continue;

        final var maxDist = config.getMaxDistance();
        if (maxDist != null && senderLocation.distance(receiverLoc) > maxDist)
          continue;
      }

      final var receiverConnection = this.api.getConnectionOf(config.getUuid());
      if (receiverConnection == null)
        continue;

      final var streamKey = senderUuid + ":" + config.getUuid();
      final var staticChannel = this.receiverChannels.computeIfAbsent(streamKey, k -> {
        final var channelId = UUID.nameUUIDFromBytes(streamKey.getBytes(StandardCharsets.UTF_8));
        final var sc = this.api.createStaticAudioChannel(channelId);
        if (sc != null) {
          sc.addTarget(receiverConnection);
          if (this.volumeCategory != null)
            sc.setCategory(this.volumeCategory.getId());
        }
        return sc;
      });

      if (staticChannel != null) {
        staticChannel.send(finalOpus);
        this.lastChannelActivity.put(streamKey, now);
      }
    }
  }

  public void onPlayerDisconnect(final @NotNull UUID uuid) {
    this.transmitters.remove(uuid);
    final var uidStr = uuid.toString();
    this.receiverChannels.keySet().removeIf(k -> k.contains(uidStr));
    this.lastChannelActivity.keySet().removeIf(k -> k.contains(uidStr));
    this.lastTransmitterActivity.remove(uuid);
    final var enc = this.transmitterEncoders.remove(uuid);
    if (enc != null && !enc.isClosed()) {
      try {
        enc.close();
      } catch (Throwable ignored) {}
    }
    removeReceiverFromAll(uuid);
  }

}
