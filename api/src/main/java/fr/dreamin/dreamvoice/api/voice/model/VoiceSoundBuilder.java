package fr.dreamin.dreamvoice.api.voice.model;

import de.maxhenkel.voicechat.api.ServerPlayer;
import fr.dreamin.dreamapi.api.DreamAPI;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import lombok.Builder;
import lombok.Getter;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Fluent builder for creating and playing raw audio streams through Simple Voice Chat.
 */
@Builder
@Getter
public final class VoiceSoundBuilder {

  private final byte[] rawAudioData;
  private @Nullable Location location;
  @Builder.Default
  private float distance = 16F;
  private @Nullable Runnable onStopped;
  private @Nullable Predicate<ServerPlayer> playerFilter;

  @Builder.Default
  private boolean loop = false;

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  /**
   * Dispatches and plays this configured sound through {@link VoiceService}.
   */
  public void play() {
    Objects.requireNonNull(DreamAPI.getAPI().getService(VoiceService.class), "VoiceService is unavailable")
      .playSound(this);
  }

}
