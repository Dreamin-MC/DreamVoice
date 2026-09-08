package fr.dreamin.dreamvoice.core.codex.service;

import fr.dreamin.dreamapi.api.config.Configurations;
import fr.dreamin.dreamvoice.api.codex.model.Codex;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.wall.model.WallConfig;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.HashMap;

/**
 * Implementation of {@link CodexService} managing DreamVoice modular config loading and synchronization.
 */
@Getter
public final class CodexServiceImpl implements CodexService {

  private final @NotNull DreamVoice plugin;
  private final @NotNull VoiceWallService voiceWallService;
  private @NotNull Codex codex;
  private @NotNull WallConfig wallConfig;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public CodexServiceImpl(final @NotNull DreamVoice plugin, final @NotNull VoiceWallService voiceWallService) {
    this.plugin = plugin;
    this.voiceWallService = voiceWallService;
    load();
  }

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public void load() {
    this.plugin.getLogger().info("Loading configuration (config.json)...");
    final var loaded = Configurations.loadConfig(this.plugin, Codex.class);
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
      this.codex.setModules(defaultModules);
      try {
        Configurations.saveConfig(this.plugin, this.codex);
      } catch (Exception e) {
        this.plugin.getLogger().warning("Failed to save default config.json: " + e.getMessage());
      }
    }

    loadWallConfig();

    this.codex.setVoiceWall(this.wallConfig);

    final var wallEnabled = this.codex.isModuleEnabled("wall") && this.wallConfig.isEnabled();
    this.voiceWallService.setEnable(wallEnabled);
    this.voiceWallService.setMode(this.wallConfig.getEffectiveMode());
    if (this.wallConfig.getAirDamping() != null)
      this.voiceWallService.setAirDampingEnabled(this.wallConfig.getAirDamping());

    this.plugin.getLogger().info("Configuration loaded successfully (mode=" + this.voiceWallService.getMode() + ", active=" + this.voiceWallService.isEnable() + ").");
  }

  @Override
  public @NonNull Codex getConfig() {
    return this.codex;
  }

  @Override
  public @NotNull WallConfig getWallConfig() {
    return this.wallConfig;
  }

  // ###############################################################
  // ----------------------- PRIVATE METHODS -----------------------
  // ###############################################################

  private void loadWallConfig() {
    final var wallDir = new File(this.plugin.getDataFolder(), "modules/wall");
    if (!wallDir.exists())
      wallDir.mkdirs();

    final var wallConfigFile = new File(wallDir, "config.json");
    if (wallConfigFile.exists()) {
      try {
        final var loadedWall = Configurations.loadJson(wallConfigFile, WallConfig.class);
        this.wallConfig = loadedWall != null ? loadedWall : new WallConfig();
        return;
      } catch (Exception e) {
        this.plugin.getLogger().warning("Failed to load modules/wall/config.json, using defaults: " + e.getMessage());
      }
    }

    this.wallConfig = new WallConfig();
    try {
      Configurations.saveJson(wallConfigFile, this.wallConfig);
    } catch (Exception e) {
      this.plugin.getLogger().warning("Failed to create default modules/wall/config.json: " + e.getMessage());
    }
  }

}
