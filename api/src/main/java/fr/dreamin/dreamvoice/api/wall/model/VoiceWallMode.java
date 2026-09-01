package fr.dreamin.dreamvoice.api.wall.model;

/**
 * Operating mode for VoiceWall sound occlusion and acoustic physics.
 */
public enum VoiceWallMode {
  /**
   * Complete sound cut-off (100% blocked) whenever a solid obstacle separates players without an open air path.
   * Ideal for Danganronpa, Murder mystery, and RP investigation games with isolated rooms.
   */
  STRICT_BLOCK,

  /**
   * Realistic acoustic attenuation based on block material categories (wood, stone, glass, metal, etc.).
   */
  REALISTIC,

  /**
   * Wall occlusion disabled (standard Simple Voice Chat behavior).
   */
  OFF
}
