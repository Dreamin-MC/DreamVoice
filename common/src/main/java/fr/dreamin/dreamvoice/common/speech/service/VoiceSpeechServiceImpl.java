package fr.dreamin.dreamvoice.common.speech.service;

import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.speech.event.TranscriptionCompleteEvent;
import fr.dreamin.dreamvoice.api.speech.event.VoiceKeywordSpokenEvent;
import fr.dreamin.dreamvoice.api.speech.model.KeywordDefinition;
import fr.dreamin.dreamvoice.api.speech.model.SpeechModelInfo;
import fr.dreamin.dreamvoice.api.speech.model.SpeechTranscriptionResult;
import fr.dreamin.dreamvoice.api.speech.service.VoiceSpeechService;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.common.speech.config.KeywordsConfig;
import fr.dreamin.dreamvoice.common.speech.config.SpeechConfig;
import fr.dreamin.dreamvoice.common.speech.model.VoskModelRegistry;
import fr.dreamin.dreamvoice.common.utils.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Platform-independent implementation of {@link VoiceSpeechService} managing
 * real-time Vosk speech recognition, keyword spotting, and transcription stations.
 */
public final class VoiceSpeechServiceImpl implements VoiceSpeechService {

  private final @NotNull VoicePlatform platform;
  private final @NotNull File speechDir;
  private final @NotNull File configFile;
  private final @NotNull File keywordsFile;
  private final @NotNull File modelsDir;

  private SpeechConfig config = new SpeechConfig();
  private KeywordsConfig keywordsConfig = new KeywordsConfig();

  private Model activeVoskModel = null;
  private String loadedModelId = null;
  private volatile String cachedGrammarJson = null;

  private final Map<UUID, Recognizer> playerKeywordRecognizers = new ConcurrentHashMap<>();
  private final Map<UUID, Long> stationCooldowns = new ConcurrentHashMap<>();
  private final Map<UUID, Long> playerKeywordCooldowns = new ConcurrentHashMap<>();
  private final Map<UUID, Long> playerLastAudioTime = new ConcurrentHashMap<>();
  private final Set<UUID> debugSubscribers = ConcurrentHashMap.newKeySet();
  private volatile boolean globalDebug = false;

  public VoiceSpeechServiceImpl(final @NotNull VoicePlatform platform) {
    this.platform = platform;
    this.speechDir = new File(platform.getDataDirectory().toFile(), "modules/speech");
    this.configFile = new File(this.speechDir, "config.json");
    this.keywordsFile = new File(this.speechDir, "keywords.json");
    this.modelsDir = new File(this.speechDir, "models");

    try {
      org.vosk.LibVosk.setLogLevel(LogLevel.WARNINGS);
    } catch (Throwable ignored) {}

    loadConfigs();
    loadActiveModelAsync();
  }

  private void loadConfigs() {
    if (!this.speechDir.exists())
      this.speechDir.mkdirs();
    if (!this.modelsDir.exists())
      this.modelsDir.mkdirs();

    if (!this.configFile.exists()) {
      this.config = new SpeechConfig();
      saveConfigFile();
    } else {
      try {
        this.config = JsonUtils.load(this.configFile, SpeechConfig.class);
        if (this.config == null)
          this.config = new SpeechConfig();
      } catch (Exception e) {
        this.platform.logError("Failed to load speech config.json: " + e.getMessage(), e);
        this.config = new SpeechConfig();
      }
    }

    this.globalDebug = this.config.getKeywords().isDebug();

    if (!this.keywordsFile.exists()) {
      try (final var in = VoiceSpeechServiceImpl.class.getClassLoader().getResourceAsStream("modules/speech/keywords.json")) {
        if (in != null)
          Files.copy(in, this.keywordsFile.toPath());
      } catch (Exception ignored) {}
    }

    if (!this.keywordsFile.exists()) {
      this.keywordsConfig = new KeywordsConfig();

      final var defaultHello = new KeywordsConfig.KeywordEntry();
      defaultHello.setId("hello");
      defaultHello.setWords(List.of("hello", "bonjour", "hey", "salut"));

      final var defaultHelp = new KeywordsConfig.KeywordEntry();
      defaultHelp.setId("help");
      defaultHelp.setWords(List.of("help", "help me", "aide", "au secours", "mayday"));
      defaultHelp.setPermission("dreamvoice.keyword.help");

      final var defaultOpen = new KeywordsConfig.KeywordEntry();
      defaultOpen.setId("open");
      defaultOpen.setWords(List.of("open sesame", "open door", "ouvre toi", "ouvre la porte"));

      this.keywordsConfig.getKeywords().addAll(List.of(defaultHello, defaultHelp, defaultOpen));
      saveKeywordsFile();
    } else {
      try {
        this.keywordsConfig = JsonUtils.load(this.keywordsFile, KeywordsConfig.class);
        if (this.keywordsConfig == null)
          this.keywordsConfig = new KeywordsConfig();
      } catch (Exception e) {
        this.platform.logError("Failed to load speech keywords.json: " + e.getMessage(), e);
        this.keywordsConfig = new KeywordsConfig();
      }
    }

    updateCachedGrammar();
  }

