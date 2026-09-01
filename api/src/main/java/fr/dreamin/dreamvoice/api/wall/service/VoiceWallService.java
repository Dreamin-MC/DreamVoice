package fr.dreamin.dreamvoice.api.wall.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Acoustic engine service managing wall sound occlusion, acoustic pathfinding through apertures,
 * air damping, and real-time particle raycast debugging.
 */
public interface VoiceWallService {

  /**
   * Initializes the VoiceWall service with the Simple Voice Chat server API.
   *
   * @param api the VoicechatServerApi instance
   */
  void init(final @NotNull VoicechatServerApi api);

  /**
   * Checks whether the VoiceWall acoustic engine is globally enabled.
   *
   * @return {@code true} if active
   */
  boolean isEnable();

  /**
   * Enables or disables VoiceWall globally.
   *
   * @param value the enabled flag
   */
  void setEnable(final boolean value);

  /**
   * Gets the active occlusion mode.
   *
   * @return the {@link VoiceWallMode}
   */
  @NotNull VoiceWallMode getMode();

  /**
   * Sets the active occlusion mode.
   *
   * @param mode the new {@link VoiceWallMode}
   */
  void setMode(final @NotNull VoiceWallMode mode);

  /**
   * Convenience check if strict block mode (100% soundproof) is active.
   *
   * @return {@code true} if strict block mode
   */
  default boolean isStrictBlock() {
    return getMode() == VoiceWallMode.STRICT_BLOCK;
  }

  /**
   * Checks whether high-frequency air damping over distance is active.
   *
   * @return {@code true} if air damping is enabled
   */
  boolean isAirDampingEnabled();

  /**
   * Sets high-frequency air damping over distance.
   *
   * @param value the air damping flag
   */
  void setAirDampingEnabled(final boolean value);

  /**
   * Checks whether debug logging is enabled.
   *
   * @return {@code true} if debug is active
   */
  boolean isDebug();

  /**
   * Sets debug logging flag.
   *
   * @param value debug flag
   */
  void setDebug(final boolean value);

  /**
   * Toggles visual particle raycast debugging and Action Bar diagnostics for a player.
   *
   * @param player the player
   * @return new debugging state ({@code true} if enabled)
   */
  boolean toggleDebugPlayer(final @NotNull Player player);

  /**
   * Checks whether a player has visual particle debugging enabled.
   *
   * @param playerUuid the player UUID
   * @return {@code true} if debug active for this player
   */
  boolean hasDebugPlayer(final @NotNull UUID playerUuid);

  /**
   * Sets visual particle debugging state for a player.
   *
   * @param playerUuid the player UUID
   * @param enabled    debugging flag
   */
  void setDebugPlayer(final @NotNull UUID playerUuid, final boolean enabled);

  /**
   * Intercepts and processes a positional entity sound packet, performing acoustic raycasting and attenuation.
   *
   * @param event        the intercepted SVC entity sound packet event
   * @param vSender      the sender voice player wrapper
   * @param vReceiver    the receiver voice player wrapper
   * @param receiverConn the receiver voicechat connection
   */
  void processEntitySoundPacket(
    final @NotNull EntitySoundPacketEvent event,
    final @NotNull VPlayer vSender,
    final @NotNull VPlayer vReceiver,
    final @NotNull VoicechatConnection receiverConn
  );

}
