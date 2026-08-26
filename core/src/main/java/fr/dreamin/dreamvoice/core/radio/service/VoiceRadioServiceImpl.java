package fr.dreamin.dreamvoice.core.radio.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.radio.model.RadioChannel;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.utils.RawUtils;
import org.bukkit.Bukkit;
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

public final class VoiceRadioServiceImpl implements VoiceRadioService, Listener {

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;

  private final Map<String, RadioChannel> channels = new ConcurrentHashMap<>();
  private final Map<UUID, String> playerChannels = new ConcurrentHashMap<>();

  // Channel audio broadcast cache
  private final Map<String, StaticAudioChannel> radioChannels = new ConcurrentHashMap<>();
  private final Map<String, Long> lastChannelActivity = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastSpeakingTimes = new ConcurrentHashMap<>();

  public VoiceRadioServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;
    Bukkit.getPluginManager().registerEvents(this, plugin);

    // Watcher task for Roger Beep end-of-transmission
    Bukkit.getScheduler().runTaskTimer(plugin, this::checkRogerBeeps, 2L, 2L);
    Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupIdleChannels, 600L, 600L);
  }

  private void cleanupIdleChannels() {
    final var now = System.currentTimeMillis();
    this.lastChannelActivity.entrySet().removeIf(entry -> {
      if (now - entry.getValue() > 30000L) {
        this.radioChannels.remove(entry.getKey());
        return true;
      }
      return false;
    });
  }


  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;
  }

  @Override
  public @NotNull Collection<RadioChannel> getChannels() {
    return Collections.unmodifiableCollection(this.channels.values());
  }

  @Override
  public @NotNull RadioChannel getOrCreateChannel(final @NotNull String name) {
    return this.channels.computeIfAbsent(name.toLowerCase(), RadioChannel::new);
  }

  @Override
  public @Nullable RadioChannel getChannel(final @NotNull String name) {
    return this.channels.get(name.toLowerCase());
  }

  @Override
  public @Nullable RadioChannel getChannelOfPlayer(final @NotNull UUID playerUuid) {
    final var channelName = this.playerChannels.get(playerUuid);
    if (channelName == null)
      return null;
    return this.channels.get(channelName);
  }

  @Override
  public void joinChannel(final @NotNull UUID playerUuid, final @NotNull String channelName) {
    leaveChannel(playerUuid);

    final var channel = getOrCreateChannel(channelName);
    channel.addMember(playerUuid);
    this.playerChannels.put(playerUuid, channel.getName());
  }

  @Override
  public void leaveChannel(final @NotNull UUID playerUuid) {
    final var current = this.playerChannels.remove(playerUuid);
    if (current != null) {
      final var ch = this.channels.get(current);
      if (ch != null) {
        ch.removeMember(playerUuid);
      }
    }
  }


  @Override
  public void removeChannel(final @NotNull String name) {
    final var ch = this.channels.remove(name.toLowerCase());
    if (ch != null) {
      ch.getMembers().forEach(this.playerChannels::remove);
    }
  }

  private void checkRogerBeeps() {
    final var now = System.currentTimeMillis();
    final var it = this.lastSpeakingTimes.entrySet().iterator();

    while (it.hasNext()) {
      final var entry = it.next();
      final var senderUuid = entry.getKey();
      final var lastTime = entry.getValue();

      if (now - lastTime >= 350) {
        it.remove();
        final var channel = getChannelOfPlayer(senderUuid);
        if (channel != null && channel.isRogerBeep()) {
          playRogerBeepToChannel(channel, senderUuid);
        }
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

      for (final var memberUuid : channel.getMembers()) {
        if (memberUuid.equals(senderUuid))
          continue;

        final var conn = this.api.getConnectionOf(memberUuid);
        if (conn == null)
          continue;

        final var streamKey = senderUuid + ":" + memberUuid;
        final var staticChannel = this.radioChannels.computeIfAbsent(streamKey, k -> {
          final var sc = this.api.createStaticAudioChannel(UUID.randomUUID());
          if (sc != null)
            sc.addTarget(conn);
          return sc;
        });


        if (staticChannel != null)
          staticChannel.send(opus);
      }
    } catch (Exception ignored) {
    }
  }

  @EventHandler
  private void onMicrophone(final @NotNull MicrophonePacketEvent event) {
    final var sender = event.getSender();
    if (sender == null)
      return;

    final var senderUuid = sender.getPlayer().getUuid();
    final var channel = getChannelOfPlayer(senderUuid);
    if (channel == null)
      return;

    this.lastSpeakingTimes.put(senderUuid, System.currentTimeMillis());

    final var members = channel.getMembers();
    if (members.size() <= 1)
      return;

    var opusData = event.getPacket().getOpusEncodedData();
    final var filterService = DreamVoice.getService(VoiceFilterService.class);

    // Apply radio filter
    try {
      final var voiceService = DreamVoice.getService(VoiceService.class);
      final var decoder = voiceService.getDecoder(senderUuid);
      final var encoder = voiceService.getEncoder(senderUuid);
      final var pcm = decoder.decode(opusData);
      if (pcm != null && pcm.length > 0) {
        var processed = pcm;
        if (filterService != null && channel.getFilterId() != null && !channel.getFilterId().equalsIgnoreCase("none")) {
          final var filter = filterService.getFilter(channel.getFilterId());
          if (filter != null)
            processed = filter.process(processed, null);
        }

        processed = fr.dreamin.dreamvoice.core.utils.audio.AudioLimiter.process(processed);
        opusData = encoder.encode(processed);
      }
    } catch (Exception ignored) {
    }

    final var finalOpus = opusData;
    final var now = System.currentTimeMillis();

    for (final var memberUuid : members) {
      if (memberUuid.equals(senderUuid))
        continue;

      final var conn = this.api.getConnectionOf(memberUuid);
      if (conn == null)
        continue;

      final var streamKey = senderUuid + ":" + memberUuid;
      final var staticChannel = this.radioChannels.computeIfAbsent(streamKey, k -> {
        final var sc = this.api.createStaticAudioChannel(UUID.randomUUID());
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

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    final var uuid = event.getPlayer().getUniqueId();
    leaveChannel(uuid);
    final var uidStr = uuid.toString();
    this.radioChannels.keySet().removeIf(k -> k.contains(uidStr));
    this.lastChannelActivity.keySet().removeIf(k -> k.contains(uidStr));
    this.lastSpeakingTimes.remove(uuid);
  }

}


