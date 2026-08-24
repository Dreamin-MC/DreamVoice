package fr.dreamin.dreamvoice.core.player.manager;

import fr.dreamin.dreamapi.api.annotations.Inject;
import fr.dreamin.dreaminvoice.api.player.model.PlayerManager;
import fr.dreamin.dreaminvoice.api.player.model.VPlayer;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Inject
@Getter @Setter
public final class VoiceWallManager extends PlayerManager {

  private final Map<UUID, WallBlockInfo> blockedPlayers = new ConcurrentHashMap<>();
  private @NotNull Set<VPlayer> vPlayersSpeaker = new HashSet<>();

  private double lastX, lastY, lastZ;
  private long lastPositionUpdate = 0;

  private int raycastCount = 0;
  private int cacheHits = 0;
  private long lastRaycastTime = 0;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceWallManager(@NotNull VPlayer gamePlayer) {
    super(gamePlayer);

    updatePosition();
  }

  // ###############################################################
  // -------------------------- METHODS ----------------------------
  // ###############################################################

  @Override
  public void init() {

  }

  @Override
  public void close() {
    this.vPlayersSpeaker.clear();
    resetStats();
  }

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  public void updatePosition() {
    final var player = this.vPlayer.getBukkitPlayer();
    if (player == null || !player.isOnline()) return;

    this.lastX = player.getX();
    this.lastY = player.getY();
    this.lastZ = player.getZ();
    this.lastPositionUpdate = System.currentTimeMillis();
  }

  public boolean hasMovedSignificantly(double threshold) {
    final var player = this.vPlayer.getBukkitPlayer();
    if (player == null || !player.isOnline()) return false;

    double currentX = player.getX();
    double currentY = player.getY();
    double currentZ = player.getZ();

    double distance = Math.sqrt(
      Math.pow(currentX - lastX, 2) +
        Math.pow(currentY - lastY, 2) +
        Math.pow(currentZ - lastZ, 2)
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
    return this.vPlayer.getClient() != null && this.vPlayer.getClient().isConnected();
  }

  public void incrementRaycastCount() {
    this.raycastCount++;
    this.lastRaycastTime = System.currentTimeMillis();
  }

  public void incrementCacheHits() {
    this.cacheHits++;
  }

  public double getCacheHitRatio() {
    int totalRequests = raycastCount + cacheHits;
    return totalRequests > 0 ? (double) cacheHits / totalRequests : 0.0;
  }

  public void resetStats() {
    this.raycastCount = 0;
    this.cacheHits = 0;
    this.lastRaycastTime = 0;
  }

  public String getStatsString() {
    return String.format("RayCasts: %d, Cache hits: %d, Hit ratio: %.2f%%",
      raycastCount, cacheHits, getCacheHitRatio() * 100);
  }

  // ###############################################################
  // ---------------------------- CLASS ----------------------------
  // ###############################################################

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
