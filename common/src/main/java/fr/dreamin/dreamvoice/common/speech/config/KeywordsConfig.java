package fr.dreamin.dreamvoice.common.speech.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import fr.dreamin.dreamvoice.api.speech.model.KeywordDefinition;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public final class KeywordsConfig {

  @JsonProperty("keywords")
  private List<KeywordEntry> keywords = new ArrayList<>();

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class KeywordEntry {
    @JsonProperty("id")
    private String id;

    @JsonProperty("words")
    private List<String> words = new ArrayList<>();

    @JsonProperty("permission")
    private String permission;

    public KeywordDefinition toDefinition() {
      return new KeywordDefinition(
        this.id != null ? this.id : "unknown",
        this.words != null ? this.words : List.of(),
        this.permission
      );
    }
  }

}
