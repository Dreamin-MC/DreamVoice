package fr.dreamin.dreamvoice.common.codex.service;

import fr.dreamin.dreamvoice.api.codex.model.Codex;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.wall.model.WallConfig;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.utils.JsonUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;

/**
 * Platform-independent implementation of {@link CodexService} managing DreamVoice modular config loading and synchronization.
 */
@Getter
public final class CodexServiceImpl implements CodexService {

  private final @NotNull VoicePlatform platform;
  private final @NotNull VoiceWallService voiceWallService;
  private @NotNull Codex codex;
  private @NotNull WallConfig wallConfig;

  public CodexServiceImpl(final @NotNull VoicePlatform platform, final @NotNull VoiceWallService voiceWallService) {
    this.platform = platform;
    this.voiceWallService = voiceWallService;
    load();
  }

  @Override
  public void load() {
    this.platform.logInfo("Loading configuration (config.json)...");
    final var configFile = new File(this.platform.getDataDirectory().toFile(), "config.json");
    Codex loaded = null;
    if (configFile.exists()) {
      try {
        loaded = JsonUtils.load(configFile, Codex.class);
      } catch (Exception e) {
        this.platform.logWarning("Failed to load config.json: " + e.getMessage());
      }
    }
    this.codex = loaded != null ? loaded : new Codex();

    if (this.codex.getModules() == null || this.codex.getModules().isEmpty()) {
      final var defaultModules = new HashMap<String, Boolean>();
      defaultModules.put("wall", true);
      defaultModules.put("speaker", true);
      defaultModules.put("record", true);
      defaultModules.put("radio", true);
      defaultModules.put("wiretap", true);
      defaultModules.put("projection", true);
      defaultModules.put("transmitter", true);
      defaultModules.put("broadcast", true);
      this.codex.setModules(defaultModules);
      try {
        JsonUtils.save(configFile, this.codex);
      } catch (Exception e) {
        this.platform.logWarning("Failed to save default config.json: " + e.getMessage());
      }
    }

    loadWallConfig();

    this.codex.setVoiceWall(this.wallConfig);

    if (this.wallConfig == null)
      return;

    final var wallEnabled = this.codex.isModuleEnabled("wall") && this.wallConfig.isEnabled();
    this.voiceWallService.setEnable(wallEnabled);
    this.voiceWallService.setMode(this.wallConfig.getEffectiveMode());
    if (this.wallConfig.getAirDamping() != null)
      this.voiceWallService.setAirDampingEnabled(this.wallConfig.getAirDamping());

    this.platform.logInfo("Configuration loaded successfully (mode=" + this.voiceWallService.getMode() + ", active=" + this.voiceWallService.isEnable() + ").");
  }

  @Override
  public @NotNull Codex getConfig() {
    return this.codex;
  }

  @Override
  public @NotNull WallConfig getWallConfig() {
    return this.wallConfig;
  }

  private void loadWallConfig() {
    final var wallDir = new File(this.platform.getDataDirectory().toFile(), "modules/wall");
    if (!wallDir.exists())
      wallDir.mkdirs();

    final var wallConfigFile = new File(wallDir, "config.json");
    if (wallConfigFile.exists()) {
      try {
        final var loadedWall = JsonUtils.load(wallConfigFile, WallConfig.class);
        this.wallConfig = loadedWall != null ? loadedWall : new WallConfig();
        return;
      } catch (Exception e) {
        this.platform.logWarning("Failed to load modules/wall/config.json, using defaults: " + e.getMessage());
      }
    }

    this.wallConfig = new WallConfig();
    try {
      JsonUtils.save(wallConfigFile, this.wallConfig);
    } catch (Exception e) {
      this.platform.logWarning("Failed to create default modules/wall/config.json: " + e.getMessage());
    }
  }

}
