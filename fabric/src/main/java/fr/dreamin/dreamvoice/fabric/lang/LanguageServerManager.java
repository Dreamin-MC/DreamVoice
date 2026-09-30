package fr.dreamin.dreamvoice.fabric.lang;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class LanguageServerManager {

  private static final Logger LOGGER = LoggerFactory.getLogger("DreamVoice-I18n");
  private static final String FOLDER_NAME = "DreamVoice";
  private static final String LANG_DIR_NAME = "lang";
  private static final File LANG_DIR;

  private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
  private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:(#[0-9a-fA-F]{6}):(#[0-9a-fA-F]{6})>(.*?)</gradient>");
  private static final Pattern FORMAT_TAG_PATTERN = Pattern.compile("<(/?[a-zA-Z0-9_!#:]+)>");

  private static final Map<String, ChatFormatting> COLOR_MAP = new HashMap<>();

  static {
    COLOR_MAP.put("black", ChatFormatting.BLACK);
    COLOR_MAP.put("dark_blue", ChatFormatting.DARK_BLUE);
    COLOR_MAP.put("dark_green", ChatFormatting.DARK_GREEN);
    COLOR_MAP.put("dark_aqua", ChatFormatting.DARK_AQUA);
    COLOR_MAP.put("dark_red", ChatFormatting.DARK_RED);
    COLOR_MAP.put("dark_purple", ChatFormatting.DARK_PURPLE);
    COLOR_MAP.put("gold", ChatFormatting.GOLD);
    COLOR_MAP.put("gray", ChatFormatting.GRAY);
    COLOR_MAP.put("dark_gray", ChatFormatting.DARK_GRAY);
    COLOR_MAP.put("blue", ChatFormatting.BLUE);
    COLOR_MAP.put("green", ChatFormatting.GREEN);
    COLOR_MAP.put("aqua", ChatFormatting.AQUA);
    COLOR_MAP.put("red", ChatFormatting.RED);
    COLOR_MAP.put("light_purple", ChatFormatting.LIGHT_PURPLE);
    COLOR_MAP.put("yellow", ChatFormatting.YELLOW);
    COLOR_MAP.put("white", ChatFormatting.WHITE);

    final var configDir = FabricLoader.getInstance().getConfigDir().resolve(FOLDER_NAME).resolve(LANG_DIR_NAME).toFile();
    if (!configDir.exists())
      configDir.mkdirs();
    LANG_DIR = configDir;
  }

  @Getter
  private static final Map<String, Map<String, String>> keyToTranslations = new ConcurrentHashMap<>();
  @Getter
  private static final Map<String, Map<String, String>> valueToTranslations = new ConcurrentHashMap<>();

  private LanguageServerManager() {}

  public static synchronized void load() {
    keyToTranslations.clear();
    valueToTranslations.clear();

    if (!LANG_DIR.exists())
      LANG_DIR.mkdirs();

    var existingFiles = LANG_DIR.listFiles((_, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
    if (existingFiles == null || existingFiles.length == 0) {
      extractBundledLangFiles(LANG_DIR);
      existingFiles = LANG_DIR.listFiles((_, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
    }

    if (existingFiles != null)
      for (final var jsonFile : existingFiles)
        loadLangFile(jsonFile);

    if (keyToTranslations.isEmpty()) {
      final String[] bundledFiles = { "/lang/lang-dreamvoice.json", "/assets/dreamvoice/lang/lang-dreamvoice.json" };
      for (final var resourcePath : bundledFiles) {
        try (final var is = LanguageServerManager.class.getResourceAsStream(resourcePath)) {
          if (is != null) {
            loadLangFromStream(is, resourcePath);
            break;
          }
        } catch (final Exception ignored) {}
      }
    }

    LOGGER.info("[I18n Server] Loaded {} translation keys from {}", keyToTranslations.size(), LANG_DIR.getAbsolutePath());
  }

  public static synchronized void reload() {
    load();
  }

  private static void extractBundledLangFiles(final File targetDir) {
    final String[] bundledFiles = {
      "/lang/lang-dreamvoice.json",
      "/assets/dreamvoice/lang/lang-dreamvoice.json"
    };

    for (final var resourcePath : bundledFiles) {
      try (final var is = LanguageServerManager.class.getResourceAsStream(resourcePath)) {
        if (is != null) {
          final var dest = new File(targetDir, "lang-dreamvoice.json");
          Files.copy(is, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
          return;
        }
      } catch (final Exception ignored) {}
    }
  }

  private static void loadLangFile(final File file) {
    try (final var is = Files.newInputStream(file.toPath())) {
      loadLangFromStream(is, file.getName());
    } catch (final Exception e) {
      LOGGER.error("[I18n Server] Failed to read lang file: {}", file.getName(), e);
    }
  }

  private static void loadLangFromStream(final InputStream is, final String sourceName) {
    try {
      final var mapper = new ObjectMapper();
      final var root = mapper.readTree(is);
      final var keysNode = root.get("keys");
      if (keysNode == null || !keysNode.isArray()) return;

      for (final var entry : keysNode) {
        final var keyNode = entry.get("key");
        if (keyNode == null) continue;
        final var key = keyNode.asText().toLowerCase(Locale.ROOT).trim();

        final var langArray = entry.get("lang");
        if (langArray == null || !langArray.isArray()) continue;

        final var langMap = new HashMap<String, String>();
        for (final var l : langArray) {
          final var locale = l.get("locale").asText().toUpperCase(Locale.ROOT).trim();
          final var rawVal = l.get("value").asText();
          final var cleanVal = stripFormatting(rawVal);
          langMap.put(locale, rawVal);

          valueToTranslations.computeIfAbsent(cleanVal.toLowerCase(Locale.ROOT).trim(), _ -> new HashMap<>())
            .put(locale, cleanVal);
        }

        keyToTranslations.put(key, langMap);
      }
    } catch (final Exception e) {
      LOGGER.error("[I18n Server] Failed to parse lang source: {}", sourceName, e);
    }
  }

  public static String stripFormatting(final String text) {
    if (text == null) return "";
    return TAG_PATTERN.matcher(text).replaceAll("").trim();
  }

  public static String resolvePlayerLocale(final @Nullable ServerPlayer player) {
    if (player == null) return "EN";
    try {
      final var info = player.clientInformation();
      final var lang = info.language().toLowerCase(Locale.ROOT);
      if (lang.startsWith("fr")) return "FR";
      if (lang.startsWith("es")) return "ES";
      if (lang.startsWith("de")) return "DE";
    } catch (final Exception ignored) {}
    return "EN";
  }

  public static String resolveLocale(final @Nullable CommandSourceStack source) {
    if (source != null && source.isPlayer())
      return resolvePlayerLocale(source.getPlayer());
    return "EN";
  }

  public static String getRawTranslation(final @NotNull String input, final @Nullable String locale) {
    if (input.isBlank()) return input;

    final var targetLocale = (locale != null && !locale.isBlank()) ? locale.toUpperCase(Locale.ROOT) : "EN";
    final var clean = input.toLowerCase(Locale.ROOT).trim();

    if (keyToTranslations.containsKey(clean)) {
      final var m = keyToTranslations.get(clean);
      if (m.containsKey(targetLocale)) return m.get(targetLocale);
      if (m.containsKey("EN")) return m.get("EN");
      if (m.containsKey("FR")) return m.get("FR");
      final var it = m.values().iterator();
      if (it.hasNext()) return it.next();
    }

    if (valueToTranslations.containsKey(clean)) {
      final var m = valueToTranslations.get(clean);
      if (m.containsKey(targetLocale)) return m.get(targetLocale);
    }

    return input;
  }

  public static Component translate(final @NotNull CommandSourceStack source, final @NotNull String key, final Object... args) {
    final var locale = resolveLocale(source);
    final var raw = getRawTranslation(key, locale);
    return parseComponent(raw, args);
  }

  public static Component translate(final @NotNull ServerPlayer player, final @NotNull String key, final Object... args) {
    final var locale = resolvePlayerLocale(player);
    final var raw = getRawTranslation(key, locale);
    return parseComponent(raw, args);
  }

  public static Component translate(final @NotNull String key, final @Nullable String locale, final Object... args) {
    final var raw = getRawTranslation(key, locale);
    return parseComponent(raw, args);
  }

  public static void sendSuccess(final @NotNull CommandSourceStack source, final @NotNull String key, final Object... args) {
    source.sendSuccess(() -> translate(source, key, args), false);
  }

  public static void sendFailure(final @NotNull CommandSourceStack source, final @NotNull String key, final Object... args) {
    source.sendFailure(translate(source, key, args));
  }

  public static void send(final @NotNull ServerPlayer player, final @NotNull String key, final Object... args) {
    player.sendSystemMessage(translate(player, key, args));
  }

  public static Component parseComponent(final String rawText, final Object... args) {
    if (rawText == null || rawText.isEmpty())
      return Component.empty();

    var text = rawText;
    if (args != null && args.length > 0) {
      for (var i = 0; i < args.length; i++) {
        final var val = args[i] != null ? args[i].toString() : "null";
        text = text.replace("<arg:" + i + ">", val)
          .replace("{" + i + "}", val);
      }
    }

    final var root = Component.empty();
    final var gradMatcher = GRADIENT_PATTERN.matcher(text);
    var lastIdx = 0;

    while (gradMatcher.find()) {
      if (gradMatcher.start() > lastIdx)
        appendFormatted(root, text.substring(lastIdx, gradMatcher.start()));

      final var hexStart = gradMatcher.group(1);
      final var hexEnd = gradMatcher.group(2);
      final var content = gradMatcher.group(3);

      appendGradient(root, content, hexStart, hexEnd);
      lastIdx = gradMatcher.end();
    }

    if (lastIdx < text.length())
      appendFormatted(root, text.substring(lastIdx));

    return root;
  }

  private static void appendGradient(final MutableComponent root, final String text, final String hexStart, final String hexEnd) {
    if (text.isEmpty()) return;
    try {
      final var c1 = Integer.parseInt(hexStart.substring(1), 16);
      final var c2 = Integer.parseInt(hexEnd.substring(1), 16);

      final var r1 = (c1 >> 16) & 0xFF;
      final var g1 = (c1 >> 8) & 0xFF;
      final var b1 = c1 & 0xFF;

      final var r2 = (c2 >> 16) & 0xFF;
      final var g2 = (c2 >> 8) & 0xFF;
      final var b2 = c2 & 0xFF;

      final var len = text.length();
      for (var i = 0; i < len; i++) {
        final var ratio = len > 1 ? (float) i / (len - 1) : 0f;
        final var r = (int) (r1 + (r2 - r1) * ratio);
        final var g = (int) (g1 + (g2 - g1) * ratio);
        final var b = (int) (b1 + (b2 - b1) * ratio);
        final var rgb = (r << 16) | (g << 8) | b;
        root.append(Component.literal(String.valueOf(text.charAt(i)))
          .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
      }
    } catch (final Exception e) {
      root.append(Component.literal(text));
    }
  }

  private static void appendFormatted(final MutableComponent root, final String text) {
    if (text.isEmpty()) return;

    final var matcher = FORMAT_TAG_PATTERN.matcher(text);
    final var styleStack = new ArrayDeque<Style>();
    styleStack.push(Style.EMPTY);

    var lastIdx = 0;
    while (matcher.find()) {
      if (matcher.start() > lastIdx) {
        final var chunk = text.substring(lastIdx, matcher.start());
        root.append(Component.literal(chunk).withStyle(styleStack.peek()));
      }

      final var tag = matcher.group(1).toLowerCase(Locale.ROOT);
      var currentStyle = styleStack.peek();
      if (currentStyle == null) currentStyle = Style.EMPTY;

      if (tag.startsWith("/")) {
        if (styleStack.size() > 1)
          styleStack.pop();
      } else if (tag.equals("!italic"))
        styleStack.push(currentStyle.withItalic(false));
      else if (tag.equals("italic"))
        styleStack.push(currentStyle.withItalic(true));
      else if (tag.equals("!bold"))
        styleStack.push(currentStyle.withBold(false));
      else if (tag.equals("bold"))
        styleStack.push(currentStyle.withBold(true));
      else if (tag.equals("underlined"))
        styleStack.push(currentStyle.withUnderlined(true));
      else if (tag.equals("!underlined"))
        styleStack.push(currentStyle.withUnderlined(false));
      else if (COLOR_MAP.containsKey(tag))
        styleStack.push(currentStyle.withColor(COLOR_MAP.get(tag)));
      else if (tag.startsWith("#") && tag.length() == 7) {
        try {
          final var rgb = Integer.parseInt(tag.substring(1), 16);
          styleStack.push(currentStyle.withColor(TextColor.fromRgb(rgb)));
        } catch (final Exception ignored) {
          styleStack.push(currentStyle);
        }
      } else
        styleStack.push(currentStyle);

      lastIdx = matcher.end();
    }

    if (lastIdx < text.length()) {
      final var trailing = text.substring(lastIdx);
      root.append(Component.literal(trailing).withStyle(styleStack.peek()));
    }
  }
}
