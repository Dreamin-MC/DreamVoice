package fr.dreamin.dreamvoice.api.wall.model;

/**
 * Operating mode for VoiceWall sound occlusion.
 */
public enum VoiceWallMode {
  /**
   * Complete sound cut-off (100% blocked) whenever any wall or solid obstacle separates players.
   * Ideal for Danganronpa, Murder mystery, and RP games with isolated rooms.
   */
  STRICT_BLOCK,

  /**
   * Realistic acoustic attenuation based on block material categories (wood, stone, glass, etc.).
   */
  REALISTIC,

  /**
   * Wall occlusion disabled (vanilla Simple Voice Chat behavior).
   */
  OFF
}
