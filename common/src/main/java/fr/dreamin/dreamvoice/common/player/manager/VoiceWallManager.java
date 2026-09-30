package fr.dreamin.dreamvoice.common.player.manager;

import fr.dreamin.dreamvoice.api.player.model.PlayerManager;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
public final class VoiceWallManager extends PlayerManager {

  private final Map<UUID, WallBlockInfo> blockedPlayers = new ConcurrentHashMap<>();
  private @NotNull Set<VPlayer> vPlayersSpeaker = new HashSet<>();

  private double lastX, lastY, lastZ;
  private long lastPositionUpdate = 0;
  private boolean moved = false;

  private int raycastCount = 0;
  private int cacheHits = 0;
  private long lastRaycastTime = 0;

  public VoiceWallManager(final @NotNull VPlayer gamePlayer) {
    super(gamePlayer);
    updatePosition();
  }

  @Override
  public void init() {
  }

  @Override
  public void close() {
    this.vPlayersSpeaker.clear();
    resetStats();
  }

  public void updatePosition() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;

    final var locOpt = common.getPlatform().getPlayerLocation(this.vPlayer.getUuid());
    if (locOpt.isEmpty())
      return;

    final var loc = locOpt.get();
    this.lastX = loc.x();
    this.lastY = loc.y();
    this.lastZ = loc.z();
    this.lastPositionUpdate = System.currentTimeMillis();
  }

  public boolean hasMovedSignificantly(final double threshold) {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return false;

    final var locOpt = common.getPlatform().getPlayerLocation(this.vPlayer.getUuid());
    if (locOpt.isEmpty())
      return false;

    final var loc = locOpt.get();
    final var currentX = loc.x();
    final var currentY = loc.y();
    final var currentZ = loc.z();

    final var distance = Math.sqrt(
      Math.pow(currentX - this.lastX, 2) +
        Math.pow(currentY - this.lastY, 2) +
        Math.pow(currentZ - this.lastZ, 2)
    );

    return distance > threshold;
  }

  public void addBlockedPlayer(final @NotNull VPlayer blockedPlayer, final double totalAttenuation, final @NotNull WallBlockReason reason) {
    this.blockedPlayers.put(blockedPlayer.getUuid(), new WallBlockInfo(totalAttenuation, reason));
  }

  public void removeBlockedPlayer(final @NotNull VPlayer blockedPlayer) {
    this.blockedPlayers.remove(blockedPlayer.getUuid());
  }

  public boolean canHear(final @NotNull VPlayer speaker) {
    final var info = this.blockedPlayers.get(speaker.getUuid());
    return info == null || !info.canHear();
  }

  public @Nullable WallBlockInfo getWallInfo(final @NotNull VPlayer speaker) {
    return this.blockedPlayers.get(speaker.getUuid());
  }

  public double getTotalAttenuationDb(final @NotNull VPlayer speaker) {
    final var info = this.blockedPlayers.get(speaker.getUuid());
    return info != null ? info.totalAttenuationDb() : 0.0;
  }

  public WallBlockReason getBlockedReason(final @NotNull VPlayer speaker) {
    final var info = this.blockedPlayers.get(speaker.getUuid());
    return info != null ? info.reason() : null;
  }

  public void clearBlockedPlayers() {
    this.blockedPlayers.clear();
  }

  public boolean isValidClient() {
    var client = this.vPlayer.getClient();
    if (client != null && client.isConnected())
      return true;

    final var common = DreamVoiceCommon.getInstance();
    if (common != null && common.getVoiceService() != null && common.getVoiceService().getAPI() != null) {
      client = common.getVoiceService().getAPI().getConnectionOf(this.vPlayer.getUuid());
      return client != null && client.isConnected();
    }
    return false;
  }

  public void incrementRaycastCount() {
    this.raycastCount++;
    this.lastRaycastTime = System.currentTimeMillis();
  }

  public void incrementCacheHits() {
    this.cacheHits++;
  }

  public double getCacheHitRatio() {
    final var totalRequests = this.raycastCount + this.cacheHits;
    return totalRequests > 0 ? (double) this.cacheHits / totalRequests : 0.0;
  }

  public void resetStats() {
    this.raycastCount = 0;
    this.cacheHits = 0;
    this.lastRaycastTime = 0;
  }

  public String getStatsString() {
    return String.format("RayCasts: %d, Cache hits: %d, Hit ratio: %.2f%%",
      this.raycastCount, this.cacheHits, getCacheHitRatio() * 100);
  }

  public record WallBlockInfo(double totalAttenuationDb, WallBlockReason reason) {

    public boolean isWallBlocked() {
      return this.reason == WallBlockReason.WALL;
    }

    public boolean canHear() {
      return this.reason == WallBlockReason.WALL || this.reason == WallBlockReason.DISTANCE;
    }

  }

  public enum WallBlockReason {
    WALL,
    DISTANCE
  }

}
