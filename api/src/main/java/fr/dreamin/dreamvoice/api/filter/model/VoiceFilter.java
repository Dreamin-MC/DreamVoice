package fr.dreamin.dreamvoice.api.filter.model;

import fr.dreamin.dreamvoice.api.player.model.VPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface VoiceFilter {

  @NotNull String getId();

  @NotNull String getName();

  default int getPriority() {
    return 0;
  }

  short[] process(final @NotNull short[] samples, final @Nullable VPlayer player);

  default void resetState(final @NotNull UUID playerUuid) {
  }

}
