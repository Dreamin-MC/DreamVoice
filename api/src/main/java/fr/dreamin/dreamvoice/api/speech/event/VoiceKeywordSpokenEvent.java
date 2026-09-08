package fr.dreamin.dreamvoice.api.speech.event;

import fr.dreamin.dreamvoice.api.speech.model.KeywordDefinition;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired when a player speaks a recognized keyword into their microphone.
 */
@Getter
public final class VoiceKeywordSpokenEvent extends Event {

  private static final HandlerList HANDLERS = new HandlerList();

  private final @NotNull Player player;
  private final @NotNull KeywordDefinition keyword;
  private final @NotNull String matchedWord;
  private final float confidence;

  public VoiceKeywordSpokenEvent(
    final @NotNull Player player,
    final @NotNull KeywordDefinition keyword,
    final @NotNull String matchedWord,
    final float confidence
  ) {
    super(false);
    this.player = player;
    this.keyword = keyword;
    this.matchedWord = matchedWord;
    this.confidence = confidence;
  }

  @Override
  public @NotNull HandlerList getHandlers() {
    return HANDLERS;
  }

  public static @NotNull HandlerList getHandlerList() {
    return HANDLERS;
  }

}
