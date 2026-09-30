package fr.dreamin.dreamvoice.api;

import fr.dreamin.dreamvoice.api.broadcast.service.VoiceBroadcastService;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.persistence.service.VoicePersistenceService;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.room.service.VoiceRoomService;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.api.speech.service.VoiceSpeechService;
import fr.dreamin.dreamvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Public service locator and API entrypoint for DreamVoice.
 */
public interface DreamVoiceAPI {

  static @NotNull DreamVoiceAPI get() {
    return Provider.get();
  }

  static @Nullable DreamVoiceAPI getNullable() {
    return Provider.getNullable();
  }

  static void set(final @NotNull DreamVoiceAPI api) {
    Provider.set(api);
  }

  <T> @Nullable T getService(final @NotNull Class<T> serviceClass);

  void callEvent(final @NotNull VoiceEvent event);

  default @Nullable VoiceSpeakerService speakerService() {
    return getService(VoiceSpeakerService.class);
  }

  default @Nullable VoiceBroadcastService broadcastService() {
    return getService(VoiceBroadcastService.class);
  }

  default @Nullable VoiceRadioService radioService() {
    return getService(VoiceRadioService.class);
  }

  default @Nullable VoiceFilterService filterService() {
    return getService(VoiceFilterService.class);
  }

  default @Nullable VoiceRoomService roomService() {
    return getService(VoiceRoomService.class);
  }

  default @Nullable VoiceWallService wallService() {
    return getService(VoiceWallService.class);
  }

  default @Nullable VoiceWiretapService wiretapService() {
    return getService(VoiceWiretapService.class);
  }

  default @Nullable VoiceProjectionService projectionService() {
    return getService(VoiceProjectionService.class);
  }

  default @Nullable VoiceTransmitterService transmitterService() {
    return getService(VoiceTransmitterService.class);
  }

  default @Nullable VoiceSpeechService speechService() {
    return getService(VoiceSpeechService.class);
  }

  default @Nullable VoiceRecordingService recordingService() {
    return getService(VoiceRecordingService.class);
  }

  default @Nullable VoicePersistenceService persistenceService() {
    return getService(VoicePersistenceService.class);
  }

  default @Nullable PlayerService playerService() {
    return getService(PlayerService.class);
  }

  default @Nullable CodexService codexService() {
    return getService(CodexService.class);
  }

  default @Nullable VoiceService voiceService() {
    return getService(VoiceService.class);
  }

  default @Nullable VoiceSpeakerService getSpeakerService() { return speakerService(); }
  default @Nullable VoiceBroadcastService getBroadcastService() { return broadcastService(); }
  default @Nullable VoiceRadioService getRadioService() { return radioService(); }
  default @Nullable VoiceFilterService getFilterService() { return filterService(); }
  default @Nullable VoiceRoomService getRoomService() { return roomService(); }
  default @Nullable VoiceWallService getWallService() { return wallService(); }
  default @Nullable VoiceWiretapService getWiretapService() { return wiretapService(); }
  default @Nullable VoiceProjectionService getProjectionService() { return projectionService(); }
  default @Nullable VoiceTransmitterService getTransmitterService() { return transmitterService(); }
  default @Nullable VoiceSpeechService getSpeechService() { return speechService(); }
  default @Nullable VoiceRecordingService getRecordingService() { return recordingService(); }
  default @Nullable VoicePersistenceService getPersistenceService() { return persistenceService(); }
  default @Nullable PlayerService getPlayerService() { return playerService(); }
  default @Nullable CodexService getCodexService() { return codexService(); }
  default @Nullable VoiceService getVoiceService() { return voiceService(); }

  default @Nullable VoicechatServerApi getAPI() {
    final var service = voiceService();
    return service != null ? service.getAPI() : null;
  }

  final class Provider {
    private static DreamVoiceAPI instance;

    private Provider() {}

    public static @NotNull DreamVoiceAPI get() {
      if (instance == null)
        throw new IllegalStateException("DreamVoiceAPI has not been initialized yet.");
      return instance;
    }

    public static @Nullable DreamVoiceAPI getNullable() {
      return instance;
    }

    public static void set(final @NotNull DreamVoiceAPI api) {
      instance = api;
    }
  }

}
