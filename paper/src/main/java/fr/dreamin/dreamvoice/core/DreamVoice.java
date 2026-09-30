package fr.dreamin.dreamvoice.core;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import fr.dreamin.dreamapi.api.LoadMode;
import fr.dreamin.dreamapi.api.annotations.EnableServices;
import fr.dreamin.dreamapi.api.item.ItemRegistryService;
import fr.dreamin.dreamapi.core.gui.service.GuiServiceImpl;
import fr.dreamin.dreamapi.core.item.service.ItemRegistryServiceImpl;
import fr.dreamin.dreamapi.core.lang.service.LangServiceImpl;
import fr.dreamin.dreamapi.plugin.DreamPlugin;
import fr.dreamin.dreamvoice.api.broadcast.service.VoiceBroadcastService;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
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
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.core.broadcast.cmd.VoiceBroadcastCommand;
import fr.dreamin.dreamvoice.core.cmd.DebugCmd;
import fr.dreamin.dreamvoice.core.cmd.DreamVoiceCmd;
import fr.dreamin.dreamvoice.core.config.cmd.VoiceConfigCommand;
import fr.dreamin.dreamvoice.core.config.tool.VoiceConfigTool;
import fr.dreamin.dreamvoice.core.config.visualizer.VoiceVisualizerService;
import fr.dreamin.dreamvoice.core.filter.cmd.VoiceFilterCommand;
import fr.dreamin.dreamvoice.core.platform.PaperVoicePlatform;
import fr.dreamin.dreamvoice.core.projection.cmd.ProjectionCmd;
import fr.dreamin.dreamvoice.core.radio.cmd.RadioCmd;
import fr.dreamin.dreamvoice.core.recording.cmd.RecordingCmd;
import fr.dreamin.dreamvoice.core.room.command.VoiceRoomCommand;
import fr.dreamin.dreamvoice.core.speaker.cmd.SpeakerCmd;
import fr.dreamin.dreamvoice.core.speech.cmd.VoiceSpeechCommand;
import fr.dreamin.dreamvoice.core.transmitter.cmd.TransmitterCmd;
import fr.dreamin.dreamvoice.core.wall.cmd.VoiceWallCmd;
import fr.dreamin.dreamvoice.core.wiretap.cmd.WiretapCmd;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;

import java.io.File;

@EnableServices(mode = LoadMode.NONE, include = {LangServiceImpl.class, ItemRegistryServiceImpl.class, GuiServiceImpl.class})
@Getter
public final class DreamVoice extends DreamPlugin implements Listener {

  @Getter
  private static DreamVoice instance;
  private DreamVoiceCommon common;
  private PaperVoicePlatform platform;
  private VoiceVisualizerService visualizerService;

  @Override
  public void onDreamEnable() {
    instance = this;

    setLangCmd(true);

    final var langDir = new File(getDataFolder(), "lang");
    if (!langDir.exists())
      langDir.mkdirs();

    final var defaultLangFile = new File(langDir, "lang-dreamvoice.json");
    if (!defaultLangFile.exists())
      saveResource("lang/lang-dreamvoice.json", false);

    initVoiceService();

    registerCommand(new DebugCmd());
    registerCommand(new DreamVoiceCmd());
    registerCommand(new RecordingCmd());
    registerCommand(new TransmitterCmd());
    registerCommand(new SpeakerCmd());
    registerCommand(new RadioCmd());
    registerCommand(new ProjectionCmd());
    registerCommand(new WiretapCmd());
    registerCommand(new VoiceWallCmd());
    registerCommand(new VoiceRoomCommand());
    registerCommand(new VoiceFilterCommand());
    registerCommand(new VoiceSpeechCommand());
    registerCommand(new VoiceBroadcastCommand());
    registerCommand(new VoiceConfigCommand());

    final var itemService = getService(ItemRegistryService.class);
    if (itemService != null && !itemService.isRegistered(VoiceConfigTool.ID))
      itemService.register(VoiceConfigTool.createDefinition());

    this.visualizerService = new VoiceVisualizerService(this);
    this.visualizerService.start();

    Bukkit.getPluginManager().registerEvents(this, this);
  }

  @Override
  public void onDreamDisable() {
    if (this.visualizerService != null)
      this.visualizerService.stop();

    if (this.common != null)
      this.common.shutdown();
  }

  @EventHandler
  private void onPlayerQuit(final PlayerQuitEvent event) {
    if (this.visualizerService != null)
      this.visualizerService.cleanup(event.getPlayer().getUniqueId());

    if (this.common != null)
      this.common.onPlayerDisconnect(event.getPlayer().getUniqueId());
  }

  private void initVoiceService() {
    final var service = getServer().getServicesManager().load(BukkitVoicechatService.class);
    if (service == null) {
      getLogger().severe("Simple Voice Chat service not found. Disabling DreamVoice.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }

    this.platform = new PaperVoicePlatform(this);
    this.common = new DreamVoiceCommon(this.platform);

    if (this.common.getFilterService() != null)
      this.common.getFilterService().registerAnnotatedFilters(preScannedClasses);

    registerBukkitServices();
    service.registerPlugin(this.common.getVoiceService());
  }

  private void registerBukkitServices() {
    final var sm = Bukkit.getServicesManager();
    sm.register(CodexService.class, this.common.getCodexService(), this, ServicePriority.Normal);
    sm.register(PlayerService.class, this.common.getPlayerService(), this, ServicePriority.Normal);
    sm.register(VoiceFilterService.class, this.common.getFilterService(), this, ServicePriority.Normal);
    sm.register(VoiceWallService.class, this.common.getWallService(), this, ServicePriority.Normal);
    sm.register(VoiceSpeakerService.class, this.common.getSpeakerService(), this, ServicePriority.Normal);
    sm.register(VoiceTransmitterService.class, this.common.getTransmitterService(), this, ServicePriority.Normal);
    sm.register(VoiceRecordingService.class, this.common.getRecordingService(), this, ServicePriority.Normal);
    sm.register(VoiceRadioService.class, this.common.getRadioService(), this, ServicePriority.Normal);
    sm.register(VoiceProjectionService.class, this.common.getProjectionService(), this, ServicePriority.Normal);
    sm.register(VoiceWiretapService.class, this.common.getWiretapService(), this, ServicePriority.Normal);
    sm.register(VoiceRoomService.class, this.common.getRoomService(), this, ServicePriority.Normal);
    sm.register(VoiceBroadcastService.class, this.common.getBroadcastService(), this, ServicePriority.Normal);
    sm.register(VoicePersistenceService.class, this.common.getPersistenceService(), this, ServicePriority.Normal);
    sm.register(VoiceSpeechService.class, this.common.getSpeechService(), this, ServicePriority.Normal);
    sm.register(VoiceService.class, this.common.getVoiceService(), this, ServicePriority.Normal);
  }

}
