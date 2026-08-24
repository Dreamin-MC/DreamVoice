
package fr.dreamin.dreamvoice.core.utils.raycast;

import fr.dreamin.dreaminvoice.api.wall.service.VoiceWallService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Utilitaire pour afficher des particules de debug pour les raycasts
 */
public class VoiceRaycastDebugVisualizer {

  // Configuration des particules
  private static final Particle SUCCESS_PARTICLE = Particle.HAPPY_VILLAGER;  // Vert pour succès
  private static final Particle BLOCKED_PARTICLE = Particle.WAX_OFF;         // Rouge pour bloqué
  private static final Particle BYPASS_PARTICLE = Particle.END_ROD;          // Blanc pour contournement
  private static final Particle VERTICAL_PARTICLE = Particle.WITCH;  // Violet pour vertical
  private static final Particle POINT_PARTICLE = Particle.WAX_ON;             // Orange pour points clés

  private static final double PARTICLE_SPACING = 0.3; // Distance entre particules
  private static final int PARTICLE_COUNT = 1;
  private static final double PARTICLE_SPEED = 0.0;

  public static void showRaycastLine(Location from, Location to, Particle particle, String debugMessage) {
    if (!DreamVoice.getService(VoiceWallService.class).isDebug()) return;

    final var world = from.getWorld();
    if (world == null) return;

    final var direction = to.toVector().subtract(from.toVector());
    final var distance = direction.length();
    direction.normalize();

    for (double d = 0; d <= distance; d += PARTICLE_SPACING) {
      final var particleLoc = from.clone().add(direction.clone().multiply(d));
      world.spawnParticle(particle, particleLoc, PARTICLE_COUNT, 0, 0, 0, PARTICLE_SPEED);
    }

    if (debugMessage != null && !debugMessage.isEmpty()) Bukkit.broadcastMessage("§6[VoiceDebug] §f" + debugMessage + " §7(Distance: " + String.format("%.2f", distance) + " blocs)");

  }

  public static void showDirectRaycast(Location from, Location to, boolean success) {
    final var particle = success ? SUCCESS_PARTICLE : BLOCKED_PARTICLE;
    final var message = success ? "§a✓ Raycast direct réussi" : "§c✗ Raycast direct bloqué";
    showRaycastLine(from, to, particle, message);

    showPoint(from, POINT_PARTICLE, "Point de départ");
    showPoint(to, POINT_PARTICLE, "Point d'arrivée");
  }

  public static void showBypassRaycast(Location from, Location bypass, Location to, boolean success) {
    final var particle = success ? SUCCESS_PARTICLE : BLOCKED_PARTICLE;

    showRaycastLine(from, bypass, BYPASS_PARTICLE, "§e→ Contournement latéral (partie 1/2)");

    showRaycastLine(bypass, to, particle, success ? "§a✓ Contournement latéral réussi (partie 2/2)" : "§c✗ Contournement latéral bloqué (partie 2/2)");

    showPoint(bypass, BYPASS_PARTICLE, "Point de contournement");
  }

  public static void showVerticalBypassRaycast(Location from, Location highPoint, Location to, boolean success) {
    final var particle = success ? SUCCESS_PARTICLE : BLOCKED_PARTICLE;

    showRaycastLine(from, highPoint, VERTICAL_PARTICLE, "§d↑ Contournement vertical (partie 1/2)");
    showRaycastLine(highPoint, to, particle, success ? "§a✓ Contournement vertical réussi (partie 2/2)" : "§c✗ Contournement vertical bloqué (partie 2/2)");
    showPoint(highPoint, VERTICAL_PARTICLE, "Point de contournement vertical");
  }

  public static void showPoint(Location location, Particle particle, String label) {
    if (!DreamVoice.getService(VoiceWallService.class).isDebug()) return;

    final var world = location.getWorld();
    if (world == null) return;

    for (int i = 0; i < 8; i++) {
      double angle = (i * Math.PI * 2) / 8;
      double x = Math.cos(angle) * 0.5;
      double z = Math.sin(angle) * 0.5;

      final var particleLoc = location.clone().add(x, 0, z);
      world.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
    }

    if (label != null && !label.isEmpty()) Bukkit.broadcastMessage("§6[VoiceDebug] §f" + label + " §7(" + String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ()) + ")");
  }

  public static void showLineOfSightSummary(Player speaker, Player listener, boolean result) {
    if (!DreamVoice.getService(VoiceWallService.class).isDebug()) return;

    final var speakerLoc = speaker.getEyeLocation();
    final var listenerLoc = listener.getEyeLocation();

    final var summaryParticle = result ? SUCCESS_PARTICLE : BLOCKED_PARTICLE;
    final var summaryMessage = result ? "§a✓ RÉSULTAT FINAL: " + speaker.getName() + " peut entendre " + listener.getName() : "§c✗ RÉSULTAT FINAL: " + speaker.getName() + " ne peut pas entendre " + listener.getName();

    showRaycastLine(speakerLoc, listenerLoc, summaryParticle, summaryMessage);
  }

  public static void clearDebugDisplay() {
    if (DreamVoice.getService(VoiceWallService.class).isDebug()) Bukkit.broadcastMessage("§6[VoiceDebug] §8Nettoyage de l'affichage debug...");

  }
}