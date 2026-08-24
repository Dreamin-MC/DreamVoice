package fr.dreamin.dreamvoice.api.codex.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@Getter
@NoArgsConstructor
public final class Codex {

  private double distance;
  private VoiceWall voiceWall;

  public record VoiceWall(boolean enabled, SoundMaterials soundMaterials) {}

  public record SoundMaterials(
    Map<String, Double> materialAttenuation,
    double defaultAttenuation
  ) {

    // ###############################################################
    // ----------------------- PUBLIC METHODS ------------------------
    // ###############################################################

    public double getAttenuationDb(final @NotNull String material) {
      if (this.materialAttenuation == null)
        return this.defaultAttenuation;
      return this.materialAttenuation.getOrDefault(material, this.defaultAttenuation);
    }

  }

}

