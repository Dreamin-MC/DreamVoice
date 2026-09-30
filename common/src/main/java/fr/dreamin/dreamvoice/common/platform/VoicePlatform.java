package fr.dreamin.dreamvoice.common.platform;

import de.maxhenkel.voicechat.api.ServerLevel;
import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.wall.model.WallConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Platform abstraction layer separating Minecraft engine specifics (Paper / Fabric)
 * from core DreamVoice acoustic DSP and Simple Voice Chat routing.
 */
public interface VoicePlatform {

  /**
   * Root plugin / mod data directory for configs, database, logs.
   */
  @NotNull Path getDataDirectory();

  /**
   * Schedules a task to execute asynchronously.
   */
  void runAsync(final @NotNull Runnable runnable);

  /**
   * Schedules a task to execute on the main server thread.
   */
  void runSync(final @NotNull Runnable runnable);

  /**
   * Schedules a delayed task on the main server thread.
   */
  void runLater(final @NotNull Runnable runnable, final long delayTicks);

  /**
   * Schedules a recurring timer task.
   */
  void runTimer(final @NotNull Runnable runnable, final long delayTicks, final long periodTicks);

  /**
   * Retrieves player's current foot location in world space.
   */
  @NotNull Optional<VoiceLocation> getPlayerLocation(final @NotNull UUID playerUuid);

  /**
   * Retrieves player's current eye/head position in world space.
   */
  @NotNull Optional<VoiceLocation> getPlayerEyeLocation(final @NotNull UUID playerUuid);

  /**
   * Checks whether a player is currently connected and online.
   */
  boolean isPlayerOnline(final @NotNull UUID playerUuid);

  /**
   * Retrieves player's display name or username.
   */
  @NotNull String getPlayerName(final @NotNull UUID playerUuid);

  /**
   * Returns a collection of all currently online player UUIDs.
   */
  @NotNull Collection<UUID> getOnlinePlayers();

  /**
   * Retrieves entity location if currently loaded in world.
   */
  @NotNull Optional<VoiceLocation> getEntityLocation(final @NotNull UUID entityUuid);

  /**
   * Checks if an entity is alive and valid.
   */
  boolean isEntityValid(final @NotNull UUID entityUuid);

  /**
   * Resolves the Simple Voice Chat ServerLevel for a given world identifier.
   */
  @Nullable ServerLevel getServerLevel(final @NotNull String worldName);

  /**
   * Computes acoustic attenuation / decibel loss between two world points using raycasting.
   */
  double computeAcousticOcclusion(final @NotNull VoiceLocation from, final @NotNull VoiceLocation to, final @Nullable WallConfig wallConfig);

  /**
   * Tests direct line-of-sight between two world points.
   */
  boolean hasLineOfSight(final @NotNull VoiceLocation from, final @NotNull VoiceLocation to);

  /**
   * Sends a translatable message to a player.
   */
  void sendMessage(final @NotNull UUID playerUuid, final @NotNull String messageKey, final Object... args);

  /**
   * Broadcasts a translatable message to all players with a permission.
   */
  void broadcastMessage(final @NotNull String messageKey, final @Nullable String permission, final Object... args);

  /**
   * Plays a physical sound effect at a world location.
   */
  void playSoundEffect(final @NotNull VoiceLocation location, final @NotNull String soundKey, final float volume, final float pitch);

  /**
   * Renders visual debugging particles for VoiceWall raycasts.
   */
  void renderDebugRay(final @NotNull UUID viewerUuid, final @NotNull VoiceLocation from, final @NotNull VoiceLocation to, final boolean directHit);

  /**
   * Checks whether the player's eye location is submerged in liquid (water/lava).
   */
  boolean isPlayerSubmerged(final @NotNull UUID playerUuid);

  /**
   * Checks whether the player is in an underground/cave environment (e.g. low Y, zero sky light).
   */
  boolean isPlayerInCave(final @NotNull UUID playerUuid);

  /**
   * Dispatches a VoiceEvent to the platform's native event bus if applicable.
   */
  void dispatchEvent(final @NotNull VoiceEvent event);

  // Logging
  void logInfo(final @NotNull String message);
  void logWarning(final @NotNull String message);
  void logError(final @NotNull String message, final @Nullable Throwable throwable);

}
