package fr.dreamin.dreamvoice.api.speech.event;

import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.speech.model.KeywordDefinition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Event fired when a player speaks a recognized keyword into their microphone.
 */
@Getter
@RequiredArgsConstructor
public final class VoiceKeywordSpokenEvent extends VoiceEvent {

  private final @NotNull UUID playerUuid;
  private final @NotNull KeywordDefinition keyword;
  private final @NotNull String matchedWord;
  private final float confidence;

  public VoiceKeywordSpokenEvent(final @NotNull UUID playerUuid, final @NotNull KeywordDefinition keyword, final @NotNull String matchedWord) {
    this(playerUuid, keyword, matchedWord, 1.0f);
  }

}