  private void updateCachedGrammar() {
    this.cachedGrammarJson = buildKeywordsGrammarJson();
  }

  private void saveConfigFile() {
    try {
      JsonUtils.save(this.configFile, this.config);
    } catch (Exception e) {
      this.platform.logWarning("Failed to save speech config.json: " + e.getMessage());
    }
  }

  private void saveKeywordsFile() {
    try {
      JsonUtils.save(this.keywordsFile, this.keywordsConfig);
    } catch (Exception e) {
      this.platform.logWarning("Failed to save speech keywords.json: " + e.getMessage());
    }
  }

  private void loadActiveModelAsync() {
    if (!this.config.isEnabled())
      return;

    this.platform.runAsync(() -> {
      final var modelId = this.config.getActiveModel();
      final var modelFolder = new File(this.modelsDir, modelId);

      if (!modelFolder.exists()) {
        this.platform.logInfo("Vosk model '" + modelId + "' not found locally. Auto-downloading...");
        try {
          downloadModelSync(modelId, null);
        } catch (Exception e) {
          this.platform.logWarning("Could not auto-download model '" + modelId + "': " + e.getMessage());
          return;
        }
      }

      if (modelFolder.exists()) {
        try {
          this.platform.logInfo("Loading Vosk speech model '" + modelId + "' into memory...");
          final var model = new Model(modelFolder.getAbsolutePath());
          synchronized (this) {
            if (this.activeVoskModel != null)
              this.activeVoskModel.close();
            this.activeVoskModel = model;
            this.loadedModelId = modelId;
            closeAllRecognizers();
          }
          this.platform.logInfo("Vosk speech model '" + modelId + "' loaded successfully!");
        } catch (Exception e) {
          this.platform.logError("Failed to initialize Vosk model: " + e.getMessage(), e);
        }
      }
    });
  }

  private void closeAllRecognizers() {
    for (final var r : this.playerKeywordRecognizers.values()) {
      try {
        r.close();
      } catch (Throwable ignored) {}
    }
    this.playerKeywordRecognizers.clear();
  }

  @Override
  public @NotNull CompletableFuture<SpeechTranscriptionResult> transcribeRecording(final @NotNull VoiceRecording recording) {
    final var common = DreamVoiceCommon.getInstance();
    final var recordingService = common != null ? common.getService(VoiceRecordingService.class) : null;
    if (recordingService == null)
      return CompletableFuture.failedFuture(new IllegalStateException("VoiceRecordingService unavailable"));

    final var pcm = recordingService.decodeToPcm(recording);
    if (pcm == null || pcm.length == 0)
      return CompletableFuture.completedFuture(new SpeechTranscriptionResult(recording.getUuid(), recording.getSpeakerUUID(), recording.getDurationSeconds(), "", List.of()));

    return transcribePcm(pcm, recording.getSpeakerUUID()).thenApply(result ->
      new SpeechTranscriptionResult(recording.getUuid(), recording.getSpeakerUUID(), recording.getDurationSeconds(), result.fullText(), result.segments())
    );
  }

  @Override
  public @NotNull CompletableFuture<SpeechTranscriptionResult> transcribePcm(final short @NotNull [] pcm, final @Nullable UUID speakerUuid) {
    if ("SIDECAR".equalsIgnoreCase(this.config.getTranscription().getMode()))
      return transcribeViaSidecar(pcm, speakerUuid);

    return transcribeViaLocalVosk(pcm, speakerUuid);
  }

