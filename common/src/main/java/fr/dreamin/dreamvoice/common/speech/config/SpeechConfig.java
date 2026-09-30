package fr.dreamin.dreamvoice.common.speech.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public final class SpeechConfig {

  @JsonProperty("enabled")
  private boolean enabled = true;

  @JsonProperty("active_model")
  private String activeModel = "vosk-model-small-en-us-0.15";

  @JsonProperty("keywords")
  private KeywordsSection keywords = new KeywordsSection();

  @JsonProperty("transcription")
  private TranscriptionSection transcription = new TranscriptionSection();

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class KeywordsSection {
    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("debug")
    private boolean debug = false;

    @JsonProperty("cooldown_seconds")
    private double cooldownSeconds = 2.0;

    @JsonProperty("min_energy_threshold")
    private double minEnergyThreshold = 100.0;

    @JsonProperty("silence_reset_ms")
    private long silenceResetMs = 600L;
  }

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class TranscriptionSection {
    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("mode")
    private String mode = "LOCAL"; // "LOCAL" or "SIDECAR"

    @JsonProperty("sidecar")
    private SidecarSection sidecar = new SidecarSection();

    @JsonProperty("stations")
    private StationsSection stations = new StationsSection();
  }

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class SidecarSection {
    @JsonProperty("url")
    private String url = "http://127.0.0.1:8000/transcribe";

    @JsonProperty("timeout_seconds")
    private int timeoutSeconds = 15;

    @JsonProperty("fallback_to_local")
    private boolean fallbackToLocal = true;
  }

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class StationsSection {
    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("allowed_blocks")
    private List<String> allowedBlocks = new ArrayList<>(List.of("LECTERN", "JUKEBOX"));

    @JsonProperty("processing_time_seconds")
    private int processingTimeSeconds = 3;

    @JsonProperty("cooldown_seconds")
    private int cooldownSeconds = 5;

    @JsonProperty("put_on_lectern")
    private boolean putOnLectern = true;

    @JsonProperty("sound_effect")
    private String soundEffect = "block.lever.click";

    @JsonProperty("particles")
    private String particles = "CRIT";

    @JsonProperty("analysis_display")
    private AnalysisDisplaySection analysisDisplay = new AnalysisDisplaySection();

    @JsonProperty("book")
    private BookSection book = new BookSection();
  }

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class AnalysisDisplaySection {
    @JsonProperty("type")
    private String type = "ACTION_BAR"; // "ACTION_BAR", "CHAT", "TITLE"

    @JsonProperty("message_key")
    private String messageKey = "dreamvoice.speech.analyzing";

    @JsonProperty("default_text")
    private String defaultText = "<yellow>⚡ Analyzing audio cassette...</yellow>";

    @JsonProperty("title_subtitle_key")
    private String titleSubtitleKey = "dreamvoice.speech.analyzing_subtitle";

    @JsonProperty("default_subtitle")
    private String defaultSubtitle = "<gray>Please wait...</gray>";
  }

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static final class BookSection {
    @JsonProperty("title_key")
    private String titleKey = "dreamvoice.speech.book_title";

    @JsonProperty("default_title")
    private String defaultTitle = "Report: {id}";

    @JsonProperty("author")
    private String author = "Analysis Station";

    @JsonProperty("header_key")
    private String headerKey = "dreamvoice.speech.book_header";

    @JsonProperty("default_header")
    private String defaultHeader = "<bold><gold>=== AUDIO REPORT ===</gold></bold>\n<gray>Tape:</gray> <yellow>{id}</yellow>\n<gray>Duration:</gray> <aqua>{duration}s</aqua>\n<gray>Author:</gray> <yellow>{author}</yellow>\n-------------------";
  }

}
