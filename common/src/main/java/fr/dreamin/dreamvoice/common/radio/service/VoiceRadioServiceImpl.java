package fr.dreamin.dreamvoice.common.radio.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.radio.event.RadioChannelCreateEvent;
import fr.dreamin.dreamvoice.api.radio.event.RadioChannelJoinEvent;
import fr.dreamin.dreamvoice.api.radio.event.RadioChannelLeaveEvent;
import fr.dreamin.dreamvoice.api.radio.model.RadioChannel;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.radio.storage.RadiosPersistence;
import fr.dreamin.dreamvoice.common.utils.RawUtils;
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
 * Platform-independent implementation of {@link VoiceRadioService} managing walkie-talkie radio frequencies,
 * automatic Roger Beep tone generation, DSP radio filtering, and multi-user packet routing.
 */
public final class VoiceRadioServiceImpl implements VoiceRadioService {

  private static final long ROGER_BEEP_CHECK_TICKS = 2L;
  private static final long CLEANUP_INTERVAL_TICKS = 600L;
  private static final long INACTIVITY_TIMEOUT_MS = 30000L;
  private static final long ROGER_BEEP_SILENCE_THRESHOLD_MS = 350L;

  private final @NotNull VoicePlatform platform;
  private @NotNull VoicechatServerApi api;

  private final Map<String, RadioChannel> channels = new ConcurrentHashMap<>();
  private final Map<UUID, String> playerChannels = new ConcurrentHashMap<>();

  private final Map<String, StaticAudioChannel> radioChannels = new ConcurrentHashMap<>();
  private final Map<String, Long> lastChannelActivity = new ConcurrentHashMap<>();
  private final Map<String, Long> lastChannelActivityTimes = new ConcurrentHashMap<>();
  private final java.util.List<java.util.function.BiConsumer<String, UUID>> transmissionListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
  private final java.util.List<RadioAudioTransmissionListener> audioTransmissionListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
  private final Map<UUID, Long> lastSpeakingTimes = new ConcurrentHashMap<>();
  private final Map<UUID, OpusEncoder> radioEncoders = new ConcurrentHashMap<>();
  private final Map<String, fr.dreamin.dreamvoice.api.recording.model.VoiceRecording> activeRadioRecordings = new ConcurrentHashMap<>();
  private final Map<String, fr.dreamin.dreamvoice.common.recording.player.OpusAudioPlayer> activeRadioPlaybacks = new ConcurrentHashMap<>();
  private volatile RadioPacketFilter packetFilter = null;
  private boolean voiceServiceMissingLogged = false;

  public VoiceRadioServiceImpl(final @NotNull VoicePlatform platform) {
    this.platform = platform;
    this.platform.runTimer(this::checkRogerBeeps, ROGER_BEEP_CHECK_TICKS, ROGER_BEEP_CHECK_TICKS);
    this.platform.runTimer(this::cleanupChannels, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
  }

  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;
  }

  @Override
  public VoicechatServerApi getAPI() {
    return this.api;
  }

  @Override
  public Collection<RadioChannel> getChannels() {
    return Collections.unmodifiableCollection(this.channels.values());
  }

  @Override
  public @Nullable RadioChannel getChannel(final @NotNull String name) {
    return this.channels.get(name.toLowerCase());
  }

  @Override
  public @NotNull RadioChannel getOrCreateChannel(final @NotNull String name) {
    var channel = getChannel(name);
    if (channel == null) {
      channel = new RadioChannel(name);
      register(channel);
    }
    return channel;
  }

  @Override
  public @Nullable RadioChannel getChannelOfPlayer(final @NotNull UUID playerUuid) {
    final var channelName = this.playerChannels.get(playerUuid);
    if (channelName == null)
      return null;

    return getChannel(channelName);
  }

  @Override
  public void register(final @NotNull RadioChannel radioChannel) {
    this.channels.put(radioChannel.getName().toLowerCase(), radioChannel);
    new RadioChannelCreateEvent(radioChannel).callEvent();
  }

  @Override
  public void unregister(final @NotNull String name) {
    final var channel = this.channels.remove(name.toLowerCase());
    if (channel != null) {
      for (final var member : channel.getMembers())
        this.playerChannels.remove(member);
    }
  }

  @Override
  public boolean joinChannel(final @NotNull UUID playerUuid, final @NotNull String channelName) {
    final var channel = getChannel(channelName);
    if (channel == null)
      return false;

    leaveChannel(playerUuid);

    channel.addMember(playerUuid);
    this.playerChannels.put(playerUuid, channel.getName().toLowerCase());

    new RadioChannelJoinEvent(channel, playerUuid).callEvent();
    return true;
  }

