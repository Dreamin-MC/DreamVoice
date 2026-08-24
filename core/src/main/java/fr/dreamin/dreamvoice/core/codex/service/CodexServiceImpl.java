package fr.dreamin.dreamvoice.core.codex.service;

import fr.dreamin.dreamapi.api.config.Configurations;
import fr.dreamin.dreamvoice.api.codex.model.Codex;
import fr.dreamin.dreamvoice.api.codex.service.CodexService;
import fr.dreamin.dreamvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

@Getter
@RequiredArgsConstructor
public final class CodexServiceImpl implements CodexService {

  private final @NotNull DreamVoice plugin;

  private @NotNull Codex codex;

  public CodexServiceImpl(final @NotNull DreamVoice plugin, final @NotNull VoiceWallService voiceWallService) {
    this.plugin = plugin;
    load();

    if (this.codex.getVoiceWall() != null && this.codex.getVoiceWall().enabled())
      voiceWallService.setEnable(true);
  }

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public void load() {
    this.plugin.getLogger().info("Loading configuration...");
    this.codex = Configurations.loadConfig(this.plugin, Codex.class);
    this.plugin.getLogger().info("Configuration loaded successfully.");
  }

  @Override
  public @NonNull Codex getConfig() {
    return this.codex;
  }

}

