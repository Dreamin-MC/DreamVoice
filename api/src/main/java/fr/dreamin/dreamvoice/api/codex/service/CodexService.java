package fr.dreamin.dreamvoice.api.codex.service;

import fr.dreamin.dreamvoice.api.codex.model.Codex;
import fr.dreamin.dreamvoice.api.wall.model.WallConfig;
import org.jetbrains.annotations.NotNull;

/**
 * Service responsible for loading and providing the global DreamVoice configuration.
 */
public interface CodexService {

  /**
   * Reloads the configuration from disk.
   */
  void load();

  /**
   * Retrieves the active configuration container.
   *
   * @return the active {@link Codex} instance
   */
  @NotNull Codex getConfig();

  /**
   * Retrieves the acoustic Wall configuration.
   *
   * @return the active {@link WallConfig}
   */
  @NotNull WallConfig getWallConfig();

  /**
   * Checks if a module is enabled.
   *
   * @param moduleName the module identifier
   * @return {@code true} if enabled
   */
  default boolean isModuleEnabled(final @NotNull String moduleName) {
    return getConfig().isModuleEnabled(moduleName);
  }

}
