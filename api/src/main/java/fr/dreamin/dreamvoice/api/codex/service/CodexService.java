package fr.dreamin.dreamvoice.api.codex.service;

import fr.dreamin.dreamvoice.api.codex.model.Codex;
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

}
