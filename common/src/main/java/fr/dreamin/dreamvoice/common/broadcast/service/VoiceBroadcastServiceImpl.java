package fr.dreamin.dreamvoice.common.broadcast.service;

import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.broadcast.model.BroadcastPoint;
import fr.dreamin.dreamvoice.api.broadcast.service.VoiceBroadcastService;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.speaker.model.Speaker;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.common.broadcast.storage.BroadcastPersistence;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform-independent implementation of {@link VoiceBroadcastService} managing microphone/intercom broadcast points.
 */
public final class VoiceBroadcastServiceImpl implements VoiceBroadcastService {

  private final @NotNull VoicePlatform platform;
  private final @NotNull Map<UUID, BroadcastPoint> pointsByUuid = new ConcurrentHashMap<>();
  private final @NotNull Map<String, BroadcastPoint> pointsByName = new ConcurrentHashMap<>();
  private final @NotNull File storageDir;

  public VoiceBroadcastServiceImpl(final @NotNull VoicePlatform platform) {
    this.platform = platform;
    this.storageDir = new File(platform.getDataDirectory().toFile(), "broadcasts");
    load();
  }

  @Override
  public @NotNull Collection<BroadcastPoint> getBroadcastPoints() {
    return Collections.unmodifiableCollection(this.pointsByUuid.values());
  }

  @Override
  public @Nullable BroadcastPoint getBroadcastPoint(final @NotNull String name) {
    return this.pointsByName.get(name.toLowerCase());
  }

  @Override
  public @Nullable BroadcastPoint getBroadcastPoint(final @NotNull UUID uuid) {
    return this.pointsByUuid.get(uuid);
  }

  @Override
  public void register(final @NotNull BroadcastPoint broadcastPoint) {
    this.pointsByUuid.put(broadcastPoint.getUuid(), broadcastPoint);
    this.pointsByName.put(broadcastPoint.getName().toLowerCase(), broadcastPoint);
  }

  @Override
  public void unregister(final @NotNull UUID uuid) {
    final var removed = this.pointsByUuid.remove(uuid);
    if (removed != null)
      this.pointsByName.remove(removed.getName().toLowerCase());
  }

  @Override
  public void unregister(final @NotNull String name) {
    final var removed = this.pointsByName.remove(name.toLowerCase());
    if (removed != null)
      this.pointsByUuid.remove(removed.getUuid());
  }

  @Override
  public void clearBroadcastPoints() {
    this.pointsByUuid.clear();
    this.pointsByName.clear();
  }

  @Override
  public @NotNull Collection<BroadcastPoint> getBroadcastersInRange(final @NotNull VoiceLocation location) {
    final var matches = new ArrayList<BroadcastPoint>();
    for (final var point : this.pointsByUuid.values()) {
      if (point.isInRange(location))
        matches.add(point);
    }
    return matches;
  }

  @Override
  public void save() {
    BroadcastPersistence.save(this, this.storageDir, this.platform);
  }

  @Override
  public void load() {
    clearBroadcastPoints();
    BroadcastPersistence.load(this, this.storageDir, this.platform);
  }

  public void onMicrophone(final @NotNull MicrophonePacketEvent event) {
    final var senderConn = event.getSenderConnection();
    if (senderConn == null)
      return;

    final var senderUuid = senderConn.getPlayer().getUuid();
    final var senderLoc = this.platform.getPlayerLocation(senderUuid).orElse(null);
    if (senderLoc == null)
      return;

    final var rawOpus = event.getPacket().getOpusEncodedData();
    if (rawOpus == null || rawOpus.length == 0)
      return;

    final var activeBroadcasters = getBroadcastersInRange(senderLoc);
    if (activeBroadcasters.isEmpty())
      return;

    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;

    final var speakerService = common.getService(VoiceSpeakerService.class);
    if (speakerService == null)
      return;

    for (final var point : activeBroadcasters) {
      if (!point.isEnabled())
        continue;

      final Collection<Speaker> targets;
      if (point.isAllSpeakers())
        targets = speakerService.getSpeakers();
      else {
        final var filtered = new ArrayList<Speaker>();
        for (final var speakerName : point.getTargetSpeakers()) {
          final var spk = speakerService.getSpeaker(speakerName);
          if (spk != null)
            filtered.add(spk);
        }
        targets = filtered;
      }

      if (targets.isEmpty())
        continue;

      speakerService.broadcastVoice(targets, senderUuid, rawOpus, point.getFilterId());
    }
  }

}
