package fr.dreamin.dreamvoice.api.item;

import fr.dreamin.dreamapi.api.item.ItemHandler;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

public final class VoiceItemHandler {

  private VoiceItemHandler() {}

  public static @NotNull ItemHandler addFilter(final @NotNull String filterId) {
    return ctx -> new VoiceItemContext(ctx).addFilter(filterId);
  }

  public static @NotNull ItemHandler removeFilter(final @NotNull String filterId) {
    return ctx -> new VoiceItemContext(ctx).removeFilter(filterId);
  }

  public static @NotNull ItemHandler toggleFilter(final @NotNull String filterId) {
    return ctx -> new VoiceItemContext(ctx).toggleFilter(filterId);
  }

  public static @NotNull ItemHandler timedFilter(final @NotNull String filterId, final @NotNull Duration duration) {
    return ctx -> new VoiceItemContext(ctx).timedFilter(filterId, duration);
  }

  public static @NotNull ItemHandler connectRadio(final @NotNull String frequency) {
    return ctx -> new VoiceItemContext(ctx).connectRadio(frequency);
  }

  public static @NotNull ItemHandler disconnectRadio() {
    return ctx -> new VoiceItemContext(ctx).disconnectRadio();
  }

  public static @NotNull ItemHandler of(final @NotNull Function<VoiceItemContext, Boolean> handler) {
    return ctx -> handler.apply(new VoiceItemContext(ctx));
  }

  public static @NotNull ItemHandler of(final @NotNull Consumer<VoiceItemContext> consumer) {
    return ctx -> {
      consumer.accept(new VoiceItemContext(ctx));
      return true;
    };
  }

}
