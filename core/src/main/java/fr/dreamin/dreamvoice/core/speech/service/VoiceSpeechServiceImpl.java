package fr.dreamin.dreamvoice.core.speech.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.dreamin.dreamapi.api.config.Configurations;
import fr.dreamin.dreamapi.api.lang.service.LangService;
import fr.dreamin.dreamapi.plugin.DreamPlugin;
import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import fr.dreamin.dreamvoice.api.recording.service.VoiceRecordingService;
import fr.dreamin.dreamvoice.api.speech.event.TranscriptionCompleteEvent;
import fr.dreamin.dreamvoice.api.speech.event.TranscriptionStationStartEvent;
import fr.dreamin.dreamvoice.api.speech.event.VoiceKeywordSpokenEvent;
import fr.dreamin.dreamvoice.api.speech.model.KeywordDefinition;
import fr.dreamin.dreamvoice.api.speech.model.SpeechModelInfo;
import fr.dreamin.dreamvoice.api.speech.model.SpeechTranscriptionResult;
import fr.dreamin.dreamvoice.api.speech.service.VoiceSpeechService;
import fr.dreamin.dreamvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.api.voice.service.VoiceService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.recording.item.CassetteItem;
import fr.dreamin.dreamvoice.core.speech.config.KeywordsConfig;
import fr.dreamin.dreamvoice.core.speech.config.SpeechConfig;
import fr.dreamin.dreamvoice.core.speech.model.VoskModelRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Lectern;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class VoiceSpeechServiceImpl implements VoiceSpeechService, Listener {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final MiniMessage MM = MiniMessage.miniMessage();

  private final @NotNull DreamVoice plugin;
  private final @NotNull File speechDir;
  private final @NotNull File configFile;
  private final @NotNull File keywordsFile;
  private final @NotNull File modelsDir;

  private SpeechConfig config = new SpeechConfig();
  private KeywordsConfig keywordsConfig = new KeywordsConfig();

  private Model activeVoskModel = null;
  private String loadedModelId = null;
  private volatile String cachedGrammarJson = null;

  // Realtime keyword spotter recognizers per player UUID
  private final Map<UUID, Recognizer> playerKeywordRecognizers = new ConcurrentHashMap<>();
  private final Map<UUID, Long> stationCooldowns = new ConcurrentHashMap<>();
  private final Map<UUID, Long> playerKeywordCooldowns = new ConcurrentHashMap<>();
  private final Map<UUID, Long> playerLastAudioTime = new ConcurrentHashMap<>();
  private final Set<UUID> debugSubscribers = ConcurrentHashMap.newKeySet();
  private volatile boolean globalDebug = false;

  public VoiceSpeechServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;
    this.speechDir = new File(plugin.getDataFolder(), "modules/speech");
    this.configFile = new File(this.speechDir, "config.json");
    this.keywordsFile = new File(this.speechDir, "keywords.json");
    this.modelsDir = new File(this.speechDir, "models");

    try {
      org.vosk.LibVosk.setLogLevel(LogLevel.WARNINGS);
    } catch (Throwable ignored) {}

    loadConfigs();
    loadActiveModelAsync();

    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  // ###############################################################
  // ------------------- CONFIGURATION LOADING ---------------------
  // ###############################################################

  private void loadConfigs() {
    if (!this.speechDir.exists())
      this.speechDir.mkdirs();
    if (!this.modelsDir.exists())
      this.modelsDir.mkdirs();

    if (!this.configFile.exists()) {
      this.config = new SpeechConfig();
      saveConfigFile();
    }
    else {
      try {
        this.config = Configurations.loadJson(this.configFile, new TypeReference<>() {});
        if (this.config == null)
          this.config = new SpeechConfig();
      } catch (Exception e) {
        this.plugin.getLogger().severe("Failed to load speech config.json: " + e.getMessage());
        this.config = new SpeechConfig();
      }
    }

    this.globalDebug = this.config.getKeywords().isDebug();

    if (!this.keywordsFile.exists()) {
      try (final var in = this.plugin.getResource("modules/speech/keywords.json")) {
        if (in != null)
          java.nio.file.Files.copy(in, this.keywordsFile.toPath());
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
    }
    else {
      try {
        this.keywordsConfig = Configurations.loadJson(this.keywordsFile, new TypeReference<>() {});
        if (this.keywordsConfig == null)
          this.keywordsConfig = new KeywordsConfig();
      } catch (Exception e) {
        this.plugin.getLogger().severe("Failed to load speech keywords.json: " + e.getMessage());
        this.keywordsConfig = new KeywordsConfig();
      }
    }

    updateCachedGrammar();
  }

  private void updateCachedGrammar() {
    this.cachedGrammarJson = buildKeywordsGrammarJson();
    updateGrammarAcrossRecognizers();
  }

  private @Nullable String buildKeywordsGrammarJson() {
    final var definitions = getRegisteredKeywords();
    if (definitions.isEmpty())
      return null;

    final var phrases = new LinkedHashSet<String>();
    for (final var def : definitions) {
      for (final var word : def.words()) {
        final var trimmed = word.trim().toLowerCase();
        if (!trimmed.isEmpty())
          phrases.add(trimmed);
      }
    }

    if (phrases.isEmpty())
      return null;

    phrases.add("[unk]");

    try {
      return MAPPER.writeValueAsString(phrases);
    } catch (Exception e) {
      this.plugin.getLogger().warning("[Speech] Failed to serialize grammar JSON: " + e.getMessage());
      return null;
    }
  }

  private void updateGrammarAcrossRecognizers() {
    // Vosk C++ throws kaldi::KaldiFatalError if setGrammar() is called on an active recognizer.
    // Safely close existing recognizers and clear the map so they are recreated on the next audio frame.
    final var old = new ArrayList<>(this.playerKeywordRecognizers.values());
    this.playerKeywordRecognizers.clear();
    for (final var recognizer : old) {
      try {
        recognizer.close();
      } catch (Throwable ignored) {}
    }
  }

  private void saveConfigFile() {
    try {
      Configurations.saveJson(this.configFile, this.config);
    } catch (Exception e) {
      this.plugin.getLogger().severe("Failed to save speech config.json: " + e.getMessage());
    }
  }

  private void saveKeywordsFile() {
    try {
      Configurations.saveJson(this.keywordsFile, this.keywordsConfig);
    } catch (Exception e) {
      this.plugin.getLogger().severe("Failed to save speech keywords.json: " + e.getMessage());
    }
  }

  // ###############################################################
  // ------------------- VOSK MODEL MANAGEMENT ---------------------
  // ###############################################################

  private synchronized void loadActiveModelAsync() {
    final var targetModel = this.config.getActiveModel();
    if (targetModel == null || targetModel.isBlank())
      return;

    CompletableFuture.runAsync(() -> {
      final var modelFolder = new File(this.modelsDir, targetModel);
      if (!modelFolder.exists() || !modelFolder.isDirectory()) {
        this.plugin.getLogger().info("[Speech] Active model folder '" + targetModel + "' not found in " + this.modelsDir.getPath() + ". Use '/voice speech download <lang>' to download it.");
        return;
      }

      try {
        this.plugin.getLogger().info("[Speech] Loading Vosk model from " + modelFolder.getName() + "...");
        final var newModel = new Model(modelFolder.getAbsolutePath());
        synchronized (this) {
          if (this.activeVoskModel != null) {
            try {
              this.activeVoskModel.close();
            } catch (Throwable ignored) {}
          }
          this.activeVoskModel = newModel;
          this.loadedModelId = targetModel;
          this.playerKeywordRecognizers.values().forEach(r -> {
            try {
              r.close();
            } catch (Throwable ignored) {}
          });
          this.playerKeywordRecognizers.clear();
        }
        this.plugin.getLogger().info("[Speech] Model '" + targetModel + "' loaded successfully!");
      } catch (Exception e) {
        this.plugin.getLogger().severe("[Speech] Failed to load Vosk model '" + targetModel + "': " + e.getMessage());
      }
    });
  }

  @Override
  public @NotNull CompletableFuture<File> downloadModel(final @NotNull String langCode, final @Nullable Consumer<Double> progressConsumer) {
    return CompletableFuture.supplyAsync(() -> {
      final var info = VoskModelRegistry.findByLangOrId(langCode);
      if (info == null)
        throw new IllegalArgumentException("Unsupported language or model code: " + langCode);

      final var targetDir = new File(this.modelsDir, info.id());
      if (targetDir.exists()) {
        if (progressConsumer != null)
          progressConsumer.accept(1.0);
        return targetDir;
      }

      try {
        this.plugin.getLogger().info("[Speech] Downloading " + info.displayName() + " from " + info.downloadUrl() + "...");
        final var client = HttpClient.newBuilder()
          .followRedirects(HttpClient.Redirect.ALWAYS)
          .connectTimeout(Duration.ofSeconds(20))
          .build();

        final var request = HttpRequest.newBuilder()
          .uri(URI.create(info.downloadUrl()))
          .GET()
          .build();

        final var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200)
          throw new IOException("HTTP download failed with status " + response.statusCode());

        final var contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        final var tempZip = new File(this.modelsDir, info.id() + ".download.tmp");

        try (final var in = response.body(); final var out = new FileOutputStream(tempZip)) {
          final var buffer = new byte[8192];
          var bytesRead = 0;
          var totalRead = 0L;

          while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
            totalRead += bytesRead;
            if (contentLength > 0 && progressConsumer != null)
              progressConsumer.accept((double) totalRead / (double) contentLength);
          }
        }

        // Unzip model archive
        this.plugin.getLogger().info("[Speech] Extracting model archive...");
        unzipArchive(tempZip, this.modelsDir);
        tempZip.delete();

        if (progressConsumer != null)
          progressConsumer.accept(1.0);

        this.plugin.getLogger().info("[Speech] Model " + info.id() + " successfully downloaded and extracted!");

        // Automatically set as active if no model is loaded
        if (this.activeVoskModel == null)
          setActiveModel(info.id());

        return targetDir;
      } catch (Exception e) {
        throw new RuntimeException("Failed to download model: " + e.getMessage(), e);
      }
    });
  }

  private void unzipArchive(final @NotNull File zipFile, final @NotNull File destDir) throws IOException {
    try (final var zis = new ZipInputStream(new FileInputStream(zipFile))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        final var newFile = new File(destDir, entry.getName());
        if (entry.isDirectory())
          newFile.mkdirs();
        else {
          new File(newFile.getParent()).mkdirs();
          try (final var fos = new FileOutputStream(newFile)) {
            final var buffer = new byte[8192];
            var len = 0;
            while ((len = zis.read(buffer)) > 0)
              fos.write(buffer, 0, len);
          }
        }
        zis.closeEntry();
      }
    }
  }

  @Override
  public @NotNull List<String> getInstalledModels() {
    final var list = new ArrayList<String>();
    if (!this.modelsDir.exists())
      return list;

    final var files = this.modelsDir.listFiles(File::isDirectory);
    if (files != null) {
      for (final var f : files)
        list.add(f.getName());
    }
    return list;
  }

  @Override
  public @NotNull List<SpeechModelInfo> getCloudModels() {
    return VoskModelRegistry.getAllModels();
  }

  @Override
  public @NotNull String getActiveModel() {
    return this.config.getActiveModel();
  }

  @Override
  public boolean setActiveModel(final @NotNull String modelId) {
    this.config.setActiveModel(modelId);
    saveConfigFile();
    loadActiveModelAsync();
    return true;
  }

  @Override
  public @NotNull List<KeywordDefinition> getRegisteredKeywords() {
    return this.keywordsConfig.getKeywords().stream()
      .map(KeywordsConfig.KeywordEntry::toDefinition)
      .toList();
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
    } else {
      this.debugSubscribers.add(playerUuid);
      return true;
    }
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

  // ###############################################################
  // ------------------- TRANSCRIPTION ENGINE ----------------------
  // ###############################################################

  @Override
  public @NotNull CompletableFuture<SpeechTranscriptionResult> transcribeRecording(final @NotNull VoiceRecording recording) {
    return CompletableFuture.supplyAsync(() -> {
      final var recordingService = DreamVoice.getService(VoiceRecordingService.class);
      if (recordingService == null)
        throw new IllegalStateException("VoiceRecordingService unavailable");

      final var pcm = recordingService.decodeToPcm(recording);
      if (pcm == null || pcm.length == 0)
        return new SpeechTranscriptionResult(recording.getUuid(), recording.getSpeakerUUID(), 0F, "", List.of());

      final var result = transcribePcm(pcm, recording.getSpeakerUUID()).join();
      return new SpeechTranscriptionResult(
        recording.getUuid(),
        recording.getSpeakerUUID(),
        recording.getDurationSeconds(),
        result.fullText(),
        result.segments()
      );
    });
  }

  @Override
  public @NotNull CompletableFuture<SpeechTranscriptionResult> transcribePcm(final short @NotNull [] pcm, final @Nullable UUID speakerUuid) {
    final var transcriptionConfig = this.config.getTranscription();
    final var mode = transcriptionConfig.getMode() != null ? transcriptionConfig.getMode().toUpperCase() : "LOCAL";

    if ("SIDECAR".equals(mode)) {
      return transcribeViaSidecar(pcm, speakerUuid).handle((res, ex) -> {
        if (ex != null || res == null) {
          if (transcriptionConfig.getSidecar().isFallbackToLocal()) {
            this.plugin.getLogger().warning("[Speech] Sidecar failed (" + (ex != null ? ex.getMessage() : "null") + "). Falling back to local Vosk.");
            return transcribeViaVosk(pcm, speakerUuid);
          }
          throw new RuntimeException("Sidecar transcription failed", ex);
        }
        return res;
      });
    }

    return CompletableFuture.supplyAsync(() -> transcribeViaVosk(pcm, speakerUuid));
  }

  private CompletableFuture<SpeechTranscriptionResult> transcribeViaSidecar(final short @NotNull [] pcm, final @Nullable UUID speakerUuid) {
    return CompletableFuture.supplyAsync(() -> {
      final var sidecarConfig = this.config.getTranscription().getSidecar();
      final var wavBytes = pcmToWavBytes(pcm, 48000, 1);
      final var boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");

      final var header = "--" + boundary + "\r\n"
        + "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n"
        + "Content-Type: audio/wav\r\n\r\n";
      final var footer = "\r\n--" + boundary + "--\r\n";

      final var headerBytes = header.getBytes(StandardCharsets.UTF_8);
      final var footerBytes = footer.getBytes(StandardCharsets.UTF_8);
      final var totalBytes = new byte[headerBytes.length + wavBytes.length + footerBytes.length];

      System.arraycopy(headerBytes, 0, totalBytes, 0, headerBytes.length);
      System.arraycopy(wavBytes, 0, totalBytes, headerBytes.length, wavBytes.length);
      System.arraycopy(footerBytes, 0, totalBytes, headerBytes.length + wavBytes.length, footerBytes.length);

      final var client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(sidecarConfig.getTimeoutSeconds()))
        .build();

      final var request = HttpRequest.newBuilder()
        .uri(URI.create(sidecarConfig.getUrl()))
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofByteArray(totalBytes))
        .timeout(Duration.ofSeconds(sidecarConfig.getTimeoutSeconds()))
        .build();

      try {
        final var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200)
          throw new IOException("Sidecar returned status " + response.statusCode() + ": " + response.body());

        final var rootNode = MAPPER.readTree(response.body());
        final var fullText = rootNode.path("text").asText("");
        final var duration = (float) rootNode.path("duration").asDouble(pcm.length / 48000.0);

        final var speakerName = resolveSpeakerName(speakerUuid);
        final var segments = new ArrayList<SpeechTranscriptionResult.TranscriptionSegment>();

        final var segmentsNode = rootNode.path("segments");
        if (segmentsNode.isArray()) {
          for (final var node : segmentsNode) {
            final var segText = node.path("text").asText("").trim();
            final var start = (float) node.path("start").asDouble(0.0);
            if (!segText.isEmpty())
              segments.add(new SpeechTranscriptionResult.TranscriptionSegment(start, speakerName, segText));
          }
        }

        if (segments.isEmpty() && !fullText.isBlank())
          segments.add(new SpeechTranscriptionResult.TranscriptionSegment(0F, speakerName, fullText.trim()));

        return new SpeechTranscriptionResult(null, speakerUuid, duration, fullText, segments);
      } catch (Exception e) {
        throw new RuntimeException("Sidecar request error: " + e.getMessage(), e);
      }
    });
  }

  private SpeechTranscriptionResult transcribeViaVosk(final short @NotNull [] pcm, final @Nullable UUID speakerUuid) {
    if (this.activeVoskModel == null)
      throw new IllegalStateException("No Vosk model loaded. Use '/voice speech download <lang>' first.");

    try (final var recognizer = new Recognizer(this.activeVoskModel, 48000.0F)) {
      final var byteBuf = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
      for (final var s : pcm)
        byteBuf.putShort(s);

      final var audioBytes = byteBuf.array();
      recognizer.acceptWaveForm(audioBytes, audioBytes.length);

      final var finalJson = recognizer.getFinalResult();
      final var rootNode = MAPPER.readTree(finalJson);
      final var fullText = rootNode.path("text").asText("").trim();

      final var speakerName = resolveSpeakerName(speakerUuid);
      final var duration = (float) (pcm.length / 48000.0);
      final var segments = new ArrayList<SpeechTranscriptionResult.TranscriptionSegment>();

      if (!fullText.isEmpty())
        segments.add(new SpeechTranscriptionResult.TranscriptionSegment(0F, speakerName, fullText));

      return new SpeechTranscriptionResult(null, speakerUuid, duration, fullText, segments);
    } catch (Exception e) {
      throw new RuntimeException("Vosk transcription failed: " + e.getMessage(), e);
    }
  }

  private byte[] pcmToWavBytes(final short[] pcm, final int sampleRate, final int channels) {
    final var byteRate = sampleRate * channels * 2;
    final var dataSize = pcm.length * 2;
    final var totalSize = 36 + dataSize;

    final var buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
    buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
    buffer.putInt(totalSize);
    buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
    buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
    buffer.putInt(16);
    buffer.putShort((short) 1); // PCM
    buffer.putShort((short) channels);
    buffer.putInt(sampleRate);
    buffer.putInt(byteRate);
    buffer.putShort((short) (channels * 2));
    buffer.putShort((short) 16);
    buffer.put("data".getBytes(StandardCharsets.US_ASCII));
    buffer.putInt(dataSize);

    for (final var sample : pcm)
      buffer.putShort(sample);

    return buffer.array();
  }

  private @NotNull String resolveSpeakerName(final @Nullable UUID speakerUuid) {
    if (speakerUuid == null)
      return "Speaker";
    final var offline = Bukkit.getOfflinePlayer(speakerUuid);
    return offline.getName() != null ? offline.getName() : speakerUuid.toString().substring(0, 8);
  }

  // ###############################################################
  // ------------------- KEYWORD SPOTTING REALTIME -----------------
  // ###############################################################

  @EventHandler
  private void onMicrophonePacket(final @NotNull MicrophonePacketEvent event) {
    if (!this.config.isEnabled() || !this.config.getKeywords().isEnabled() || this.activeVoskModel == null)
      return;

    final var sender = event.getSender();
    if (sender == null || sender.getPlayer() == null)
      return;

    final var player = Bukkit.getPlayer(sender.getPlayer().getUuid());
    if (player == null || !player.isOnline())
      return;

    final var keywords = getRegisteredKeywords();
    if (keywords.isEmpty())
      return;

    final var voiceService = DreamVoice.getService(VoiceService.class);
    if (voiceService == null)
      return;

    try {
      final var decoder = voiceService.getDecoder(player.getUniqueId());
      final var pcm = decoder.decode(event.getPacket().getOpusEncodedData());
      if (pcm == null || pcm.length == 0)
        return;

      // Energy check (noise gate) to avoid feeding silence/hiss to recognizer
      final var minEnergy = this.config.getKeywords().getMinEnergyThreshold();
      if (minEnergy > 0) {
        long sumSquares = 0;
        for (final var s : pcm) {
          sumSquares += (long) s * s;
        }
        final var rms = Math.sqrt((double) sumSquares / pcm.length);
        if (rms < minEnergy) {
          return;
        }
      }

      final var now = System.currentTimeMillis();
      final var silenceResetMs = this.config.getKeywords().getSilenceResetMs();
      final var lastAudio = this.playerLastAudioTime.put(player.getUniqueId(), now);

      final var recognizer = this.playerKeywordRecognizers.computeIfAbsent(player.getUniqueId(), _ -> {
        try {
          final var grammar = this.cachedGrammarJson;
          if (grammar != null && !grammar.isBlank()) {
            return new Recognizer(this.activeVoskModel, 48000.0F, grammar);
          }
          return new Recognizer(this.activeVoskModel, 48000.0F);
        } catch (Exception e) {
          return null;
        }
      });

      if (recognizer == null)
        return;

      // If user was silent for longer than silenceResetMs, reset recognizer to clear stale phonetic buffers
      if (lastAudio != null && (now - lastAudio) > silenceResetMs) {
        recognizer.reset();
      }

      final var byteBuf = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
      for (final var s : pcm)
        byteBuf.putShort(s);
      final var audioBytes = byteBuf.array();

      final var isFinal = recognizer.acceptWaveForm(audioBytes, audioBytes.length);
      final var resultJson = isFinal ? recognizer.getResult() : recognizer.getPartialResult();
      final var text = isFinal ? MAPPER.readTree(resultJson).path("text").asText("") : MAPPER.readTree(resultJson).path("partial").asText("");

      if (!text.isBlank()) {
        final var matched = checkSpokenKeywords(player, recognizer, text.toLowerCase(), text);
        if (!matched && isFinal && hasActiveDebug(player.getUniqueId())) {
          notifyDebugHeard(player, text);
        }
      }

    } catch (Throwable ignored) {}
  }

  private boolean checkSpokenKeywords(
    final @NotNull Player player,
    final @NotNull Recognizer recognizer,
    final @NotNull String lowerText,
    final @NotNull String rawText
  ) {
    final var now = System.currentTimeMillis();
    final var cooldownMs = (long) (Math.max(0.2, this.config.getKeywords().getCooldownSeconds()) * 1000L);
    final var lastTrigger = this.playerKeywordCooldowns.get(player.getUniqueId());
    if (lastTrigger != null && (now - lastTrigger) < cooldownMs) {
      return false;
    }

    for (final var def : getRegisteredKeywords()) {
      if (def.permission() != null && !def.permission().isBlank() && !player.hasPermission(def.permission()))
        continue;

      for (final var word : def.words()) {
        final var target = word.toLowerCase();
        if (matchesKeyword(lowerText, target)) {
          this.playerKeywordCooldowns.put(player.getUniqueId(), now);
          recognizer.reset();

          Bukkit.getScheduler().runTask(this.plugin, () -> {
            final var event = new VoiceKeywordSpokenEvent(player, def, target, 1.0F);
            Bukkit.getPluginManager().callEvent(event);

            notifyDebugKeyword(player, def, target, rawText);
          });
          return true;
        }
      }
    }
    return false;
  }

  private static boolean matchesKeyword(final @NotNull String text, final @NotNull String target) {
    if (text.equals(target))
      return true;

    final var pattern = java.util.regex.Pattern.compile(
      "(?<!\\p{L})" + java.util.regex.Pattern.quote(target) + "(?!\\p{L})",
      java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CHARACTER_CLASS
    );
    return pattern.matcher(text).find();
  }

  private void notifyDebugKeyword(
    final @NotNull Player speaker,
    final @NotNull KeywordDefinition def,
    final @NotNull String matchedWord,
    final @NotNull String fullText
  ) {
    final var message = Component.text("[Speech Debug] ", NamedTextColor.GOLD)
      .append(Component.text(speaker.getName(), NamedTextColor.AQUA))
      .append(Component.text(" triggered keyword '", NamedTextColor.GRAY))
      .append(Component.text(def.id(), NamedTextColor.YELLOW))
      .append(Component.text("' (matched: \"", NamedTextColor.GRAY))
      .append(Component.text(matchedWord, NamedTextColor.GREEN))
      .append(Component.text("\", heard: \"", NamedTextColor.GRAY))
      .append(Component.text(fullText, NamedTextColor.WHITE))
      .append(Component.text("\")", NamedTextColor.GRAY));

    if (this.debugSubscribers.contains(speaker.getUniqueId()))
      speaker.sendMessage(message);

    for (final var uuid : this.debugSubscribers) {
      if (!uuid.equals(speaker.getUniqueId())) {
        final var p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline())
          p.sendMessage(message);
      }
    }

    if (this.globalDebug) {
      this.plugin.getLogger().info("[Speech Debug] Player " + speaker.getName() + " triggered keyword '" + def.id() + "' (matched: \"" + matchedWord + "\", heard: \"" + fullText + "\")");
    }
  }

  private void notifyDebugHeard(final @NotNull Player speaker, final @NotNull String text) {
    final var message = Component.text("[Speech Debug] ", NamedTextColor.DARK_GRAY)
      .append(Component.text(speaker.getName(), NamedTextColor.GRAY))
      .append(Component.text(" spoke: \"", NamedTextColor.DARK_GRAY))
      .append(Component.text(text, NamedTextColor.WHITE))
      .append(Component.text("\" (no keyword matched)", NamedTextColor.DARK_GRAY));

    if (this.debugSubscribers.contains(speaker.getUniqueId()))
      speaker.sendMessage(message);

    for (final var uuid : this.debugSubscribers) {
      if (!uuid.equals(speaker.getUniqueId())) {
        final var p = Bukkit.getPlayer(uuid);
        if (p != null && p.isOnline())
          p.sendMessage(message);
      }
    }

    if (this.globalDebug) {
      this.plugin.getLogger().info("[Speech Debug] Player " + speaker.getName() + " spoke: \"" + text + "\" (no keyword matched)");
    }
  }

  private boolean hasActiveDebug(final @NotNull UUID playerUuid) {
    if (this.globalDebug || this.debugSubscribers.contains(playerUuid))
      return true;
    for (final var uuid : this.debugSubscribers) {
      final var p = Bukkit.getPlayer(uuid);
      if (p != null && p.isOnline())
        return true;
    }
    return false;
  }

  @EventHandler
  private void onPlayerQuit(final @NotNull PlayerQuitEvent event) {
    final var uuid = event.getPlayer().getUniqueId();
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

  // ###############################################################
  // ------------------- STATIONS & INTERACTION --------------------
  // ###############################################################

  @Override
  public boolean isStationBlock(final @NotNull Block block) {
    if (!this.config.isEnabled() || !this.config.getTranscription().getStations().isEnabled())
      return false;

    final var typeName = block.getType().name();
    return this.config.getTranscription().getStations().getAllowedBlocks().contains(typeName);
  }

  @EventHandler(priority = EventPriority.HIGH)
  private void onPlayerInteract(final @NotNull PlayerInteractEvent event) {
    if (!event.getAction().name().contains("RIGHT_CLICK"))
      return;
    if (event.getHand() != EquipmentSlot.HAND)
      return;

    final var clickedBlock = event.getClickedBlock();
    if (clickedBlock == null || !isStationBlock(clickedBlock))
      return;

    final var item = event.getItem();
    final var recUuid = CassetteItem.getRecordingUuid(item);
    if (recUuid == null)
      return;

    event.setCancelled(true);
    startStationProcess(event.getPlayer(), clickedBlock, item);
  }

  @Override
  public void startStationProcess(final @NotNull Player player, final @NotNull Block stationBlock, final @NotNull ItemStack cassetteItem) {
    final var recordingService = DreamVoice.getService(VoiceRecordingService.class);
    if (recordingService == null)
      return;

    final var recUuid = CassetteItem.getRecordingUuid(cassetteItem);
    final var recording = recUuid != null ? recordingService.getVoiceRecording(recUuid) : null;

    final var startEvent = new TranscriptionStationStartEvent(player, stationBlock, cassetteItem, recording);
    if (!startEvent.callEvent())
      return;

    if (recording == null) {
      player.sendMessage(Component.text("[Speech] Recording not found on the server.", net.kyori.adventure.text.format.NamedTextColor.RED));
      return;
    }

    final var stationsConfig = this.config.getTranscription().getStations();
    final var cooldownMs = stationsConfig.getCooldownSeconds() * 1000L;
    final var now = System.currentTimeMillis();
    final var lastUsed = this.stationCooldowns.getOrDefault(player.getUniqueId(), 0L);

    if (now - lastUsed < cooldownMs) {
      final var remainingSec = Math.ceil((cooldownMs - (now - lastUsed)) / 1000.0);
      player.sendMessage(Component.text("Station cooling down (" + remainingSec + "s)...", net.kyori.adventure.text.format.NamedTextColor.RED));
      return;
    }
    this.stationCooldowns.put(player.getUniqueId(), now);

    // Display configurable analysis feedback
    displayAnalysisNotice(player, stationsConfig.getAnalysisDisplay());

    // Play initial sound & spawn particles
    triggerStationEffects(stationBlock, stationsConfig.getSoundEffect(), stationsConfig.getParticles());

    final var processingSec = stationsConfig.getProcessingTimeSeconds();

    // Async transcription
    transcribeRecording(recording).thenAccept(result -> {
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
        if (!player.isOnline())
          return;

        final var bookItem = createReportBook(player, result);
        final var completeEvent = new TranscriptionCompleteEvent(player, stationBlock, result, bookItem);
        Bukkit.getPluginManager().callEvent(completeEvent);

        final var finalBook = completeEvent.getResultBook();

        var placedOnLectern = false;
        if (stationsConfig.isPutOnLectern() && stationBlock.getType() == Material.LECTERN) {
          if (stationBlock.getState() instanceof Lectern lectern) {
            if (lectern.getInventory().getItem(0) == null) {
              lectern.getInventory().setItem(0, finalBook);
              lectern.update();
              placedOnLectern = true;
            }
          }
        }

        if (!placedOnLectern)
          player.getInventory().addItem(finalBook);

        player.playSound(stationBlock.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
        player.sendMessage(Component.text("Transcription complete! Report ready.", net.kyori.adventure.text.format.NamedTextColor.GREEN));

      }, Math.max(1L, processingSec * 20L));
    }).exceptionally(err -> {
      Bukkit.getScheduler().runTask(this.plugin, () -> {
        player.sendMessage(Component.text("Transcription failed: " + err.getMessage(), net.kyori.adventure.text.format.NamedTextColor.RED));
      });
      return null;
    });
  }

  private void displayAnalysisNotice(final @NotNull Player player, final @NotNull SpeechConfig.AnalysisDisplaySection displayConfig) {
    final var langService = DreamPlugin.getService(LangService.class);
    final var type = displayConfig.getType() != null ? displayConfig.getType().toUpperCase() : "ACTION_BAR";

    // Resolve main message
    String rawMsg = null;
    if (langService != null && displayConfig.getMessageKey() != null)
      rawMsg = langService.getTranslation(player, displayConfig.getMessageKey()).orElse(null);
    if (rawMsg == null || rawMsg.isBlank())
      rawMsg = displayConfig.getDefaultText();

    final var component = MM.deserialize(rawMsg != null ? rawMsg : "<yellow>Analyzing...</yellow>");

    switch (type) {
      case "TITLE" -> {
        String rawSub = null;
        if (langService != null && displayConfig.getTitleSubtitleKey() != null)
          rawSub = langService.getTranslation(player, displayConfig.getTitleSubtitleKey()).orElse(null);
        if (rawSub == null || rawSub.isBlank())
          rawSub = displayConfig.getDefaultSubtitle();

        final var subComponent = MM.deserialize(rawSub != null ? rawSub : "");
        player.showTitle(Title.title(component, subComponent, Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(500))));
      }
      case "CHAT" -> player.sendMessage(component);
      default -> player.sendActionBar(component);
    }
  }

  private void triggerStationEffects(final @NotNull Block block, final @Nullable String soundName, final @Nullable String particleName) {
    final var loc = block.getLocation().add(0.5, 1.1, 0.5);

    if (soundName != null && !soundName.isBlank()) {
      try {
        block.getWorld().playSound(loc, soundName, 1.0F, 1.0F);
      } catch (Exception ignored) {}
    }

    if (particleName != null && !particleName.isBlank()) {
      try {
        final var particle = Particle.valueOf(particleName.toUpperCase());
        block.getWorld().spawnParticle(particle, loc, 15, 0.3, 0.3, 0.3, 0.05);
      } catch (Exception ignored) {}
    }
  }

  @Override
  public @NotNull ItemStack createReportBook(final @NotNull Player player, final @NotNull SpeechTranscriptionResult result) {
    final var bookConfig = this.config.getTranscription().getStations().getBook();
    final var langService = DreamPlugin.getService(LangService.class);

    final var item = new ItemStack(Material.WRITTEN_BOOK);
    final var meta = (BookMeta) item.getItemMeta();
    if (meta == null)
      return item;

    final var recId = result.recordingUuid() != null ? result.recordingUuid().toString().substring(0, 8) : "Live";
    final var duration = String.format(Locale.US, "%.1f", result.durationSeconds());
    final var authorName = resolveSpeakerName(result.speakerUuid());

    // Resolve book title
    String title = null;
    if (langService != null && bookConfig.getTitleKey() != null)
      title = langService.getTranslation(player, bookConfig.getTitleKey()).orElse(null);
    if (title == null || title.isBlank())
      title = bookConfig.getDefaultTitle();
    title = title.replace("{id}", recId).replace("{duration}", duration);
    if (title.length() > 32)
      title = title.substring(0, 32);

    meta.setTitle(title);
    meta.setAuthor(bookConfig.getAuthor() != null ? bookConfig.getAuthor() : "Analysis Station");

    // Resolve header
    String header = null;
    if (langService != null && bookConfig.getHeaderKey() != null)
      header = langService.getTranslation(player, bookConfig.getHeaderKey()).orElse(null);
    if (header == null || header.isBlank())
      header = bookConfig.getDefaultHeader();

    header = header.replace("{id}", recId).replace("{duration}", duration).replace("{author}", authorName);

    // Build pages
    final var pages = new ArrayList<Component>();
    final var firstPageLines = new StringBuilder();
    firstPageLines.append(header).append("\n\n");

    if (result.segments().isEmpty()) {
      firstPageLines.append("<gray><i>(No speech detected)</i></gray>");
      pages.add(MM.deserialize(firstPageLines.toString()));
    } else {
      var currentPage = new StringBuilder(firstPageLines.toString());
      for (final var seg : result.segments()) {
        final var minutes = (int) (seg.offsetSeconds() / 60);
        final var seconds = (int) (seg.offsetSeconds() % 60);
        final var timeTag = String.format("[%02d:%02d]", minutes, seconds);
        final var line = "<gray>" + timeTag + "</gray> <gold>" + seg.speakerName() + "</gold>: " + seg.text() + "\n";

        if (currentPage.length() + line.length() > 250) {
          pages.add(MM.deserialize(currentPage.toString()));
          currentPage = new StringBuilder(line);
        } else {
          currentPage.append(line);
        }
      }
      if (!currentPage.isEmpty())
        pages.add(MM.deserialize(currentPage.toString()));
    }

    meta.pages(pages);
    item.setItemMeta(meta);
    return item;
  }

}