  @Override
  public void leaveChannel(final @NotNull UUID playerUuid) {
    final var current = getChannelOfPlayer(playerUuid);
    if (current != null) {
      current.removeMember(playerUuid);
      this.playerChannels.remove(playerUuid);
      new RadioChannelLeaveEvent(current, playerUuid).callEvent();
    }
  }

  @Override
  public boolean isInChannel(final @NotNull UUID playerUuid) {
    return this.playerChannels.containsKey(playerUuid);
  }

  @Override
  public void unregisterAll() {
    this.channels.clear();
    this.playerChannels.clear();
    this.radioChannels.clear();
    this.lastChannelActivity.clear();
    this.lastSpeakingTimes.clear();
    this.radioEncoders.values().forEach(enc -> {
      if (!enc.isClosed()) {
        try {
          enc.close();
        } catch (Throwable ignored) {}
      }
    });
    this.radioEncoders.clear();
  }

  @Override
  public void save() {
    final var moduleDir = new File(this.platform.getDataDirectory().toFile(), "modules/radio");
    RadiosPersistence.save(this, moduleDir, this.platform);
  }

  @Override
  public void load() {
    final var moduleDir = new File(this.platform.getDataDirectory().toFile(), "modules/radio");
    RadiosPersistence.load(this, moduleDir, this.platform);
  }

  @Override
  public void setPacketFilter(final @Nullable RadioPacketFilter filter) {
    this.packetFilter = filter;
  }

  @Override
  public @Nullable RadioPacketFilter getPacketFilter() {
    return this.packetFilter;
  }

  private void cleanupChannels() {
    final var now = System.currentTimeMillis();
    this.lastChannelActivity.entrySet().removeIf(entry -> {
      if (now - entry.getValue() > INACTIVITY_TIMEOUT_MS) {
        this.radioChannels.remove(entry.getKey());
        return true;
      }
      return false;
    });
  }

  private void checkRogerBeeps() {
    if (this.lastSpeakingTimes.isEmpty())
      return;

    final var now = System.currentTimeMillis();
    final var iterator = this.lastSpeakingTimes.entrySet().iterator();

    while (iterator.hasNext()) {
      final var entry = iterator.next();
      final var senderUuid = entry.getKey();
      final var lastSpoke = entry.getValue();

      if (now - lastSpoke >= ROGER_BEEP_SILENCE_THRESHOLD_MS) {
        iterator.remove();

        final var channel = getChannelOfPlayer(senderUuid);
        if (channel != null && channel.isRogerBeep())
          playRogerBeepToChannel(channel, senderUuid);
      }
    }
  }

  private void playRogerBeepToChannel(final @NotNull RadioChannel channel, final @NotNull UUID senderUuid) {
    try {
      final var beep1 = RawUtils.generateBeep(2400, 45);
      final var beep2 = RawUtils.generateBeep(1800, 55);
      final var combined = new byte[beep1.length + beep2.length];
      System.arraycopy(beep1, 0, combined, 0, beep1.length);
      System.arraycopy(beep2, 0, combined, beep1.length, beep2.length);

      final var pcm = RawUtils.bytesToShorts(combined);
      final var encoder = this.api.createEncoder();
      final var opus = encoder.encode(pcm);
      if (!encoder.isClosed()) {
        try {
          encoder.close();
        } catch (Throwable ignored) {}
      }

      for (final var memberUuid : channel.getMembers()) {
        if (memberUuid.equals(senderUuid))
          continue;

        final var conn = this.api.getConnectionOf(memberUuid);
        if (conn == null)
          continue;

        final var streamKey = senderUuid + ":" + memberUuid;
        final var staticChannel = this.radioChannels.computeIfAbsent(streamKey, k -> {
          final var channelId = UUID.nameUUIDFromBytes(streamKey.getBytes(StandardCharsets.UTF_8));
          final var sc = this.api.createStaticAudioChannel(channelId);
          if (sc != null)
            sc.addTarget(conn);
          return sc;
        });

        if (staticChannel != null)
          staticChannel.send(opus);
      }
    } catch (Exception exception) {
      this.platform.logWarning("Failed to send Roger beep for radio channel '" + channel.getName() + "' (sender=" + senderUuid + "): " + exception.getMessage());
    }
  }

  private byte[] processRadioAudio(
    final @NotNull UUID senderUuid,
    final @NotNull RadioChannel channel,
    final byte[] opusData,
    final @NotNull VoiceService voiceService,
    final @Nullable VoiceFilterService filterService
  ) {
    final var filterId = channel.getFilterId();
    if (filterService == null || filterId == null || filterId.equalsIgnoreCase("none"))
      return opusData;

    try {
      final var decoder = voiceService.getDecoder(senderUuid);
      final var encoder = this.radioEncoders.computeIfAbsent(senderUuid, _ -> this.api.createEncoder());
      final var pcm = decoder.decode(opusData);
      if (pcm == null || pcm.length == 0)
        return opusData;

      var processed = pcm;
      final var filter = filterService.getFilter(filterId);
      if (filter != null)
        processed = filter.process(processed, null);

      processed = AudioLimiter.process(processed);
      return encoder.encode(processed);
    } catch (Exception exception) {
      this.platform.logWarning("Failed to process radio voice packet for channel '" + channel.getName() + "' (sender=" + senderUuid + "): " + exception.getMessage());
      return opusData;
    }
  }

