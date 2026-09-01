package fr.dreamin.dreamvoice.api.speaker.model;

/**
 * Access mode for 3D locational speakers.
 */
public enum SpeakerMode {
  /**
   * Any nearby player can broadcast their voice through the speaker.
   */
  GLOBAL,
  /**
   * Only explicitly linked players can broadcast their voice.
   */
  RESTRICTED
}
