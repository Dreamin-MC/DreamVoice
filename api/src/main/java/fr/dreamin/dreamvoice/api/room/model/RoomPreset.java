package fr.dreamin.dreamvoice.api.room.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable acoustic preset defining soundproofing isolation, spatial reverb,
 * and automatic voice filters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomPreset {

  private @NotNull String id;

  private @NotNull String name;

  /**
   * Sound isolation percentage from outside sound (0 to 100).
   * 100% means completely soundproof.
   */
  @Builder.Default
  private int isolationPct = 100;

  /**
   * Spatial reverberation acoustics inside this room.
   */
  @Builder.Default
  private @NotNull RoomReverbConfig reverb = new RoomReverbConfig();

  /**
   * Identifiers of voice filters automatically applied to players inside this room.
   */
  @Builder.Default
  private @NotNull List<String> filters = new ArrayList<>();

}
