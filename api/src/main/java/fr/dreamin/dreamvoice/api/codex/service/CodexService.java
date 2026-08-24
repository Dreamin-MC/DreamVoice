package fr.dreamin.dreamvoice.api.codex.service;

import fr.dreamin.dreamvoice.api.codex.model.Codex;
import org.jetbrains.annotations.NotNull;

public interface CodexService {

  void load();

  @NotNull Codex getConfig();

}
