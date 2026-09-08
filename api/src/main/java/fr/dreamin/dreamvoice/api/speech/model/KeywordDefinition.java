package fr.dreamin.dreamvoice.api.speech.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Model representing a keyword target and its spoken variants/aliases.
 *
 * @param id         unique key of the keyword action
 * @param words      list of words or phrases that trigger this keyword
 * @param permission optional permission required by the player to trigger
 */
public record KeywordDefinition(
  @NotNull String id,
  @NotNull List<String> words,
  @Nullable String permission
) {
}
