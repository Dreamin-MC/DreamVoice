package fr.dreamin.dreamvoice.core.utils;

import fr.dreamin.dreaminvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.utils.raycast.VoiceRaycastDebugLogger;
import fr.dreamin.dreamvoice.core.utils.raycast.VoiceRaycastDebugVisualizer;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public class VoiceRayCast {

  @Getter private static double wallBypassWidth = 2.0;
  @Getter @Setter private static boolean enableVerticalBypass = true;
  @Getter private static double maxBypassHeight = 2.0;

  @Getter private static double bypassDistanceMultiplier = 1.5;

  // #################################################################
  // ---------------------- PUBLIC METHOD ----------------------------
  // #################################################################

  public static RaycastResult check(final @NotNull Player speaker, final @NotNull Player listener) {
    if (!speaker.getWorld().equals(listener.getWorld())) return RaycastResult.CLEAR;

    final var startTime = System.currentTimeMillis();

    VoiceRaycastDebugLogger.logAnalysisStart(speaker, listener);

    if (DreamVoice.getService(VoiceWallService.class).isDebug()) {
      VoiceRaycastDebugLogger.logConfiguration();
      VoiceRaycastDebugLogger.logDeveloperInfo(String.format("Multiplicateur distance contournement: %s", bypassDistanceMultiplier));
    }

    final var speakerLoc = speaker.getEyeLocation();
    final var listenerLoc = listener.getEyeLocation();

    final var directResult = checkDirectLineOfSight(speakerLoc, listenerLoc);

    VoiceRaycastDebugLogger.logDirectRaycast(speaker, listener, directResult.isLineOfSight(),
      directResult.isLineOfSight() ? "Aucun obstacle détecté" : "Obstacle bloquant (épaisseur: " + String.format("%.2f", directResult.getWallThickness()) + " blocs)");
    VoiceRaycastDebugVisualizer.showDirectRaycast(speakerLoc, listenerLoc, directResult.isLineOfSight());

    if (directResult.isLineOfSight()) {
      logAndReturn(startTime, speaker, listener, true, "Ligne directe");
      return directResult;
    }

    // 2. Contournement latéral
    if (canBypassWallCompleteVerification(speakerLoc, listenerLoc, speaker, listener)) {
      logAndReturn(startTime, speaker, listener, true, "Contournement latéral");
      return RaycastResult.CLEAR;
    }

    // 3. Contournement vertical
    if (enableVerticalBypass && canBypassVerticallyComplete(speakerLoc, listenerLoc, speaker, listener)) {
      logAndReturn(startTime, speaker, listener, true, "Contournement vertical");
      return RaycastResult.CLEAR;
    }

    // 4. Bloqué → retourne avec épaisseur
    logAndReturn(startTime, speaker, listener, false, "Aucune");
    VoiceRaycastDebugLogger.logDeveloperInfo("Épaisseur mur calculée: " + String.format("%.2f", directResult.getWallThickness()) + " blocs");
    return directResult;
  }

  public static boolean hasLineOfSight(final @NotNull Player speaker, final @NotNull Player listener) {
    return check(speaker, listener).isLineOfSight();
  }

  // #################################################################
  // ---------------------- PRIVATE METHOD ---------------------------
  // #################################################################

  /**
   * Raycast direct avec calcul d'épaisseur du mur (double raycast aller+retour)
   */
  private static RaycastResult checkDirectLineOfSight(Location from, Location to) {
    final var world = from.getWorld();
    if (world == null) return RaycastResult.CLEAR;

    final var dirFwd = to.toVector().subtract(from.toVector());
    final var totalDistance = dirFwd.length();

    try {
      // Raycast aller : speaker → listener
      final var hitFwd = world.rayTraceBlocks(from, dirFwd.clone().normalize(), totalDistance, FluidCollisionMode.NEVER, true);

      if (hitFwd == null || hitFwd.getHitBlock() == null) {
        return RaycastResult.CLEAR;
      }

      if (DreamVoice.getService(VoiceWallService.class).isDebug()) {
        VoiceRaycastDebugLogger.logDeveloperInfo("Bloc bloquant: " + hitFwd.getHitBlock().getType() + " à " +
          String.format("%.1f, %.1f, %.1f", (double) hitFwd.getHitBlock().getX(),
            (double) hitFwd.getHitBlock().getY(), (double) hitFwd.getHitBlock().getZ()));
      }

      // Distance jusqu'au 1er bloc côté speaker
      final var distToWallFwd = hitFwd.getHitPosition().distance(from.toVector());

      // Raycast retour : listener → speaker pour trouver l'autre face du mur
      final var dirBwd = from.toVector().subtract(to.toVector()).normalize();
      final var hitBwd = world.rayTraceBlocks(to, dirBwd, totalDistance, FluidCollisionMode.NEVER, true);
      final var distToWallBwd = (hitBwd != null && hitBwd.getHitBlock() != null)
        ? hitBwd.getHitPosition().distance(to.toVector())
        : 0.0;

      // Épaisseur = distance totale - espace libre côté speaker - espace libre côté listener
      final var wallThickness = Math.max(0.0, totalDistance - distToWallFwd - distToWallBwd);

      VoiceRaycastDebugLogger.logDeveloperInfo(String.format(
        "Double raycast → distFwd=%.2f, distBwd=%.2f, total=%.2f, épaisseur=%.2f",
        distToWallFwd, distToWallBwd, totalDistance, wallThickness));

      return new RaycastResult(false, wallThickness);

    } catch (Exception e) {
      VoiceRaycastDebugLogger.logError("checkDirectLineOfSight", e);
      return RaycastResult.CLEAR;
    }
  }

  /**
   * Contournement latéral avec vérification complète du chemin
   */
  private static boolean canBypassWallCompleteVerification(Location from, Location to, Player speaker, Player listener) {
    final var directDistance = from.distance(to);
    final var maxBypassDistance = directDistance * bypassDistanceMultiplier;

    VoiceRaycastDebugLogger.logDeveloperInfo("=== CONTOURNEMENT LATÉRAL AVEC VÉRIFICATION COMPLÈTE ===");
    VoiceRaycastDebugLogger.logDeveloperInfo("Distance directe: " + String.format("%.2f", directDistance) + " blocs");
    VoiceRaycastDebugLogger.logDeveloperInfo("Distance max autorisée: " + String.format("%.2f", maxBypassDistance) + " blocs (x" + bypassDistanceMultiplier + ")");

    final var direction = to.toVector().subtract(from.toVector()).normalize();
    final var perpendicular = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();

    // Tester les contournements avec vérification complète
    final var leftSuccess = testCompleteBypassPath(from, to, perpendicular.clone().multiply(-wallBypassWidth),
      maxBypassDistance, "gauche", speaker, listener);
    if (leftSuccess) {
      VoiceRaycastDebugLogger.logDeveloperInfo("✓ Contournement gauche réussi avec vérification complète !");
      return true;
    }

    final var rightSuccess = testCompleteBypassPath(from, to, perpendicular.clone().multiply(wallBypassWidth),
      maxBypassDistance, "droite", speaker, listener);
    if (rightSuccess) {
      VoiceRaycastDebugLogger.logDeveloperInfo("✓ Contournement droite réussi avec vérification complète !");
      return true;
    }

    VoiceRaycastDebugLogger.logDeveloperInfo("✗ Aucun contournement latéral possible avec vérification complète");
    return false;
  }

  /**
   * CONTOURNEMENT VERTICAL CORRIGÉ : Avec vérification complète du chemin
   * Applique la même logique que le contournement latéral
   */
  private static boolean canBypassVerticallyComplete(Location from, Location to, Player speaker, Player listener) {
    final var world = from.getWorld();
    if (world == null) return false;

    // Calculer la distance directe et la limite
    final var directDistance = from.distance(to);
    final var maxBypassDistance = directDistance * bypassDistanceMultiplier;

    // Calculer le point de contournement vertical
    final var midX = (from.getX() + to.getX()) / 2;
    final var midZ = (from.getZ() + to.getZ()) / 2;
    final var maxY = Math.max(from.getY(), to.getY()) + maxBypassHeight;

    final var highPoint = new Location(world, midX, maxY, midZ);

    // Calculer la distance totale du contournement vertical
    final var segment1 = from.distance(highPoint);
    final var segment2 = highPoint.distance(to);
    final var totalDistance = segment1 + segment2;

    try {
      VoiceRaycastDebugLogger.logDeveloperInfo("=== CONTOURNEMENT VERTICAL AVEC VÉRIFICATION COMPLÈTE ===");
      VoiceRaycastDebugLogger.logDeveloperInfo("Point de contournement vertical: " +
        String.format("%.1f, %.1f, %.1f", highPoint.getX(), highPoint.getY(), highPoint.getZ()));
      VoiceRaycastDebugLogger.logDeveloperInfo("Distance directe: " + String.format("%.2f", directDistance) + " blocs");
      VoiceRaycastDebugLogger.logDeveloperInfo("Distance contournement vertical: " + String.format("%.2f", totalDistance) + " blocs");
      VoiceRaycastDebugLogger.logDeveloperInfo("Distance max autorisée: " + String.format("%.2f", maxBypassDistance) + " blocs");
      VoiceRaycastDebugLogger.logDeveloperInfo("Hauteur de contournement: " + String.format("%.1f", maxBypassHeight) + " blocs");

      // ÉTAPE 1 : Vérifier la limite de distance
      if (totalDistance > maxBypassDistance) {
        VoiceRaycastDebugLogger.logVerticalBypass(speaker, listener, maxBypassHeight, false,
          "Contournement vertical trop long (" + String.format("%.2f", totalDistance) + " > " + String.format("%.2f", maxBypassDistance) + ")");
        return false;
      }

      // ÉTAPE 2 : CRUCIAL - Vérifier que le chemin VERS le point haut est libre
      if (!isPathClear(from, highPoint, "vers point vertical")) {
        VoiceRaycastDebugLogger.logVerticalBypass(speaker, listener, maxBypassHeight, false,
          "Chemin vers point vertical bloqué - contournement impossible");
        return false;
      }

      // ÉTAPE 3 : Vérifier que le point de contournement vertical est accessible
      if (!isPointAccessible(highPoint)) {
        VoiceRaycastDebugLogger.logVerticalBypass(speaker, listener, maxBypassHeight, false,
          "Point de contournement vertical bloqué");
        return false;
      }

      // ÉTAPE 4 : Vérifier que le chemin DEPUIS le point haut est libre
      if (!isPathClear(highPoint, to, "depuis point vertical")) {
        VoiceRaycastDebugLogger.logVerticalBypass(speaker, listener, maxBypassHeight, false,
          "Chemin depuis point vertical bloqué - contournement impossible");
        return false;
      }

      // Si toutes les vérifications passent
      VoiceRaycastDebugLogger.logVerticalBypass(speaker, listener, maxBypassHeight, true,
        "Contournement vertical réussi - chemin entièrement libre (distance: " + String.format("%.2f", totalDistance) + ")");
      VoiceRaycastDebugVisualizer.showVerticalBypassRaycast(from, highPoint, to, true);

      return true;
    } catch (Exception e) {
      VoiceRaycastDebugLogger.logError("canBypassVerticallyComplete", e);
      return false;
    }
  }

  /**
   * Teste un contournement latéral avec vérification complète du chemin
   */
  private static boolean testCompleteBypassPath(Location from, Location to, Vector sideOffset, double maxDistance, String sideName, Player speaker, Player listener) {
    try {
      // Point de contournement
      final var bypassPoint = from.clone().add(sideOffset);

      // Calculer la distance totale
      final var segment1 = from.distance(bypassPoint);
      final var segment2 = bypassPoint.distance(to);
      final var totalDistance = segment1 + segment2;

      VoiceRaycastDebugLogger.logDeveloperInfo("--- Test contournement " + sideName + " (VÉRIFICATION COMPLÈTE) ---");
      VoiceRaycastDebugLogger.logDeveloperInfo("Point: " + String.format("%.1f, %.1f, %.1f",
        bypassPoint.getX(), bypassPoint.getY(), bypassPoint.getZ()));
      VoiceRaycastDebugLogger.logDeveloperInfo("Segment 1 (vers point): " + String.format("%.2f", segment1) + " blocs");
      VoiceRaycastDebugLogger.logDeveloperInfo("Segment 2 (depuis point): " + String.format("%.2f", segment2) + " blocs");
      VoiceRaycastDebugLogger.logDeveloperInfo("Distance totale: " + String.format("%.2f", totalDistance) + " blocs");
      VoiceRaycastDebugLogger.logDeveloperInfo("Limite: " + String.format("%.2f", maxDistance) + " blocs");

      // ÉTAPE 1 : Vérifier la limite de distance
      if (totalDistance > maxDistance) {
        VoiceRaycastDebugLogger.logLateralBypass(speaker, listener, sideOffset.length() * (sideName.equals("gauche") ? -1 : 1), false,
          "Contournement " + sideName + " trop long (" + String.format("%.2f", totalDistance) + " > " + String.format("%.2f", maxDistance) + ")");
        return false;
      }

      // ÉTAPE 2 : CRUCIAL - Vérifier que le chemin VERS le point de contournement est libre
      if (!isPathClear(from, bypassPoint, "vers point " + sideName)) {
        VoiceRaycastDebugLogger.logLateralBypass(speaker, listener, sideOffset.length() * (sideName.equals("gauche") ? -1 : 1), false,
          "Chemin vers point " + sideName + " bloqué - contournement impossible");
        return false;
      }

      // ÉTAPE 3 : Vérifier que le point de contournement est accessible
      if (!isPointAccessible(bypassPoint)) {
        VoiceRaycastDebugLogger.logLateralBypass(speaker, listener, sideOffset.length() * (sideName.equals("gauche") ? -1 : 1), false,
          "Point de contournement " + sideName + " bloqué");
        return false;
      }

      // ÉTAPE 4 : Vérifier que le chemin DEPUIS le point de contournement est libre
      if (!isPathClear(bypassPoint, to, "depuis point " + sideName)) {
        VoiceRaycastDebugLogger.logLateralBypass(speaker, listener, sideOffset.length() * (sideName.equals("gauche") ? -1 : 1), false,
          "Chemin depuis point " + sideName + " bloqué - contournement impossible");
        return false;
      }

      // Si toutes les vérifications passent
      VoiceRaycastDebugLogger.logLateralBypass(speaker, listener, sideOffset.length() * (sideName.equals("gauche") ? -1 : 1), true,
        "Contournement " + sideName + " réussi - chemin entièrement libre (distance: " + String.format("%.2f", totalDistance) + ")");

      VoiceRaycastDebugVisualizer.showBypassRaycast(from, bypassPoint, to, true);

      return true;
    } catch (Exception e) {
      VoiceRaycastDebugLogger.logError("testCompleteBypassPath (" + sideName + ")", e);
      return false;
    }
  }

  /**
   * Vérifie qu'un chemin entre deux points est entièrement libre
   */
  private static boolean isPathClear(Location from, Location to, String pathDescription) {
    final var world = from.getWorld();
    if (world == null) return false;

    final var direction = to.toVector().subtract(from.toVector());
    final var distance = direction.length();

    if (distance < 0.1) return true; // Points trop proches

    direction.normalize();

    try {
      final var result = world.rayTraceBlocks(from, direction, distance, FluidCollisionMode.NEVER, true);

      final var isClear = result == null || result.getHitBlock() == null;

      if (DreamVoice.getService(VoiceWallService.class).isDebug()) {
        if (isClear) {
          VoiceRaycastDebugLogger.logDeveloperInfo("✓ Chemin " + pathDescription + " libre (distance: " + String.format("%.2f", distance) + ")");
        } else {
          String blockInfo = result.getHitBlock().getType().toString();
          VoiceRaycastDebugLogger.logDeveloperInfo("✗ Chemin " + pathDescription + " bloqué par: " + blockInfo +
            " à " + String.format("%.1f, %.1f, %.1f", (double)result.getHitBlock().getX(), (double)result.getHitBlock().getY(), (double)result.getHitBlock().getZ()));
        }
      }

      return isClear;
    } catch (Exception e) {
      VoiceRaycastDebugLogger.logError("isPathClear (" + pathDescription + ")", e);
      return false;
    }
  }

  /**
   * Méthode simplifiée pour vérifier l'accessibilité d'un point
   */
  private static boolean isPointAccessible(Location point) {
    final var world = point.getWorld();
    if (world == null) return false;

    // Vérification simple : le point et celui au-dessus doivent être libres
    final var groundFree = world.getBlockAt(point).getType().isAir();
    final var headFree = world.getBlockAt(point.clone().add(0, 1, 0)).getType().isAir();

    final var accessible = groundFree && headFree;

    if (DreamVoice.getService(VoiceWallService.class).isDebug() && !accessible) {
      VoiceRaycastDebugLogger.logDeveloperInfo("Point inaccessible: " +
        world.getBlockAt(point).getType() + " à " +
        String.format("%.1f, %.1f, %.1f", point.getX(), point.getY(), point.getZ()));
    }

    return accessible;
  }

  private static void logAndReturn(long startTime, Player speaker, Player listener, boolean result, String method) {
    final var endTime = System.currentTimeMillis();
    VoiceRaycastDebugLogger.logPerformanceStats(endTime - startTime, false);
    VoiceRaycastDebugLogger.logFinalResult(speaker, listener, result, method);
    VoiceRaycastDebugVisualizer.showLineOfSightSummary(speaker, listener, result);
  }

  // #################################################################
  // ---------------------- CONFIGURATION METHODS -------------------
  // #################################################################

  public static void setWallBypassWidth(double width) {
    wallBypassWidth = Math.max(0.5, width);
    if (DreamVoice.getService(VoiceWallService.class).isDebug()) VoiceRaycastDebugLogger.logDeveloperInfo("Largeur de contournement mise à jour: " + wallBypassWidth);
  }

  public static void setVerticalBypassEnabled(boolean enabled) {
    enableVerticalBypass = enabled;
    if (DreamVoice.getService(VoiceWallService.class).isDebug()) VoiceRaycastDebugLogger.logDeveloperInfo("Contournement vertical: " + (enabled ? "activé" : "désactivé"));
  }

  public static void setMaxBypassHeight(double height) {
    maxBypassHeight = Math.max(1.0, height);
    if (DreamVoice.getService(VoiceWallService.class).isDebug()) VoiceRaycastDebugLogger.logDeveloperInfo("Hauteur max de contournement mise à jour: " + maxBypassHeight);
  }

  public static void setBypassDistanceMultiplier(double multiplier) {
    bypassDistanceMultiplier = Math.max(1.0, multiplier);
    if (DreamVoice.getService(VoiceWallService.class).isDebug()) VoiceRaycastDebugLogger.logDeveloperInfo("Multiplicateur distance contournement mis à jour: " + bypassDistanceMultiplier);
  }

  public static boolean hasLineOfSightLegacy(Player speaker, Player listener) {
    return hasLineOfSight(speaker, listener);
  }

  public static void clearDebugDisplay() {
    VoiceRaycastDebugVisualizer.clearDebugDisplay();
  }

  // ###############################################################
  // ----------------------- CLASS ------------------------
  // ###############################################################

  @Getter
  public static class RaycastResult {
    public static final RaycastResult CLEAR = new RaycastResult(true, 0.0);

    public final boolean lineOfSight;
    public final double wallThickness;

    public RaycastResult(boolean lineOfSight, double wallThickness) {
      this.lineOfSight = lineOfSight;
      this.wallThickness = wallThickness;
    }

    public boolean isBlocked() { return !this.lineOfSight; }

    @Override
    public String toString() {
      return this.lineOfSight
        ? "RaycastResult{CLEAR}"
        : String.format("RaycastResult{BLOCKED, thickness=%.2f}", wallThickness);
    }
  }

}