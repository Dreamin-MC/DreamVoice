package fr.dreamin.dreamvoice.fabric;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import fr.dreamin.dreamvoice.common.voice.service.VoiceServiceImpl;
import org.jetbrains.annotations.Nullable;

public final class DreamVoiceVoicechatPlugin implements VoicechatPlugin {

  private static VoicechatServerApi svcApi;

  public static @Nullable VoicechatServerApi getSvcApi() {
    return svcApi;
  }

  @Override
  public String getPluginId() {
    return "DreamVoice";
  }

  @Override
  public void registerEvents(final EventRegistration registration) {
    registration.registerEvent(VoicechatServerStartedEvent.class, event -> {
      svcApi = event.getVoicechat();
      final var common = DreamVoiceFabric.getCommon();
      if (common != null && common.getVoiceService() instanceof VoiceServiceImpl vs)
        vs.onServerStarted(event);
    });

    registration.registerEvent(MicrophonePacketEvent.class, event -> {
      final var common = DreamVoiceFabric.getCommon();
      if (common != null && common.getVoiceService() instanceof VoiceServiceImpl vs)
        vs.onMicrophonePacket(event);
    });

    registration.registerEvent(EntitySoundPacketEvent.class, event -> {
      final var common = DreamVoiceFabric.getCommon();
      if (common != null && common.getVoiceService() instanceof VoiceServiceImpl vs)
        vs.onEntitySoundPacket(event);
    });

    registration.registerEvent(PlayerDisconnectedEvent.class, event -> {
      final var common = DreamVoiceFabric.getCommon();
      if (common != null && common.getVoiceService() instanceof VoiceServiceImpl vs)
        vs.onPlayerDisconnected(event);
    });
  }
}
