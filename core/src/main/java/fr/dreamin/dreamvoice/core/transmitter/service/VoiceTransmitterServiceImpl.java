package fr.dreamin.dreamvoice.core.transmitter.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.transmitter.model.ReceiverConfig;
import fr.dreamin.dreamvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.utils.audio.AudioLimiter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class VoiceTransmitterServiceImpl implements VoiceTransmitterService, Listener {

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;
  private VolumeCategory volumeCategory;
  private boolean voiceServiceMissingLogged = false;

  private final Map<UUID, Map<UUID, ReceiverConfig>> transmitters = new ConcurrentHashMap<>();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceTransmitterServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;

    Bukkit.getPluginManager().registerEvents(this, plugin);
    Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupIdleChannels, 600L, 600L);
  }

  private void cleanupIdleChannels() {
    final var now = System.currentTimeMillis();
    this.lastChannelActivity.entrySet().removeIf(entry -> {
      if (now - entry.getValue() > 30000L) {
        this.receiverChannels.remove(entry.getKey());
        return true;
      }
      return false;
    });
  }


  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public @NonNull VoicechatServerApi getAPI() {
    return this.api;
  }

  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;

    this.volumeCategory = this.api.volumeCategoryBuilder()
      .setId("trans_volume")
      .setName("Transmitter")
      .setDescription("Transmitter Volume")
      .build();

    this.api.registerVolumeCategory(this.volumeCategory);
  }

  // ------------------------------------------------
  // Transmitter
  // ------------------------------------------------

  @Override
  public boolean isTransmitter(final @NotNull Player player) {
    return isTransmitter(player.getUniqueId());
  }

  @Override
  public boolean isTransmitter(final @NotNull UUID uuid) {
    return this.transmitters.containsKey(uuid);
  }

  @Override
  public void createTransmitter(final @NotNull Player player) {
    createTransmitter(player.getUniqueId());
  }

  @Override
  public void createTransmitter(final @NotNull UUID uuid) {
    this.transmitters.putIfAbsent(uuid, new ConcurrentHashMap<>());
  }

  @Override
  public void removeTransmitter(final @NotNull Player player) {
    removeTransmitter(player.getUniqueId());
  }

  @Override
  public void removeTransmitter(final @NotNull UUID uuid) {
    this.transmitters.remove(uuid);
  }

  // ------------------------------------------------
  // Receivers
  // ------------------------------------------------

  @Override
  public @NotNull Collection<ReceiverConfig> getReceivers(final @NotNull Player player) {
    return getReceivers(player.getUniqueId());
  }

  @Override
  public @NotNull Collection<ReceiverConfig> getReceivers(final @NotNull UUID uuid) {
    return this.transmitters.getOrDefault(uuid, Collections.emptyMap()).values();
  }

  @Override
  public void addReceiver(final @NotNull Player transmitter, final @NotNull Player receiver) {
    addReceiver(transmitter.getUniqueId(), receiver.getUniqueId());
  }

  @Override
  public void addReceiver(final @NotNull UUID transmitter, final @NotNull UUID receiver) {
    this.transmitters.computeIfAbsent(transmitter, k -> new ConcurrentHashMap<>())
      .put(receiver, new ReceiverConfig(receiver));
  }

  @Override
  public void addReceiver(final @NotNull Player transmitter, final @NotNull Player receiver, final double maxDistance) {
    addReceiver(transmitter.getUniqueId(), receiver.getUniqueId(), maxDistance);
  }

  @Override
  public void addReceiver(final @NotNull UUID transmitter, final @NotNull UUID receiver, final double maxDistance) {
    this.transmitters.computeIfAbsent(transmitter, k -> new ConcurrentHashMap<>())
      .put(receiver, new ReceiverConfig(receiver, maxDistance));
  }

  @Override
  public void addReceiver(final @NotNull UUID transmitter, final @NotNull ReceiverConfig receiverConfig) {
    this.transmitters.computeIfAbsent(transmitter, k -> new ConcurrentHashMap<>())
      .put(receiverConfig.getUuid(), receiverConfig);
  }

  @Override
  public void removeReceiver(final @NotNull Player transmitter, final @NotNull Player receiver) {
    removeReceiver(transmitter.getUniqueId(), receiver.getUniqueId());
  }

  @Override
  public void removeReceiver(final @NotNull UUID transmitter, final @NotNull UUID receiver) {
    final var map = this.transmitters.get(transmitter);
    if (map == null)
      return;

    map.remove(receiver);
    if (map.isEmpty())
      this.transmitters.remove(transmitter);
  }

  @Override
  public void clearReceivers(final @NotNull Player transmitter) {
    clearReceivers(transmitter.getUniqueId());
  }

  @Override
  public void clearReceivers(final @NotNull UUID transmitter) {
    final var map = this.transmitters.get(transmitter);
    if (map != null)
      map.clear();
  }

  // ------------------------------------------------
  // Global Operations
  // ------------------------------------------------

  @Override
  public void addReceiverToAll(final @NotNull Player receiver) {
    addReceiverToAll(receiver.getUniqueId());
  }

  @Override
  public void addReceiverToAll(final @NotNull UUID receiver) {
    this.transmitters.forEach((transmitter, map) -> {
      if (transmitter == receiver)
        return;
      map.put(receiver, new ReceiverConfig(receiver));
    });
  }

  @Override
  public void addReceiverToAll(final @NotNull Player receiver, final double maxDistance) {
    addReceiverToAll(receiver.getUniqueId(), maxDistance);
  }

  @Override
  public void addReceiverToAll(final @NotNull UUID receiver, final double maxDistance) {
    this.transmitters.forEach((transmitter, map) -> {
      if (transmitter == receiver)
        return;
      map.put(receiver, new ReceiverConfig(receiver, maxDistance));
    });
  }

  @Override
  public void removeReceiverFromAll(final @NotNull Player receiver) {
    removeReceiverFromAll(receiver.getUniqueId());
  }

  @Override
  public void removeReceiverFromAll(final @NotNull UUID receiver) {
    this.transmitters.forEach((transmitter, map) -> map.remove(receiver));
  }

  private final Map<String, StaticAudioChannel> receiverChannels = new ConcurrentHashMap<>();
  private final Map<String, Long> lastChannelActivity = new ConcurrentHashMap<>();

  // ###############################################################
  // ---------------------- LISTENER METHODS -----------------------
  // ###############################################################

  @EventHandler
  private void onMicrophone(final @NotNull MicrophonePacketEvent event) {
    final var senderConnection = event.getSender();
    if (senderConnection == null)
      return;

    final var senderUuid = senderConnection.getPlayer().getUuid();
    final var receivers = this.transmitters.get(senderUuid);

    if (receivers == null || receivers.isEmpty())
      return;

    final var senderPlayer = Bukkit.getPlayer(senderUuid);
    if (senderPlayer == null)
      return;

    final var senderLocation = senderPlayer.getLocation();

    var opusData = event.getPacket().getOpusEncodedData();
    final var filterService = DreamVoice.getService(VoiceFilterService.class);

    if (filterService != null && filterService.hasActiveFilters(senderUuid)) {
      final var voiceService = DreamVoice.getService(VoiceService.class);
      if (voiceService == null) {
        if (!this.voiceServiceMissingLogged) {
          this.voiceServiceMissingLogged = true;
          this.plugin.getLogger().warning("VoiceService is unavailable. Transmitter filters are skipped.");
        }
      } else try {
        final var decoder = voiceService.getDecoder(senderUuid);
        final var encoder = voiceService.getEncoder(senderUuid);
        if (decoder != null && encoder != null) {
          final var pcm = decoder.decode(opusData);
          if (pcm != null && pcm.length > 0) {
            var filteredPcm = filterService.applyFilters(senderUuid, pcm);
            filteredPcm = AudioLimiter.process(filteredPcm);
            opusData = encoder.encode(filteredPcm);
          }
        }
      } catch (Exception exception) {
        this.plugin.getLogger().warning("Failed to process transmitter voice filters (sender=" + senderUuid + ", receiverCount=" + receivers.size() + "): " + exception.getMessage());
      }
    }

    final var finalOpus = opusData;
    final var now = System.currentTimeMillis();

    for (final var config : receivers.values()) {
      final var receiverPlayer = Bukkit.getPlayer(config.getUuid());
      if (receiverPlayer == null)
        continue;

      if (config.hasMaxDistance()) {
        if (!receiverPlayer.getWorld().equals(senderLocation.getWorld()))
          continue;

        final var maxDist = config.getMaxDistance();
        if (maxDist != null && senderLocation.distanceSquared(receiverPlayer.getLocation()) > maxDist * maxDist)
          continue;
      }

      final var receiverConnection = this.api.getConnectionOf(config.getUuid());
      if (receiverConnection == null)
        continue;

      final var streamKey = senderUuid + ":" + config.getUuid();
      final var staticChannel = this.receiverChannels.computeIfAbsent(streamKey, k -> {
        final var sc = this.api.createStaticAudioChannel(UUID.randomUUID());
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

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    final var uuid = event.getPlayer().getUniqueId();
    this.transmitters.remove(uuid);
    final var uidStr = uuid.toString();
    this.receiverChannels.keySet().removeIf(k -> k.contains(uidStr));
    this.lastChannelActivity.keySet().removeIf(k -> k.contains(uidStr));
    removeReceiverFromAll(uuid);
  }

}
