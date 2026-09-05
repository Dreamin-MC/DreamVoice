package fr.dreamin.dreamvoice.api.codex.model;

import fr.dreamin.dreamvoice.api.wall.model.VoiceWallMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Root configuration container for DreamVoice settings and VoiceWall acoustic properties.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public final class Codex {

  private double distance = 16.0;
  private VoiceWall voiceWall;

  /**
   * VoiceWall acoustic engine configuration.
   *
   * @param enabled            whether VoiceWall is enabled globally
   * @param mode               the configured occlusion mode
   * @param airDamping         whether high-frequency distance absorption is active
   * @param globalMultiplier   server-wide soundproofing multiplier
   * @param defaultAttenuation fallback dB attenuation for unclassified solid blocks
   * @param categories         dB attenuation values per material category
   * @param overrides          explicit block-specific dB overrides
   * @param diffraction        acoustic obstacle bypass and aperture pathfinding settings
   * @param soundMaterials     legacy material mapping container
   */
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

    /**
     * Resolves the effective VoiceWall mode based on explicit mode and enabled flag.
     *
     * @return the resolved {@link VoiceWallMode}
     */
    public @NotNull VoiceWallMode getEffectiveMode() {
      if (!this.enabled)
        return VoiceWallMode.OFF;
      if (this.mode != null && this.mode != VoiceWallMode.OFF)
        return this.mode;
      return VoiceWallMode.REALISTIC;
    }

    /**
     * Resolves the active diffraction configuration.
     *
     * @return the active {@link DiffractionConfig}
     */
    public @NotNull DiffractionConfig getDiffractionConfig() {
      if (this.diffraction != null)
        return this.diffraction;
      return DiffractionConfig.defaults();
    }

    /**
     * Gets the effective global soundproofing multiplier.
     *
     * @return the multiplier value (defaults to 1.0)
     */
    public double getMultiplier() {
      return (this.globalMultiplier != null && this.globalMultiplier > 0.0) ? this.globalMultiplier : 1.0;
    }

    /**
     * Gets the fallback dB attenuation for unclassified blocks.
     *
     * @return the default attenuation in dB
     */
    public double getDefaultAttenuationDb() {
      if (this.defaultAttenuation != null)
        return this.defaultAttenuation;
      if (this.soundMaterials != null && this.soundMaterials.defaultAttenuation > 0)
        return this.soundMaterials.defaultAttenuation;
      return 15.0;
    }

    /**
     * Calculates the total dB attenuation for a specific Minecraft material.
     *
     * @param materialName the name of the material
     * @return the total attenuation in dB
     */
    public double getAttenuationDb(final @NotNull String materialName) {
      final var mult = getMultiplier();
      final var matUpper = materialName.toUpperCase();

      if (this.overrides != null && this.overrides.containsKey(matUpper))
        return this.overrides.get(matUpper) * mult;

      if (this.soundMaterials != null && this.soundMaterials.materialAttenuation != null && this.soundMaterials.materialAttenuation.containsKey(matUpper))
        return this.soundMaterials.materialAttenuation.get(matUpper) * mult;

      final var catAttenuation = resolveCategoryAttenuation(matUpper);
      if (catAttenuation != null)
        return catAttenuation * mult;

      return getDefaultAttenuationDb() * mult;
    }

    private @Nullable Double resolveCategoryAttenuation(final @NotNull String mat) {
      if (this.categories == null)
        return getDefaultCategoryValue(mat);

      final var catKey = determineCategoryKey(mat);
      if (catKey != null && this.categories.containsKey(catKey))
        return this.categories.get(catKey);

      return getDefaultCategoryValue(mat);
    }

    private static @Nullable String determineCategoryKey(final @NotNull String mat) {
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

    private static @Nullable Double getDefaultCategoryValue(final @NotNull String mat) {
      final var key = determineCategoryKey(mat);
      if (key == null)
        return null;
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

  /**
   * Acoustic diffraction and air aperture pathfinding parameters.
   */
  public record DiffractionConfig(
    boolean enabled,
    double maxBypassWidth,
    double maxBypassHeight,
    double maxPathDistance,
    double diffractionLossDb,
    double lossPerMeter
  ) {
    /**
     * Default diffraction configuration.
     */
    public static DiffractionConfig defaults() {
      return new DiffractionConfig(true, 2.5, 2.5, 14.0, 4.0, 1.2);
    }
  }

  /**
   * Legacy sound material container.
   */
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
