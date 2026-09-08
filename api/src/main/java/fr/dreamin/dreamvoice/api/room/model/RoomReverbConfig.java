package fr.dreamin.dreamvoice.api.room.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Spatial reverberation parameters applied to voices inside an acoustic room.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RoomReverbConfig {

  @Builder.Default
  private boolean enabled = false;

  /**
   * Decay time factor (0.0 to 1.0).
   */
  @Builder.Default
  private float decay = 0.4f;

  /**
   * Room size factor (delay in ms, typically 50 to 500ms).
   */
  @Builder.Default
  private int roomSizeMs = 120;

  /**
   * Wet mix ratio (0.0 = completely dry, 1.0 = fully wet reverb).
   */
  @Builder.Default
  private float wetGain = 0.35f;

}