  public void onMicrophone(final @NotNull MicrophonePacketEvent event) {
    final var sender = event.getSenderConnection();
    if (sender == null)
      return;

    final var senderUuid = sender.getPlayer().getUuid();
    final var channel = getChannelOfPlayer(senderUuid);
    if (channel == null)
      return;

    final var filter = this.packetFilter;
    if (filter != null && !filter.shouldTransmit(channel, senderUuid))
      return;

    final var now = System.currentTimeMillis();
    this.lastSpeakingTimes.put(senderUuid, now);
    this.lastChannelActivityTimes.put(channel.getName().toLowerCase(), now);
    for (final var l : this.transmissionListeners)
      l.accept(channel.getName(), senderUuid);

    var opusData = event.getPacket().getOpusEncodedData();
    final var common = DreamVoiceCommon.getInstance();
    final var filterService = common != null ? common.getService(VoiceFilterService.class) : null;
    final var voiceService = common != null ? common.getService(VoiceService.class) : null;

    if (voiceService != null)
      opusData = processRadioAudio(senderUuid, channel, opusData, voiceService, filterService);
    else if (!this.voiceServiceMissingLogged) {
      this.voiceServiceMissingLogged = true;
      this.platform.logWarning("VoiceService is unavailable. Radio packets will be forwarded without processing.");
    }

    final var finalOpus = opusData;

    final var activeRec = this.activeRadioRecordings.get(channel.getName().toLowerCase());
    if (activeRec != null && activeRec.isRecording())
      activeRec.addAudio(senderUuid, finalOpus);

    new fr.dreamin.dreamvoice.api.radio.event.RadioTransmissionEvent(channel, senderUuid, finalOpus).callEvent();

    for (final var l : this.audioTransmissionListeners)
      l.onAudioTransmit(channel.getName(), senderUuid, finalOpus);

    final var members = channel.getMembers();
    if (members.size() <= 1)
      return;

    for (final var memberUuid : members) {
      if (memberUuid.equals(senderUuid))
        continue;

      if (filter != null && !filter.shouldDeliver(channel, senderUuid, memberUuid))
        continue;

      final var conn = this.api.getConnectionOf(memberUuid);
      if (conn == null)
        continue;

      final var streamKey = senderUuid + ":" + memberUuid;
      final var staticChannel = this.radioChannels.computeIfAbsent(streamKey, k -> {
        final var channelId = UUID.nameUUIDFromBytes(streamKey.getBytes(StandardCharsets.UTF_8));
        final var sc = this.api.createStaticAudioChannel(channelId);
        if (sc != null)
          sc.addTarget(conn);
        return sc;
      });

      if (staticChannel != null) {
        staticChannel.send(finalOpus);
        this.lastChannelActivity.put(streamKey, now);
      }
    }
  }

  public void onPlayerDisconnect(final @NotNull UUID uuid) {
    leaveChannel(uuid);
    final var uidStr = uuid.toString();
    this.radioChannels.keySet().removeIf(k -> k.contains(uidStr));
    this.lastChannelActivity.keySet().removeIf(k -> k.contains(uidStr));
    this.lastSpeakingTimes.remove(uuid);
    final var enc = this.radioEncoders.remove(uuid);
    if (enc != null && !enc.isClosed()) {
      try {
        enc.close();
      } catch (Throwable ignored) {}
    }
  }

  @Override
  public void addTransmissionListener(final @NotNull java.util.function.BiConsumer<String, UUID> listener) {
    this.transmissionListeners.add(listener);
  }

  @Override
  public void addAudioTransmissionListener(final @NotNull RadioAudioTransmissionListener listener) {
    this.audioTransmissionListeners.add(listener);
  }

  @Override
  public boolean isChannelActive(final @NotNull String channelName) {
    final var last = this.lastChannelActivityTimes.get(channelName.toLowerCase());
    return last != null && (System.currentTimeMillis() - last < 500L);
  }

  @Override
  public @Nullable fr.dreamin.dreamvoice.api.recording.model.VoiceRecording startRecording(final @NotNull String channelName) {
    final var key = channelName.toLowerCase();
    final var existing = this.activeRadioRecordings.get(key);
    if (existing != null && existing.isRecording())
      return existing;

    final var recUuid = UUID.randomUUID();
    final var rec = new fr.dreamin.dreamvoice.api.recording.model.VoiceRecording(recUuid);
    rec.start();
    this.activeRadioRecordings.put(key, rec);

    final var common = DreamVoiceCommon.getInstance();
    if (common != null) {
      final var recService = common.getService(fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService.class);
      if (recService != null)
        recService.register(rec);
    }
    return rec;
  }

