package fr.dreamin.dreamvoice.core;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import fr.dreamin.dreamapi.plugin.DreamPlugin;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.player.service.PlayerService;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.cmd.DebugCmd;
import fr.dreamin.dreamvoice.core.codex.service.CodexServiceImpl;
import fr.dreamin.dreamvoice.core.filter.service.VoiceFilterServiceImpl;
import fr.dreamin.dreamvoice.core.player.service.PlayerServiceImpl;
import fr.dreamin.dreamvoice.core.recording.cmd.RecordingCmd;
import fr.dreamin.dreamvoice.core.recording.service.VoiceRecordingServiceImpl;
import fr.dreamin.dreamvoice.core.speaker.cmd.SpeakerCmd;
import fr.dreamin.dreamvoice.core.speaker.service.VoiceSpeakerServiceImpl;
import fr.dreamin.dreamvoice.core.transmitter.cmd.TransmitterCmd;
import fr.dreamin.dreamvoice.core.transmitter.service.VoiceTransmitterServiceImpl;
import fr.dreamin.dreamvoice.core.voice.service.VoiceServiceImpl;
import fr.dreamin.dreamvoice.core.wall.service.VoiceWallServiceImpl;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.ServicePriority;

@Getter
public final class DreamVoice extends DreamPlugin implements Listener {

  @Override
  public void onDreamEnable() {
    instance = this;

    initVoiceService();

    registerCommand(new DebugCmd());
    registerCommand(new RecordingCmd());
    registerCommand(new TransmitterCmd());
    registerCommand(new SpeakerCmd());
    registerCommand(new fr.dreamin.dreamvoice.core.radio.cmd.RadioCmd());
    registerCommand(new fr.dreamin.dreamvoice.core.projection.cmd.ProjectionCmd());
    registerCommand(new fr.dreamin.dreamvoice.core.wiretap.cmd.WiretapCmd());
    registerCommand(new fr.dreamin.dreamvoice.core.wall.cmd.VoiceWallCmd());
  }



  @Override
  public void onDreamDisable() {
    final var voiceService = getService(VoiceService.class);
    if (voiceService != null)
      voiceService.clearAllSounds();
  }


  // ###############################################################
  // ----------------------- PRIVATE METHODS -----------------------
  // ###############################################################

  private void initVoiceService() {
    final var service = getServer().getServicesManager().load(BukkitVoicechatService.class);
    if (service == null) {
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    final var playerService = new PlayerServiceImpl(this);
    final var voiceFilterService = new VoiceFilterServiceImpl(this, playerService);
    final var voiceRecordingService = new VoiceRecordingServiceImpl(this);
    final var voiceTransmitterService = new VoiceTransmitterServiceImpl(this);
    final var voiceSpeakerService = new VoiceSpeakerServiceImpl(this);
    final var voiceRadioService = new fr.dreamin.dreamvoice.core.radio.service.VoiceRadioServiceImpl(this);
    final var voiceProjectionService = new fr.dreamin.dreamvoice.core.projection.service.VoiceProjectionServiceImpl(this);
    final var voiceWiretapService = new fr.dreamin.dreamvoice.core.wiretap.service.VoiceWiretapServiceImpl(this);
    final var voiceWallService = new VoiceWallServiceImpl(this, playerService);
    final var codexService = new CodexServiceImpl(this, voiceWallService);
    final var voiceService = new VoiceServiceImpl(this, codexService, playerService, voiceWallService);

    Bukkit.getServicesManager().register(CodexService.class, codexService, this, ServicePriority.Normal);
    Bukkit.getServicesManager().register(PlayerService.class, playerService, this, ServicePriority.Normal);
    Bukkit.getServicesManager().register(VoiceFilterService.class, voiceFilterService, this, ServicePriority.Normal);
    Bukkit.getServicesManager().register(VoiceWallService.class, voiceWallService, this, ServicePriority.Normal);
    Bukkit.getServicesManager().register(VoiceSpeakerService.class, voiceSpeakerService, this, ServicePriority.Normal);
    Bukkit.getServicesManager().register(VoiceTransmitterService.class, voiceTransmitterService, this, ServicePriority.Normal);
    Bukkit.getServicesManager().register(VoiceRecordingService.class, voiceRecordingService, this, ServicePriority.Normal);
    Bukkit.getServicesManager().register(fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService.class, voiceRadioService, this, ServicePriority.Normal);
    Bukkit.getServicesManager().register(fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService.class, voiceProjectionService, this, ServicePriority.Normal);
    Bukkit.getServicesManager().register(fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService.class, voiceWiretapService, this, ServicePriority.Normal);
    Bukkit.getServicesManager().register(VoiceService.class, voiceService, this, ServicePriority.Normal);
    service.registerPlugin(voiceService);
  }





}

