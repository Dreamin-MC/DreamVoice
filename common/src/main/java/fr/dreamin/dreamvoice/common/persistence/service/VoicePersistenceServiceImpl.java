package fr.dreamin.dreamvoice.common.persistence.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.dreamin.dreamvoice.api.broadcast.service.VoiceBroadcastService;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.persistence.model.ModulePersistenceConfig;
import fr.dreamin.dreamvoice.api.persistence.service.VoicePersistenceService;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
import fr.dreamin.dreamvoice.api.room.service.VoiceRoomService;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.projection.storage.ProjectionsPersistence;
import fr.dreamin.dreamvoice.common.radio.storage.RadiosPersistence;
import fr.dreamin.dreamvoice.common.speaker.storage.SpeakersPersistence;
import fr.dreamin.dreamvoice.common.transmitter.storage.TransmittersPersistence;
import fr.dreamin.dreamvoice.common.utils.JsonUtils;
import fr.dreamin.dreamvoice.common.wiretap.storage.WiretapsPersistence;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Platform-independent implementation of {@link VoicePersistenceService} coordinating modular JSON persistence.
 */
public final class VoicePersistenceServiceImpl implements VoicePersistenceService {

  private static final String[] PERSISTENT_MODULES = { "speaker", "wiretap", "projection", "radio", "transmitter", "room", "broadcast" };

