package fr.dreamin.dreamvoice.api.wall.service;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import org.jetbrains.annotations.NotNull;

public interface VoiceWallService {

  void init(final @NotNull VoicechatServerApi api);

  boolean isEnable();
  void setEnable(final boolean value);

  @NotNull fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode getMode();
  void setMode(final @NotNull fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode mode);

  default boolean isStrictBlock() {
    return getMode() == fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode.STRICT_BLOCK;
  }

  boolean isAirDampingEnabled();
  void setAirDampingEnabled(final boolean value);


  boolean isDebug();
  void setDebug(final boolean value);

  boolean toggleDebugPlayer(final @NotNull org.bukkit.entity.Player player);
  boolean hasDebugPlayer(final @NotNull java.util.UUID playerUuid);
  void setDebugPlayer(final @NotNull java.util.UUID playerUuid, final boolean enabled);


  void processEntitySoundPacket(
    final @NotNull EntitySoundPacketEvent event,
    final @NotNull VPlayer vSender,
    final @NotNull VPlayer vReceiver,
    final @NotNull VoicechatConnection receiverConn
  );

}

