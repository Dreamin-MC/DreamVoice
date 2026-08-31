package fr.dreamin.dreamvoice.api.codex.model;

import fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@Getter
@NoArgsConstructor
public final class Codex {

  private double distance = 16.0;
  private VoiceWall voiceWall;

  public record VoiceWall(
    boolean enabled,
    @Nullable VoiceWallMode mode,
    @Nullable Boolean airDamping,
    @Nullable Double globalMultiplier,
    @Nullable Double defaultAttenuation,
    @Nullable Map<String, Double> categories,
    @Nullable Map<String, Double> overrides,
    @Nullable DiffractionConfig diffraction,
    @Nullable SoundMaterials soundMaterials
  ) {

    public @NotNull VoiceWallMode getEffectiveMode() {
      if (this.mode != null)
        return this.mode;
      return this.enabled ? VoiceWallMode.REALISTIC : VoiceWallMode.OFF;
    }

    public @NotNull DiffractionConfig getDiffractionConfig() {
      if (this.diffraction != null)
        return this.diffraction;
      return DiffractionConfig.defaults();
    }

    public double getMultiplier() {
      return (this.globalMultiplier != null && this.globalMultiplier > 0.0) ? this.globalMultiplier : 1.0;
    }

    public double getDefaultAttenuationDb() {
      if (this.defaultAttenuation != null)
        return this.defaultAttenuation;
      if (this.soundMaterials != null && this.soundMaterials.defaultAttenuation > 0)
        return this.soundMaterials.defaultAttenuation;
      return 15.0;
    }

    public double getAttenuationDb(final @NotNull String materialName) {
      final var mult = getMultiplier();
      final var matUpper = materialName.toUpperCase();

      // 1. Direct block override
      if (this.overrides != null && this.overrides.containsKey(matUpper)) {
        return this.overrides.get(matUpper) * mult;
      }

      // Legacy soundMaterials check
      if (this.soundMaterials != null && this.soundMaterials.materialAttenuation != null && this.soundMaterials.materialAttenuation.containsKey(matUpper)) {
        return this.soundMaterials.materialAttenuation.get(matUpper) * mult;
      }

      // 2. Smart automatic category matching
      final var catAttenuation = resolveCategoryAttenuation(matUpper);
      if (catAttenuation != null) {
        return catAttenuation * mult;
      }

      // 3. Default fallback
      return getDefaultAttenuationDb() * mult;
    }

    private @Nullable Double resolveCategoryAttenuation(final @NotNull String mat) {
      if (this.categories == null) {
        return getDefaultCategoryValue(mat);
      }

      final var catKey = determineCategoryKey(mat);
      if (catKey != null && this.categories.containsKey(catKey)) {
        return this.categories.get(catKey);
      }

      return getDefaultCategoryValue(mat);
    }

    private @Nullable String determineCategoryKey(final @NotNull String mat) {
      if (mat.contains("GLASS") || mat.contains("PANE") || mat.contains("BEACON"))
        return "glass";
      if (mat.contains("PLANKS") || mat.contains("LOG") || mat.contains("WOOD") || mat.contains("FENCE")
        || mat.contains("GATE") || (mat.contains("DOOR") && !mat.contains("IRON")) || mat.contains("TRAPDOOR")
        || mat.contains("BARREL") || mat.contains("CHEST") || mat.contains("BOOKSHELF"))
        return "wood";
      if (mat.contains("WOOL") || mat.contains("CARPET") || mat.contains("BANNER") || mat.contains("BED"))
        return "wool";
      if (mat.contains("IRON") || mat.contains("GOLD") || mat.contains("COPPER") || mat.contains("NETHERITE")
        || mat.contains("ANVIL") || mat.contains("HOPPER") || mat.contains("CHAIN") || mat.contains("CAULDRON"))
        return "metal";
      if (mat.contains("LEAVES") || mat.contains("VINE") || mat.contains("BUSH") || mat.contains("GRASS_BLOCK") || mat.contains("MOSS"))
        return "foliage";
      if (mat.contains("DIRT") || mat.contains("SAND") || mat.contains("GRAVEL") || mat.contains("CLAY")
        || mat.contains("MUD") || mat.contains("SOUL_") || mat.contains("FARMLAND"))
        return "earth";
      if (mat.contains("WATER") || mat.contains("LAVA"))
        return "water";
      if (mat.contains("STONE") || mat.contains("DEEPSLATE") || mat.contains("BRICK") || mat.contains("CONCRETE")
        || mat.contains("TERRACOTTA") || mat.contains("ANDESITE") || mat.contains("DIORITE") || mat.contains("GRANITE")
        || mat.contains("BASALT") || mat.contains("BLACKSTONE") || mat.contains("OBSIDIAN") || mat.contains("ORE")
        || mat.contains("END_STONE") || mat.contains("NETHERRACK") || mat.contains("PRISMARINE") || mat.contains("SANDSTONE")
        || mat.contains("QUARTZ") || mat.contains("COBBLESTONE") || mat.contains("CALCITE") || mat.contains("TUFF"))
        return "stone";

      return null;
    }

    private @Nullable Double getDefaultCategoryValue(final @NotNull String mat) {
      final var key = determineCategoryKey(mat);
      if (key == null) return null;
      return switch (key) {
        case "glass" -> 6.0;
        case "wood" -> 10.0;
        case "foliage" -> 3.0;
        case "earth" -> 8.0;
        case "wool" -> 18.0;
        case "water" -> 20.0;
        case "stone" -> 25.0;
        case "metal" -> 35.0;
        default -> 15.0;
      };
    }

  }

  public record DiffractionConfig(
    boolean enabled,
    double maxBypassWidth,
    double maxBypassHeight,
    double maxPathDistance,
    double diffractionLossDb,
    double lossPerMeter
  ) {
    public static DiffractionConfig defaults() {
      return new DiffractionConfig(true, 2.5, 2.5, 14.0, 4.0, 1.2);
    }
  }

  public record SoundMaterials(
    Map<String, Double> materialAttenuation,
    double defaultAttenuation
  ) {
    public double getAttenuationDb(final @NotNull String material) {
      if (this.materialAttenuation == null)
        return this.defaultAttenuation;
      return this.materialAttenuation.getOrDefault(material, this.defaultAttenuation);
    }
  }

}
