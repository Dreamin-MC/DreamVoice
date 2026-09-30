package fr.dreamin.dreamvoice.core.platform;

import de.maxhenkel.voicechat.api.ServerLevel;
import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.wall.model.WallConfig;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.utils.raycast.VoiceRayCast;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Paper implementation of {@link VoicePlatform} backed by Bukkit, Paper API, and Adventure.
 */
public final class PaperVoicePlatform implements VoicePlatform {

  private final @NotNull DreamVoice plugin;

  public PaperVoicePlatform(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;
  }

  @Override
  public @NotNull Path getDataDirectory() {
    return this.plugin.getDataFolder().toPath();
  }

  @Override
  public void runAsync(final @NotNull Runnable runnable) {
    Bukkit.getScheduler().runTaskAsynchronously(this.plugin, runnable);
  }

  @Override
  public void runSync(final @NotNull Runnable runnable) {
    Bukkit.getScheduler().runTask(this.plugin, runnable);
  }

  @Override
  public void runLater(final @NotNull Runnable runnable, final long delayTicks) {
    Bukkit.getScheduler().runTaskLater(this.plugin, runnable, delayTicks);
  }

  @Override
  public void runTimer(final @NotNull Runnable runnable, final long delayTicks, final long periodTicks) {
    Bukkit.getScheduler().runTaskTimer(this.plugin, runnable, delayTicks, periodTicks);
  }

