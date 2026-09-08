package fr.dreamin.dreamvoice.api.codex.model;

import fr.dreamin.dreamvoice.api.wall.model.WallConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Root configuration container for DreamVoice managing active modules.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public final class Codex {

  private Map<String, Boolean> modules = new HashMap<>();
  private @Nullable WallConfig voiceWall;

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  public boolean isModuleEnabled(final @NotNull String moduleName) {
    if (this.modules == null || this.modules.isEmpty())
      return true;

    return this.modules.getOrDefault(moduleName.toLowerCase(), true);
  }

  public double getEffectiveDistance() {
    return this.voiceWall != null ? this.voiceWall.getEffectiveDistance() : 16.0;
  }

  public double getEffectiveDistance(final double fallback) {
    return this.voiceWall != null ? this.voiceWall.getEffectiveDistance(fallback) : fallback;
  }

  // ###############################################################
  // ----------------------- STATIC METHODS ------------------------
  // ###############################################################

  public static class DiffractionConfig {
    public static WallConfig.DiffractionConfig defaults() {
      return WallConfig.DiffractionConfig.defaults();
    }
  }

}