  private @NotNull CompletableFuture<SpeechTranscriptionResult> transcribeViaLocalVosk(final short @NotNull [] pcm, final @Nullable UUID speakerUuid) {
    return CompletableFuture.supplyAsync(() -> {
      synchronized (this) {
        if (this.activeVoskModel == null)
          throw new IllegalStateException("No active Vosk model loaded.");

        try (final var recognizer = new Recognizer(this.activeVoskModel, 48000.0f)) {
          recognizer.setWords(true);
          final var pcmBytes = new byte[pcm.length * 2];
          ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm);

          final var chunkSize = 4096;
          var offset = 0;
          while (offset < pcmBytes.length) {
            final var length = Math.min(chunkSize, pcmBytes.length - offset);
            recognizer.acceptWaveForm(pcmBytes, length);
            offset += length;
          }

          final var jsonResult = recognizer.getFinalResult();
          return parseVoskJsonResult(jsonResult, speakerUuid, pcm.length / 48000.0);
        } catch (final IOException e) {
          throw new RuntimeException("Vosk recognizer failed: " + e.getMessage(), e);
        }
      }
    });
  }

  private @NotNull CompletableFuture<SpeechTranscriptionResult> transcribeViaSidecar(final short @NotNull [] pcm, final @Nullable UUID speakerUuid) {
    final var sidecarConfig = this.config.getTranscription().getSidecar();
    return CompletableFuture.supplyAsync(() -> {
      try {
        final var client = HttpClient.newHttpClient();
        final var pcmBytes = new byte[pcm.length * 2];
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm);

        final var request = HttpRequest.newBuilder()
          .uri(URI.create(sidecarConfig.getUrl()))
          .header("Content-Type", "application/octet-stream")
          .POST(HttpRequest.BodyPublishers.ofByteArray(pcmBytes))
          .build();

        final var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
          final var root = JsonUtils.MAPPER.readTree(response.body());
          final var text = root.has("text") ? root.get("text").asText() : "";
          return new SpeechTranscriptionResult(null, speakerUuid, (float) (pcm.length / 48000.0), text, List.of());
        }

        if (sidecarConfig.isFallbackToLocal())
          return transcribeViaLocalVosk(pcm, speakerUuid).join();

        throw new IOException("Sidecar returned status " + response.statusCode());
      } catch (Exception e) {
        if (sidecarConfig.isFallbackToLocal())
          return transcribeViaLocalVosk(pcm, speakerUuid).join();
        throw new RuntimeException("Transcription sidecar failed: " + e.getMessage(), e);
      }
    });
  }

  private SpeechTranscriptionResult parseVoskJsonResult(final String json, final @Nullable UUID speakerUuid, final double duration) {
    try {
      final var root = JsonUtils.MAPPER.readTree(json);
      final var text = root.has("text") ? root.get("text").asText() : "";
      return new SpeechTranscriptionResult(null, speakerUuid, (float) duration, text, List.of());
    } catch (Exception e) {
      return new SpeechTranscriptionResult(null, speakerUuid, (float) duration, "", List.of());
    }
  }

  @Override
  public @NotNull CompletableFuture<File> downloadModel(final @NotNull String langCode, final @Nullable Consumer<Double> progressConsumer) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return downloadModelSync(langCode, progressConsumer);
      } catch (Exception e) {
        throw new RuntimeException("Model download failed: " + e.getMessage(), e);
      }
    });
  }

  private File downloadModelSync(final @NotNull String langOrId, final @Nullable Consumer<Double> progressConsumer) throws Exception {
    final var modelInfo = VoskModelRegistry.findByLangOrId(langOrId);
    if (modelInfo == null)
      throw new IllegalArgumentException("Unknown model or language code: " + langOrId);

    final var targetDir = new File(this.modelsDir, modelInfo.id());
    if (targetDir.exists())
      return targetDir;

    final var tempZip = new File(this.modelsDir, modelInfo.id() + ".zip");
    final var client = HttpClient.newHttpClient();
    final var request = HttpRequest.newBuilder().uri(URI.create(modelInfo.downloadUrl())).build();
    final var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

    try (final var in = new BufferedInputStream(response.body());
         final var fos = new FileOutputStream(tempZip)) {
      final var buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1)
        fos.write(buffer, 0, read);
    }

    // Extract zip
    try (final var zipIn = new ZipInputStream(Files.newInputStream(tempZip.toPath()))) {
      ZipEntry entry;
      while ((entry = zipIn.getNextEntry()) != null) {
        final var file = new File(this.modelsDir, entry.getName());
        if (entry.isDirectory())
          file.mkdirs();
        else {
          file.getParentFile().mkdirs();
          try (final var fos = new FileOutputStream(file)) {
            final var buffer = new byte[8192];
            int len;
            while ((len = zipIn.read(buffer)) != -1)
              fos.write(buffer, 0, len);
          }
        }
      }
    }

    Files.deleteIfExists(tempZip.toPath());
    return targetDir;
  }

  @Override
  public @NotNull List<String> getInstalledModels() {
    if (!this.modelsDir.exists())
      return List.of();
    final var files = this.modelsDir.listFiles(File::isDirectory);
    if (files == null)
      return List.of();
    final var names = new ArrayList<String>();
    for (final var f : files)
      names.add(f.getName());
    return names;
  }

  @Override
  public @NotNull List<SpeechModelInfo> getCloudModels() {
    return VoskModelRegistry.getAllModels();
  }

  @Override
  public @NotNull String getActiveModel() {
    return this.loadedModelId != null ? this.loadedModelId : this.config.getActiveModel();
  }

  @Override
  public boolean setActiveModel(final @NotNull String modelId) {
    this.config.setActiveModel(modelId);
    saveConfigFile();
    loadActiveModelAsync();
    return true;
  }

  @Override
  public boolean isStationBlock(final @NotNull String blockId) {
    if (!this.config.isEnabled() || !this.config.getTranscription().getStations().isEnabled())
      return false;

    return this.config.getTranscription().getStations().getAllowedBlocks().stream()
      .anyMatch(b -> b.equalsIgnoreCase(blockId));
  }

  @Override
  public void startStationProcess(final @NotNull UUID playerUuid, final @NotNull VoiceLocation stationLocation, final @NotNull VoiceRecording recording) {
    final var stationsConfig = this.config.getTranscription().getStations();
    final var cooldownMs = stationsConfig.getCooldownSeconds() * 1000L;
    final var now = System.currentTimeMillis();
    final var lastUsed = this.stationCooldowns.getOrDefault(playerUuid, 0L);

    if (now - lastUsed < cooldownMs) {
      final var remainingSec = Math.ceil((cooldownMs - (now - lastUsed)) / 1000.0);
      this.platform.sendMessage(playerUuid, "speech.station.cooldown", remainingSec);
      return;
    }
    this.stationCooldowns.put(playerUuid, now);

    this.platform.sendMessage(playerUuid, "speech.station.analyzing");
    this.platform.playSoundEffect(stationLocation, stationsConfig.getSoundEffect(), 1.0f, 1.0f);

    final var processingSec = stationsConfig.getProcessingTimeSeconds();

    transcribeRecording(recording).thenAccept(result -> {
      this.platform.runLater(() -> {
        new TranscriptionCompleteEvent(playerUuid, result).callEvent();
        this.platform.sendMessage(playerUuid, "speech.station.complete");
      }, Math.max(1L, processingSec * 20L));
    }).exceptionally(err -> {
      this.platform.runLater(() -> {
        this.platform.sendMessage(playerUuid, "speech.station.failed", err.getMessage());
      }, 1L);
      return null;
    });
  }

  @Override
  public @NotNull List<KeywordDefinition> getRegisteredKeywords() {
    final var list = new ArrayList<KeywordDefinition>();
    for (final var k : this.keywordsConfig.getKeywords())
      list.add(k.toDefinition());
    return Collections.unmodifiableList(list);
  }

  @Override
  public void reload() {
    loadConfigs();
    loadActiveModelAsync();
  }

  @Override
  public boolean isDebugEnabled(final @NotNull UUID playerUuid) {
    return this.debugSubscribers.contains(playerUuid);
  }

  @Override
  public void setDebugEnabled(final @NotNull UUID playerUuid, final boolean enabled) {
    if (enabled)
      this.debugSubscribers.add(playerUuid);
    else
      this.debugSubscribers.remove(playerUuid);
  }

  @Override
  public boolean toggleDebug(final @NotNull UUID playerUuid) {
    if (this.debugSubscribers.contains(playerUuid)) {
      this.debugSubscribers.remove(playerUuid);
      return false;
    }
    this.debugSubscribers.add(playerUuid);
    return true;
  }

  @Override
  public boolean isGlobalDebugEnabled() {
    return this.globalDebug;
  }

  @Override
  public void setGlobalDebugEnabled(final boolean enabled) {
    this.globalDebug = enabled;
  }

  @Override
  public boolean toggleGlobalDebug() {
    this.globalDebug = !this.globalDebug;
    return this.globalDebug;
  }

  private String buildKeywordsGrammarJson() {
    final var words = new ArrayList<String>();
    for (final var entry : this.keywordsConfig.getKeywords()) {
      if (entry.getWords() != null)
        words.addAll(entry.getWords());
    }
    words.add("[unk]");
    try {
      return JsonUtils.MAPPER.writeValueAsString(words);
    } catch (Exception e) {
      return "[\"[unk]\"]";
    }
  }

  public void onMicrophone(final @NotNull MicrophonePacketEvent event) {
    if (!this.config.isEnabled() || !this.config.getKeywords().isEnabled() || this.activeVoskModel == null)
      return;

    final var sender = event.getSenderConnection();
    if (sender == null)
      return;

    final var senderUuid = sender.getPlayer().getUuid();
    final var opusData = event.getPacket().getOpusEncodedData();
    if (opusData == null || opusData.length == 0)
      return;

    final var common = DreamVoiceCommon.getInstance();
    final var voiceService = common != null ? common.getService(VoiceService.class) : null;
    if (voiceService == null)
      return;

    try {
      final var decoder = voiceService.getDecoder(senderUuid);
      final var pcm = decoder.decode(opusData);
      if (pcm == null || pcm.length == 0)
        return;

      final var energy = computeEnergy(pcm);
      if (energy < this.config.getKeywords().getMinEnergyThreshold())
        return;

      var recognizer = this.playerKeywordRecognizers.get(senderUuid);
      if (recognizer == null) {
        recognizer = (this.cachedGrammarJson != null)
          ? new Recognizer(this.activeVoskModel, 48000.0f, this.cachedGrammarJson)
          : new Recognizer(this.activeVoskModel, 48000.0f);
        this.playerKeywordRecognizers.put(senderUuid, recognizer);
      }

      final var pcmBytes = new byte[pcm.length * 2];
      ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm);

      if (recognizer.acceptWaveForm(pcmBytes, pcmBytes.length)) {
        final var result = recognizer.getResult();
        handleSpokenKeywords(senderUuid, result);
      }
    } catch (Exception ignored) {}
  }

  private double computeEnergy(final short[] pcm) {
    long sum = 0;
    for (final short s : pcm)
      sum += (long) s * s;
    return Math.sqrt((double) sum / pcm.length);
  }

  private void handleSpokenKeywords(final @NotNull UUID senderUuid, final @NotNull String json) {
    try {
      final var root = JsonUtils.MAPPER.readTree(json);
      final var text = root.has("text") ? root.get("text").asText().trim() : "";
      if (text.isBlank())
        return;

      for (final var entry : this.keywordsConfig.getKeywords()) {
        if (entry.getWords() != null) {
          for (final var w : entry.getWords()) {
            if (text.toLowerCase().contains(w.toLowerCase())) {
              new VoiceKeywordSpokenEvent(senderUuid, entry.toDefinition(), text).callEvent();
              return;
            }
          }
        }
      }
    } catch (Exception ignored) {}
  }

  public void onPlayerDisconnect(final @NotNull UUID uuid) {
    this.stationCooldowns.remove(uuid);
    this.playerKeywordCooldowns.remove(uuid);
    this.playerLastAudioTime.remove(uuid);
    this.debugSubscribers.remove(uuid);
    final var recognizer = this.playerKeywordRecognizers.remove(uuid);
    if (recognizer != null) {
      try {
        recognizer.close();
      } catch (Throwable ignored) {}
    }
  }

}