  @Override
  public @NotNull Optional<VoiceLocation> getPlayerLocation(final @NotNull UUID playerUuid) {
    final var player = Bukkit.getPlayer(playerUuid);
    if (player == null || !player.isOnline())
      return Optional.empty();

    final var loc = player.getLocation();
    final var world = loc.getWorld();
    if (world == null)
      return Optional.empty();

    return Optional.of(new VoiceLocation(world.getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
  }

  @Override
  public @NotNull Optional<VoiceLocation> getPlayerEyeLocation(final @NotNull UUID playerUuid) {
    final var player = Bukkit.getPlayer(playerUuid);
    if (player == null || !player.isOnline())
      return Optional.empty();

    final var loc = player.getEyeLocation();
    final var world = loc.getWorld();
    if (world == null)
      return Optional.empty();

    return Optional.of(new VoiceLocation(world.getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
  }

  @Override
  public boolean isPlayerOnline(final @NotNull UUID playerUuid) {
    final var player = Bukkit.getPlayer(playerUuid);
    return player != null && player.isOnline();
  }

  @Override
  public @NotNull String getPlayerName(final @NotNull UUID playerUuid) {
    final var player = Bukkit.getPlayer(playerUuid);
    if (player != null)
      return player.getName();

    final var offline = Bukkit.getOfflinePlayer(playerUuid);
    final var name = offline.getName();
    return name != null ? name : playerUuid.toString();
  }

  @Override
  public @NotNull Collection<UUID> getOnlinePlayers() {
    return Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
  }

  @Override
  public @NotNull Optional<VoiceLocation> getEntityLocation(final @NotNull UUID entityUuid) {
    final var entity = Bukkit.getEntity(entityUuid);
    if (entity == null || !entity.isValid())
      return Optional.empty();

    final var loc = entity.getLocation();
    final var world = loc.getWorld();
    if (world == null)
      return Optional.empty();

    return Optional.of(new VoiceLocation(world.getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()));
  }

  @Override
  public boolean isEntityValid(final @NotNull UUID entityUuid) {
    final var entity = Bukkit.getEntity(entityUuid);
    return entity != null && entity.isValid();
  }

  @Override
  public @Nullable ServerLevel getServerLevel(final @NotNull String worldName) {
    final var world = Bukkit.getWorld(worldName);
    if (world == null)
      return null;

    final var api = DreamVoiceAPI.get().getAPI();
    return api != null ? api.fromServerLevel(world) : null;
  }

  @Override
  public double computeAcousticOcclusion(
    final @NotNull VoiceLocation from,
    final @NotNull VoiceLocation to,
    final @Nullable WallConfig wallConfig
  ) {
    final var locFrom = toBukkitLocation(from);
    final var locTo = toBukkitLocation(to);
    if (locFrom == null || locTo == null)
      return 0.0;

    return VoiceRayCast.check(locFrom, locTo).totalAttenuation();
  }

  @Override
  public boolean hasLineOfSight(final @NotNull VoiceLocation from, final @NotNull VoiceLocation to) {
    final var locFrom = toBukkitLocation(from);
    final var locTo = toBukkitLocation(to);
    if (locFrom == null || locTo == null)
      return true;

    return VoiceRayCast.check(locFrom, locTo).lineOfSight();
  }

  @Override
  public void sendMessage(final @NotNull UUID playerUuid, final @NotNull String messageKey, final Object... args) {
    final var player = Bukkit.getPlayer(playerUuid);
    if (player != null)
      player.sendMessage(Component.translatable(messageKey));
  }

  @Override
  public void broadcastMessage(final @NotNull String messageKey, final @Nullable String permission, final Object... args) {
    final var component = Component.translatable(messageKey);
    for (final var player : Bukkit.getOnlinePlayers()) {
      if (permission == null || player.hasPermission(permission))
        player.sendMessage(component);
    }
  }

  @Override
  public void playSoundEffect(final @NotNull VoiceLocation location, final @NotNull String soundKey, final float volume, final float pitch) {
    final var loc = toBukkitLocation(location);
    if (loc == null || loc.getWorld() == null)
      return;

    try {
      final var sound = Sound.valueOf(soundKey.toUpperCase().replace('.', '_'));
      loc.getWorld().playSound(loc, sound, volume, pitch);
    } catch (final IllegalArgumentException e) {
      loc.getWorld().playSound(loc, soundKey, volume, pitch);
    }
  }

  @Override
  public void renderDebugRay(
    final @NotNull UUID viewerUuid,
    final @NotNull VoiceLocation from,
    final @NotNull VoiceLocation to,
    final boolean directHit
  ) {
    final var viewer = Bukkit.getPlayer(viewerUuid);
    if (viewer == null || !viewer.isOnline())
      return;

    final var locFrom = toBukkitLocation(from);
    final var locTo = toBukkitLocation(to);
    if (locFrom == null || locTo == null || locFrom.getWorld() == null)
      return;

    final var world = locFrom.getWorld();
    final var dir = locTo.toVector().subtract(locFrom.toVector());
    final var length = dir.length();
    if (length < 0.1)
      return;

    final var step = 0.5;
    final var steps = (int) (length / step);
    final var stepVec = dir.clone().normalize().multiply(step);
    final var current = locFrom.clone();
    final var color = directHit ? Color.GREEN : Color.RED;
    final var dust = new Particle.DustOptions(color, 1.0f);

    for (int i = 0; i <= steps; i++) {
      viewer.spawnParticle(Particle.DUST, current, 1, dust);
      current.add(stepVec);
    }
  }

  @Override
  public boolean isPlayerSubmerged(final @NotNull UUID playerUuid) {
    final var player = Bukkit.getPlayer(playerUuid);
    if (player == null || !player.isOnline())
      return false;

    return player.getEyeLocation().getBlock().isLiquid();
  }

  @Override
  public boolean isPlayerInCave(final @NotNull UUID playerUuid) {
    final var player = Bukkit.getPlayer(playerUuid);
    if (player == null || !player.isOnline())
      return false;

    final var loc = player.getEyeLocation();
    return loc.getY() < 55 && loc.getBlock().getLightFromSky() == 0;
  }

  @Override
  public void dispatchEvent(final @NotNull VoiceEvent event) {
  }

  @Override
  public void logInfo(final @NotNull String message) {
    this.plugin.getLogger().info(message);
  }

  @Override
  public void logWarning(final @NotNull String message) {
    this.plugin.getLogger().warning(message);
  }

  @Override
  public void logError(final @NotNull String message, final @Nullable Throwable throwable) {
    if (throwable != null)
      this.plugin.getLogger().log(Level.SEVERE, message, throwable);
    else
      this.plugin.getLogger().severe(message);
  }

  private @Nullable Location toBukkitLocation(final @NotNull VoiceLocation vLoc) {
    final var world = Bukkit.getWorld(vLoc.world());
    if (world == null)
      return null;
    return new Location(world, vLoc.x(), vLoc.y(), vLoc.z(), vLoc.yaw(), vLoc.pitch());
  }

}
