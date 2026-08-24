package fr.dreamin.dreaminvoice.api.codex.service;

import fr.dreamin.dreaminvoice.api.codex.model.Codex;
import org.jetbrains.annotations.NotNull;

public interface CodexService {

  void load();

  @NotNull Codex getConfig();

}
