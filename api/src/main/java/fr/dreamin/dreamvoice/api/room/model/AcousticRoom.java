package fr.dreamin.dreamvoice.api.room.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an active acoustic zone or soundproof room bounded by one or more {@link VoiceCuboid}s.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcousticRoom {

  private @NotNull String id;

  private @NotNull String name;

  /**
   * Reference preset ID (from room config.json).
   */
  @Builder.Default
  private @NotNull String presetId = "default";

  /**
   * Optional custom isolation override (null uses preset value).
   */
  private @Nullable Integer isolationPctOverride;

  /**
   * Optional custom reverb override (null uses preset value).
   */
  private @Nullable RoomReverbConfig reverbOverride;

  /**
   * Additional voice filters attached specifically to this room.
   */
  @Builder.Default
  private @NotNull List<String> additionalFilters = new ArrayList<>();

  /**
   * VoiceCuboids defining the bounding volume of this acoustic room.
   */
  @Builder.Default
  private @NotNull List<VoiceCuboid> cuboids = new ArrayList<>();

}