  @Override
  public @Nullable fr.dreamin.dreamvoice.api.recording.model.VoiceRecording stopRecording(final @NotNull String channelName) {
    final var key = channelName.toLowerCase();
    final var rec = this.activeRadioRecordings.remove(key);
    if (rec == null)
      return null;

    if (rec.isRecording()) {
      rec.stop();
      new fr.dreamin.dreamvoice.api.recording.event.VoiceRecordingStopEvent(rec).callEvent();
      final var common = DreamVoiceCommon.getInstance();
      if (common != null) {
        final var recService = common.getService(fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService.class);
        if (recService != null) {
          recService.register(rec);
          final var dir = new File(this.platform.getDataDirectory().toFile(), "recordings");
          this.platform.runAsync(() -> fr.dreamin.dreamvoice.common.recording.storage.VoiceRecordingPersistence.save(rec, dir));
        }
      }
    }
    return rec;
  }

  @Override
  public boolean isRecording(final @NotNull String channelName) {
    final var rec = this.activeRadioRecordings.get(channelName.toLowerCase());
    return rec != null && rec.isRecording();
  }

  @Override
  public @Nullable fr.dreamin.dreamvoice.api.recording.model.VoiceRecording getActiveRecording(final @NotNull String channelName) {
    return this.activeRadioRecordings.get(channelName.toLowerCase());
  }

  @Override
  public void playRecordingToRadio(final @NotNull String channelName, final @NotNull fr.dreamin.dreamvoice.api.recording.model.VoiceRecording recording) {
    final var key = channelName.toLowerCase();
    stopRadioPlayback(key);

    final var frames = recording.getAudioFrames();
    if (frames.isEmpty())
      return;

    final var totalDurationMs = (long) (recording.getDurationSeconds() * 1000L);
    final var virtualSpeaker = UUID.nameUUIDFromBytes(("radio_playback:" + key).getBytes(StandardCharsets.UTF_8));

    final var player = new fr.dreamin.dreamvoice.common.recording.player.OpusAudioPlayer(
      java.util.Collections.emptyList(),
      frames,
      totalDurationMs,
      false,
      frame -> {
        final long now = System.currentTimeMillis();
        this.lastChannelActivityTimes.put(key, now);

        final var data = frame.data();
        if (data == null || data.length == 0)
          return;

        for (final var l : this.audioTransmissionListeners)
          l.onAudioTransmit(channelName, virtualSpeaker, data);

        if (this.api == null)
          return;

        final var channel = getOrCreateChannel(key);
        final var members = channel.getMembers();
        if (members.isEmpty())
          return;

        for (final var memberUuid : members) {
          final var filter = this.packetFilter;
          if (filter != null && !filter.shouldDeliver(channel, virtualSpeaker, memberUuid))
            continue;

          final var conn = this.api.getConnectionOf(memberUuid);
          if (conn == null)
            continue;

          final var streamKey = "radio_playback:" + key + ":" + memberUuid;
          this.lastChannelActivity.put(streamKey, now);
          final var staticChannel = this.radioChannels.computeIfAbsent(streamKey, k -> {
            final var channelId = UUID.nameUUIDFromBytes(k.getBytes(StandardCharsets.UTF_8));
            final var sc = this.api.createStaticAudioChannel(channelId);
            if (sc != null)
              sc.addTarget(conn);
            return sc;
          });

          if (staticChannel != null)
            staticChannel.send(data);
        }
      }
    );

    player.setOnStopped(() -> {
      this.activeRadioPlaybacks.remove(key);
      this.radioChannels.keySet().removeIf(k -> k.startsWith("radio_playback:" + key + ":"));
      this.lastChannelActivity.keySet().removeIf(k -> k.startsWith("radio_playback:" + key + ":"));
    });
    this.activeRadioPlaybacks.put(key, player);
    player.startPlaying();
  }

  @Override
  public void stopRadioPlayback(final @NotNull String channelName) {
    final var key = channelName.toLowerCase();
    final var player = this.activeRadioPlaybacks.remove(key);
    if (player != null)
      player.stopPlaying();
    this.radioChannels.keySet().removeIf(k -> k.startsWith("radio_playback:" + key + ":"));
    this.lastChannelActivity.keySet().removeIf(k -> k.startsWith("radio_playback:" + key + ":"));
  }

  @Override
  public boolean isRadioPlaying(final @NotNull String channelName) {
    final var player = this.activeRadioPlaybacks.get(channelName.toLowerCase());
    return player != null && player.isPlaying();
  }

}
