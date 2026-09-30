package fr.dreamin.dreamvoice.common;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
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
import fr.dreamin.dreamvoice.common.broadcast.service.VoiceBroadcastServiceImpl;
import fr.dreamin.dreamvoice.common.codex.service.CodexServiceImpl;
import fr.dreamin.dreamvoice.common.filter.service.VoiceFilterServiceImpl;
import fr.dreamin.dreamvoice.common.persistence.service.VoicePersistenceServiceImpl;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.player.service.PlayerServiceImpl;
import fr.dreamin.dreamvoice.common.projection.service.VoiceProjectionServiceImpl;
import fr.dreamin.dreamvoice.common.radio.service.VoiceRadioServiceImpl;
import fr.dreamin.dreamvoice.common.recording.service.VoiceRecordingServiceImpl;
import fr.dreamin.dreamvoice.common.room.service.VoiceRoomServiceImpl;
import fr.dreamin.dreamvoice.common.speaker.service.VoiceSpeakerServiceImpl;
import fr.dreamin.dreamvoice.common.speech.service.VoiceSpeechServiceImpl;
import fr.dreamin.dreamvoice.common.transmitter.service.VoiceTransmitterServiceImpl;
import fr.dreamin.dreamvoice.common.voice.service.VoiceServiceImpl;
import fr.dreamin.dreamvoice.common.wall.service.VoiceWallServiceImpl;
import fr.dreamin.dreamvoice.common.wiretap.service.VoiceWiretapServiceImpl;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform-independent DreamVoice engine implementation coordinating all acoustic services.
 */
@Getter
public final class DreamVoiceCommon implements DreamVoiceAPI {

  private static DreamVoiceCommon instance;

  private final @NotNull VoicePlatform platform;
  private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

  private PlayerServiceImpl playerService;
  private VoiceFilterServiceImpl filterService;
  private VoiceRecordingServiceImpl recordingService;
  private VoiceTransmitterServiceImpl transmitterService;
  private VoiceSpeakerServiceImpl speakerService;
  private VoiceRadioServiceImpl radioService;
  private VoiceProjectionServiceImpl projectionService;
  private VoiceWiretapServiceImpl wiretapService;
  private VoiceRoomServiceImpl roomService;
  private VoiceWallServiceImpl wallService;
  private VoicePersistenceServiceImpl persistenceService;
  private CodexServiceImpl codexService;
  private VoiceSpeechServiceImpl speechService;
  private VoiceBroadcastServiceImpl broadcastService;
  private VoiceServiceImpl voiceService;

  public DreamVoiceCommon(final @NotNull VoicePlatform platform) {
    this.platform = platform;
    instance = this;
    DreamVoiceAPI.set(this);
    initServices();
  }

  public static @Nullable DreamVoiceCommon getInstance() {
    return instance;
  }

  private void initServices() {
    this.playerService = new PlayerServiceImpl(this.platform);
    this.filterService = new VoiceFilterServiceImpl(this.platform, this.playerService);
    this.recordingService = new VoiceRecordingServiceImpl(this.platform);
    this.transmitterService = new VoiceTransmitterServiceImpl(this.platform);
    this.speakerService = new VoiceSpeakerServiceImpl(this.platform);
    this.radioService = new VoiceRadioServiceImpl(this.platform);
    this.projectionService = new VoiceProjectionServiceImpl(this.platform);
    this.wiretapService = new VoiceWiretapServiceImpl(this.platform);
    this.roomService = new VoiceRoomServiceImpl(this.platform);
    this.wallService = new VoiceWallServiceImpl(this.platform, this.playerService);
    this.persistenceService = new VoicePersistenceServiceImpl(this.platform);
    this.codexService = new CodexServiceImpl(this.platform, this.wallService);
    this.speechService = new VoiceSpeechServiceImpl(this.platform);
    this.broadcastService = new VoiceBroadcastServiceImpl(this.platform);
    this.voiceService = new VoiceServiceImpl(this.platform, this.playerService, this.wallService);

    registerService(PlayerService.class, this.playerService);
    registerService(VoiceFilterService.class, this.filterService);
    registerService(VoiceRecordingService.class, this.recordingService);
    registerService(VoiceTransmitterService.class, this.transmitterService);
    registerService(VoiceSpeakerService.class, this.speakerService);
    registerService(VoiceRadioService.class, this.radioService);
    registerService(VoiceProjectionService.class, this.projectionService);
    registerService(VoiceWiretapService.class, this.wiretapService);
    registerService(VoiceRoomService.class, this.roomService);
    registerService(VoiceWallService.class, this.wallService);
    registerService(VoicePersistenceService.class, this.persistenceService);
    registerService(CodexService.class, this.codexService);
    registerService(VoiceSpeechService.class, this.speechService);
    registerService(VoiceBroadcastService.class, this.broadcastService);
    registerService(VoiceService.class, this.voiceService);
  }

  public <T> void registerService(final @NotNull Class<T> serviceClass, final @NotNull T serviceInstance) {
    this.services.put(serviceClass, serviceInstance);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> @Nullable T getService(final @NotNull Class<T> serviceClass) {
    return (T) this.services.get(serviceClass);
  }

  @Override
  public void callEvent(final @NotNull VoiceEvent event) {
    this.platform.dispatchEvent(event);
  }

  public void onSvcStarted(final @NotNull VoicechatServerApi svcApi) {
    this.voiceService.init(svcApi);
    this.speakerService.init(svcApi);
    this.radioService.init(svcApi);
    this.projectionService.init(svcApi);
    this.wiretapService.init(svcApi);
    this.wallService.init(svcApi);
    this.transmitterService.init(svcApi);
    this.recordingService.init(svcApi);
  }

  public void onPlayerDisconnect(final @NotNull java.util.UUID playerUuid) {
    if (this.filterService != null)
      this.filterService.onPlayerQuit(playerUuid);
    if (this.playerService != null)
      this.playerService.removePlayer(playerUuid);
    if (this.speakerService != null)
      this.speakerService.onPlayerDisconnect(playerUuid);
    if (this.radioService != null)
      this.radioService.onPlayerDisconnect(playerUuid);
    if (this.recordingService != null)
      this.recordingService.onPlayerDisconnect(playerUuid);
    if (this.wiretapService != null)
      this.wiretapService.onPlayerDisconnect(playerUuid);
    if (this.transmitterService != null)
      this.transmitterService.onPlayerDisconnect(playerUuid);
    if (this.speechService != null)
      this.speechService.onPlayerDisconnect(playerUuid);
    if (this.wallService != null)
      this.wallService.onPlayerDisconnect(playerUuid);
  }

  public void shutdown() {
    try {
      if (this.persistenceService != null) {
        this.persistenceService.cancelAutoSaveTasks();
        this.persistenceService.saveAll();
      }
    } catch (final Throwable t) {
      this.platform.logError("Failed to save persistence during shutdown", t);
    }

    try {
      if (this.voiceService != null)
        this.voiceService.clearAllSounds();
    } catch (final Throwable t) {
      this.platform.logError("Failed to clear voice sounds during shutdown", t);
    }
  }

}
