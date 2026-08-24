package fr.dreamin.dreamvoice.core.utils.raycast;

import fr.dreamin.dreaminvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.utils.VoiceRayCast;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class VoiceRaycastDebugLogger {
  private static final String PREFIX = "§6[VoiceDebug]";
  private static final String SEPARATOR = "§8" + "=".repeat(25);
  private static final String SUB_SEPARATOR = "§8" + "-".repeat(25);


  public static void logAnalysisStart(Player speaker, Player listener) {
    if (!DreamVoice.getService(VoiceWallService.class).isDebug()) return;

    Bukkit.broadcastMessage(SEPARATOR);
    Bukkit.broadcastMessage(PREFIX + " §f🔍 ANALYSE LIGNE DE VUE");
    Bukkit.broadcastMessage(PREFIX + " §fSpeaker: §a" + speaker.getName() + " §7(" + formatLocation(speaker.getEyeLocation()) + ")");
    Bukkit.broadcastMessage(PREFIX + " §fListener: §a" + listener.getName() + " §7(" + formatLocation(listener.getEyeLocation()) + ")");

    final var distance = speaker.getEyeLocation().distance(listener.getEyeLocation());
    Bukkit.broadcastMessage(PREFIX + " §fDistance: §e" + String.format("%.2f", distance) + " blocs");
    Bukkit.broadcastMessage(SUB_SEPARATOR);
  }

  public static void logDirectRaycast(Player speaker, Player listener, boolean success, String details) {
    if (!DreamVoice.getService(VoiceWallService.class).isDebug()) return;

    final var status = success ? "§a✓ RÉUSSI" : "§c✗ BLOQUÉ";
    Bukkit.broadcastMessage(PREFIX + " §f📏 Test raycast direct: " + status);

    if (details != null && !details.isEmpty()) Bukkit.broadcastMessage(PREFIX + " §7   └─ " + details);

    if (success) Bukkit.broadcastMessage(PREFIX + " §a   └─ Communication directe possible !");
    else Bukkit.broadcastMessage(PREFIX + " §c   └─ Obstacle détecté, test de contournement...");
  }

  public static void logLateralBypass(Player speaker, Player listener, double offset, boolean success, String details) {
    if (!DreamVoice.getService(VoiceWallService.class).isDebug()) return;

    final var status = success ? "§a✓ RÉUSSI" : "§c✗ BLOQUÉ";
    final var direction = offset < 0 ? "gauche" : "droite";

    Bukkit.broadcastMessage(PREFIX + " §f🔄 Test contournement " + direction + " (offset: " + String.format("%.1f", Math.abs(offset)) + "): " + status);

    if (details != null && !details.isEmpty()) Bukkit.broadcastMessage(PREFIX + " §7   └─ " + details);
    if (success) Bukkit.broadcastMessage(PREFIX + " §a   └─ Contournement latéral possible !");
  }

  public static void logVerticalBypass(Player speaker, Player listener, double height, boolean success, String details) {
    if (!DreamVoice.getService(VoiceWallService.class).isDebug()) return;

    final var status = success ? "§a✓ RÉUSSI" : "§c✗ BLOQUÉ";

    Bukkit.broadcastMessage(PREFIX + " §f⬆️ Test contournement vertical (hauteur: " + String.format("%.1f", height) + "): " + status);

    if (details != null && !details.isEmpty()) Bukkit.broadcastMessage(PREFIX + " §7   └─ " + details);

    if (success) Bukkit.broadcastMessage(PREFIX + " §a   └─ Contournement vertical possible !");
  }

  public static void logFinalResult(Player speaker, Player listener, boolean canCommunicate, String method) {
    if (!DreamVoice.getService(VoiceWallService.class).isDebug()) return;

    Bukkit.broadcastMessage(SUB_SEPARATOR);

    if (canCommunicate) {
      Bukkit.broadcastMessage(PREFIX + " §a🎉 RÉSULTAT: Communication possible !");
      Bukkit.broadcastMessage(PREFIX + " §f   Méthode: §a" + method);
      Bukkit.broadcastMessage(PREFIX + " §f   " + speaker.getName() + " §a↔️§f " + listener.getName());
    } else {
      Bukkit.broadcastMessage(PREFIX + " §c❌ RÉSULTAT: Communication impossible");
      Bukkit.broadcastMessage(PREFIX + " §f   Tous les tests ont échoué");
      Bukkit.broadcastMessage(PREFIX + " §f   " + speaker.getName() + " §c✗§f " + listener.getName());
    }

    Bukkit.broadcastMessage(SEPARATOR);
  }

  public static void logError(String context, Exception e) {
    if (!DreamVoice.getService(VoiceWallService.class).isDebug()) return;

    Bukkit.broadcastMessage(PREFIX + " §c⚠️ ERREUR dans " + context);
    Bukkit.broadcastMessage(PREFIX + " §7   Type: " + e.getClass().getSimpleName());
    Bukkit.broadcastMessage(PREFIX + " §7   Message: " + e.getMessage());
  }

  public static void logConfiguration() {
    if (!DreamVoice.getService(VoiceWallService.class).isDebug()) return;

    Bukkit.broadcastMessage(PREFIX + " §f⚙️ CONFIGURATION ACTUELLE:");
    Bukkit.broadcastMessage(PREFIX + " §7   Largeur contournement: §e" + VoiceRayCast.getWallBypassWidth() + " blocs");
    Bukkit.broadcastMessage(PREFIX + " §7   Contournement vertical: §e" + (VoiceRayCast.isEnableVerticalBypass() ? "activé" : "désactivé"));
    Bukkit.broadcastMessage(PREFIX + " §7   Hauteur max vertical: §e" + VoiceRayCast.getMaxBypassHeight() + " blocs");
  }

  public static void logPerformanceStats(long raycastTime, boolean fromCache) {
    if (!DreamVoice.getService(VoiceWallService.class).isDebug()) return;

    final var source = fromCache ? "§acache" : "§ecalcul";
    Bukkit.broadcastMessage(PREFIX + " §f⏱️ Temps de traitement: §e" + raycastTime + "ms §7(source: " + source + "§7)");
  }

  public static void logDeveloperInfo(String info) {
    if (!DreamVoice.getService(VoiceWallService.class).isDebug()) return;

    Bukkit.broadcastMessage(PREFIX + " §9[DEV] §7" + info);
  }

  public static String formatLocation(Location loc) {
    return String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ());
  }


  public static void logSessionSummary(int totalTests, int successfulTests, int cacheHits) {
    if (!DreamVoice.getService(VoiceWallService.class).isDebug()) return;

    final var successRate = totalTests > 0 ? (double) successfulTests / totalTests * 100 : 0;
    final var cacheHitRate = totalTests > 0 ? (double) cacheHits / totalTests * 100 : 0;

    Bukkit.broadcastMessage(PREFIX + " §f📊 RÉSUMÉ SESSION:");
    Bukkit.broadcastMessage(PREFIX + " §7   Tests total: §e" + totalTests);
    Bukkit.broadcastMessage(PREFIX + " §7   Succès: §a" + successfulTests + " §7(" + String.format("%.1f", successRate) + "%)");
    Bukkit.broadcastMessage(PREFIX + " §7   Cache hits: §b" + cacheHits + " §7(" + String.format("%.1f", cacheHitRate) + "%)");
  }
}