  private final @NotNull VoicePlatform platform;
  private final @NotNull File modulesDir;
  private final @NotNull Map<String, ModulePersistenceConfig> moduleConfigs = new ConcurrentHashMap<>();
  private final @NotNull Map<String, ScheduledFuture<?>> autoSaveTasks = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    final var thread = new Thread(r, "DreamVoice-Persistence");
    thread.setDaemon(true);
    return thread;
  });

  public VoicePersistenceServiceImpl(final @NotNull VoicePlatform platform) {
    this.platform = platform;
    this.modulesDir = new File(platform.getDataDirectory().toFile(), "modules");
    if (!this.modulesDir.exists())
      this.modulesDir.mkdirs();

    migrateLegacyData();
    reloadModuleConfigs();
  }

  @Override
  public void saveAll() {
    this.platform.logInfo("Saving DreamVoice persistent data...");
    if (isModuleActive("speaker") && getModuleConfig("speaker").isSaveOnStop())
      saveSpeakers();

    if (isModuleActive("wiretap") && getModuleConfig("wiretap").isSaveOnStop())
      saveWiretaps();

    if (isModuleActive("projection") && getModuleConfig("projection").isSaveOnStop())
      saveProjections();

    if (isModuleActive("radio") && getModuleConfig("radio").isSaveOnStop())
      saveRadios();

    if (isModuleActive("transmitter") && getModuleConfig("transmitter").isSaveOnStop())
      saveTransmitters();

    if (isModuleActive("room") && getModuleConfig("room").isSaveOnStop())
      saveRooms();

    if (isModuleActive("broadcast") && getModuleConfig("broadcast").isSaveOnStop())
      saveBroadcasts();

    this.platform.logInfo("DreamVoice persistent data saved successfully.");
  }

  @Override
  public void loadAll() {
    this.platform.logInfo("Loading all DreamVoice persistent data...");
    reloadModuleConfigs();

    if (isModuleActive("speaker"))
      loadSpeakers();

    if (isModuleActive("wiretap"))
      loadWiretaps();

    if (isModuleActive("projection"))
      loadProjections();

    if (isModuleActive("radio"))
      loadRadios();

    if (isModuleActive("transmitter"))
      loadTransmitters();

    if (isModuleActive("room"))
      loadRooms();

    if (isModuleActive("broadcast"))
      loadBroadcasts();

    startAutoSaveTasks();

    this.platform.logInfo("All DreamVoice data loaded successfully.");
  }

  @Override
  public @NotNull ModulePersistenceConfig getModuleConfig(final @NotNull String moduleName) {
    return this.moduleConfigs.computeIfAbsent(moduleName.toLowerCase(), this::loadModuleConfigFromDisk);
  }

  @Override
  public void reloadModuleConfigs() {
    for (final var module : PERSISTENT_MODULES)
      this.moduleConfigs.put(module, loadModuleConfigFromDisk(module));
  }

  @Override
  public void startAutoSaveTasks() {
    cancelAutoSaveTasks();

    for (final var module : PERSISTENT_MODULES) {
      if (!isModuleActive(module))
        continue;

      final var config = getModuleConfig(module);
      if (!config.isAutoSave())
        continue;

      final var intervalTicks = config.getAutoSaveIntervalTicks();
      if (intervalTicks <= 0)
        continue;

      final var intervalSeconds = Math.max(1, intervalTicks / 20);
      final var future = this.scheduler.scheduleAtFixedRate(() -> {
        switch (module) {
          case "speaker" -> saveSpeakers();
          case "wiretap" -> saveWiretaps();
          case "projection" -> saveProjections();
          case "radio" -> saveRadios();
          case "transmitter" -> saveTransmitters();
          case "room" -> saveRooms();
        }
      }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

      this.autoSaveTasks.put(module, future);
    }
  }

  @Override
  public void cancelAutoSaveTasks() {
    for (final var task : this.autoSaveTasks.values()) {
      if (task != null)
        task.cancel(false);
    }
    this.autoSaveTasks.clear();
  }

  @Override
  public void saveSpeakers() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceSpeakerService.class);
    if (service != null)
      SpeakersPersistence.save(service, getModuleDir("speaker"), this.platform);
  }

  @Override
  public void loadSpeakers() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceSpeakerService.class);
    if (service != null) {
      service.unregisterAll();
      SpeakersPersistence.load(getModuleDir("speaker"), this.platform);
    }
  }

  @Override
  public void saveSpeaker(final @NotNull UUID uuid) {
    saveSpeakers();
  }

  @Override
  public void saveWiretaps() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceWiretapService.class);
    if (service != null)
      WiretapsPersistence.save(service, getModuleDir("wiretap"), this.platform);
  }

  @Override
  public void loadWiretaps() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceWiretapService.class);
    if (service != null)
      WiretapsPersistence.load(service, getModuleDir("wiretap"), this.platform);
  }

  @Override
  public void saveProjections() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceProjectionService.class);
    if (service != null)
      ProjectionsPersistence.save(service, getModuleDir("projection"), this.platform);
  }

  @Override
  public void loadProjections() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceProjectionService.class);
    if (service != null) {
      service.clearProjections();
      ProjectionsPersistence.load(service, getModuleDir("projection"), this.platform);
    }
  }

  @Override
  public void saveRadios() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceRadioService.class);
    if (service != null)
      RadiosPersistence.save(service, getModuleDir("radio"), this.platform);
  }

  @Override
  public void loadRadios() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceRadioService.class);
    if (service != null)
      RadiosPersistence.load(service, getModuleDir("radio"), this.platform);
  }

  @Override
  public void saveTransmitters() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceTransmitterService.class);
    if (service != null)
      TransmittersPersistence.save(service, getModuleDir("transmitter"), this.platform);
  }

  @Override
  public void loadTransmitters() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceTransmitterService.class);
    if (service != null)
      TransmittersPersistence.load(service, getModuleDir("transmitter"), this.platform);
  }

  @Override
  public void saveRooms() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceRoomService.class);
    if (service != null)
      service.save();
  }

  @Override
  public void loadRooms() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceRoomService.class);
    if (service != null)
      service.reload();
  }

  @Override
  public void saveBroadcasts() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceBroadcastService.class);
    if (service != null)
      service.save();
  }

  @Override
  public void loadBroadcasts() {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return;
    final var service = common.getService(VoiceBroadcastService.class);
    if (service != null)
      service.load();
  }

  private @NotNull ModulePersistenceConfig loadModuleConfigFromDisk(final @NotNull String moduleName) {
    final var moduleDir = getModuleDir(moduleName);
    final var configFile = new File(moduleDir, "config.json");

    if (configFile.exists()) {
      try {
        final var mapper = JsonUtils.MAPPER;
        final var rootNode = mapper.readTree(configFile);
        if (rootNode != null && rootNode.has("persistence")) {
          final var config = mapper.treeToValue(rootNode.get("persistence"), ModulePersistenceConfig.class);
          if (config != null)
            return config;
        }
      } catch (Exception e) {
        this.platform.logWarning("Failed to parse persistence block in " + configFile.getPath() + ": " + e.getMessage());
      }
    }

    final var defaultConfig = new ModulePersistenceConfig();
    saveDefaultModuleConfig(configFile, defaultConfig);
    return defaultConfig;
  }

  private void saveDefaultModuleConfig(final @NotNull File configFile, final @NotNull ModulePersistenceConfig persistenceConfig) {
    try {
      final var mapper = JsonUtils.MAPPER;
      ObjectNode rootNode;
      if (configFile.exists()) {
        final var existing = mapper.readTree(configFile);
        rootNode = (existing instanceof ObjectNode on) ? on : mapper.createObjectNode();
      } else
        rootNode = mapper.createObjectNode();

      if (!rootNode.has("persistence")) {
        rootNode.set("persistence", mapper.valueToTree(persistenceConfig));
        mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, rootNode);
      }
    } catch (Exception e) {
      this.platform.logWarning("Failed to save default persistence config to " + configFile.getPath() + ": " + e.getMessage());
    }
  }

  private @NotNull File getModuleDir(final @NotNull String moduleName) {
    final var dir = new File(this.modulesDir, moduleName);
    if (!dir.exists())
      dir.mkdirs();

    return dir;
  }

  private void migrateLegacyData() {
    final var oldDataDir = new File(this.platform.getDataDirectory().toFile(), "data");
    if (oldDataDir.exists() && oldDataDir.isDirectory()) {
      migrateFile(new File(oldDataDir, "speakers.json"), new File(getModuleDir("speaker"), "data.json"));
      migrateFile(new File(oldDataDir, "radios.json"), new File(getModuleDir("radio"), "data.json"));
      migrateFile(new File(oldDataDir, "wiretaps.json"), new File(getModuleDir("wiretap"), "data.json"));
      migrateFile(new File(oldDataDir, "projections.json"), new File(getModuleDir("projection"), "data.json"));
      migrateFile(new File(oldDataDir, "transmitters.json"), new File(getModuleDir("transmitter"), "data.json"));

      final var remaining = oldDataDir.listFiles();
      if (remaining == null || remaining.length == 0)
        oldDataDir.delete();
    }

    final var oldRecordingsDir = new File(this.platform.getDataDirectory().toFile(), "recordings");
    final var newRecordingsDir = new File(getModuleDir("record"), "recordings");
    migrateDir(oldRecordingsDir, newRecordingsDir);

    final var oldExportsDir = new File(this.platform.getDataDirectory().toFile(), "exports");
    final var newExportsDir = new File(getModuleDir("record"), "exports");
    migrateDir(oldExportsDir, newExportsDir);
  }

  private void migrateFile(final @NotNull File source, final @NotNull File target) {
    if (!source.exists())
      return;

    final var parent = target.getParentFile();
    if (!parent.exists())
      parent.mkdirs();

    if (!target.exists()) {
      try {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        this.platform.logInfo("Migrated " + source.getName() + " -> " + target.getPath());
      } catch (Exception e) {
        this.platform.logWarning("Failed to migrate " + source.getName() + ": " + e.getMessage());
      }
    }
  }

  private void migrateDir(final @NotNull File sourceDir, final @NotNull File targetDir) {
    if (!sourceDir.exists() || !sourceDir.isDirectory())
      return;

    if (!targetDir.exists())
      targetDir.mkdirs();

    final var files = sourceDir.listFiles();
    if (files != null) {
      for (final var file : files) {
        if (file.isFile()) {
          try {
            final var dest = new File(targetDir, file.getName());
            if (!dest.exists())
              Files.move(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
          } catch (Exception e) {
            this.platform.logWarning("Failed to move " + file.getName() + " to " + targetDir.getPath() + ": " + e.getMessage());
          }
        }
      }
    }

    final var remaining = sourceDir.listFiles();
    if (remaining == null || remaining.length == 0)
      sourceDir.delete();
  }

  private boolean isModuleActive(final @NotNull String moduleName) {
    final var common = DreamVoiceCommon.getInstance();
    if (common == null)
      return true;
    final var codexService = common.getService(CodexService.class);
    if (codexService == null)
      return true;

    return codexService.isModuleEnabled(moduleName);
  }

}
