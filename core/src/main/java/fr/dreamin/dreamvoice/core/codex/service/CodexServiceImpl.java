package fr.dreamin.dreamvoice.core.codex.service;

import fr.dreamin.dreamapi.api.config.Configurations;
import fr.dreamin.dreamvoice.api.codex.model.Codex;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

/**
 * Implementation of {@link CodexService} managing DreamVoice config loading and synchronization.
 */
@Getter
public final class CodexServiceImpl implements CodexService {

  // ###############################################################
  // --------------------- INSTANCE FIELDS -------------------------
  // ###############################################################

  private final @NotNull DreamVoice plugin;
  private final @NotNull VoiceWallService voiceWallService;
  private @NotNull Codex codex;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public CodexServiceImpl(final @NotNull DreamVoice plugin, final @NotNull VoiceWallService voiceWallService) {
    this.plugin = plugin;
    this.voiceWallService = voiceWallService;
    load();
  }

  // ###############################################################
  // ------------------- PUBLIC SERVICE METHODS --------------------
  // ###############################################################

  @Override
  public void load() {
    this.plugin.getLogger().info("Loading configuration (config.json)...");
    final var loaded = Configurations.loadConfig(this.plugin, Codex.class);
    this.codex = loaded != null ? loaded : new Codex();

    if (this.codex.getVoiceWall() != null) {
      final var vw = this.codex.getVoiceWall();
      this.voiceWallService.setEnable(vw.enabled());
      this.voiceWallService.setMode(vw.getEffectiveMode());
      if (vw.airDamping() != null)
        this.voiceWallService.setAirDampingEnabled(vw.airDamping());
    }

    this.plugin.getLogger().info("Configuration loaded successfully (mode=" + this.voiceWallService.getMode() + ", active=" + this.voiceWallService.isEnable() + ").");
  }

  @Override
  public @NonNull Codex getConfig() {
    return this.codex;
  }

}
