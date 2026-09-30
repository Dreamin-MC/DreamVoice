package fr.dreamin.dreamvoice.fabric.platform;

import de.maxhenkel.voicechat.api.ServerLevel;
import fr.dreamin.dreamvoice.api.event.VoiceEvent;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.wall.model.WallConfig;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.common.platform.VoicePlatform;
import fr.dreamin.dreamvoice.fabric.DreamVoiceFabric;
import fr.dreamin.dreamvoice.fabric.DreamVoiceVoicechatPlugin;
import fr.dreamin.dreamvoice.fabric.event.DreamVoiceEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Fabric implementation of {@link VoicePlatform} backed by Minecraft Server, Fabric API, and SLF4J.
 */
public final class FabricVoicePlatform implements VoicePlatform {

  private static final Logger LOGGER = LoggerFactory.getLogger(DreamVoiceFabric.MOD_ID);

  private final @NotNull DreamVoiceFabric mod;
  private final @NotNull MinecraftServer server;
  private final @NotNull Path dataDirectory;
  private final ConcurrentLinkedQueue<TickTask> tasks = new ConcurrentLinkedQueue<>();

  public FabricVoicePlatform(final @NotNull DreamVoiceFabric mod, final @NotNull MinecraftServer server) {
    this.mod = mod;
    this.server = server;
    this.dataDirectory = FabricLoader.getInstance().getConfigDir().resolve("dreamvoice");

    ServerTickEvents.END_SERVER_TICK.register(srv -> tick());
  }

  private void tick() {
    final var it = this.tasks.iterator();
    while (it.hasNext()) {
      final var task = it.next();
      task.remainingTicks--;
      if (task.remainingTicks <= 0) {
        try {
          task.runnable.run();
        } catch (final Throwable t) {
          LOGGER.error("Error executing tick task", t);
        }
        if (task.periodTicks > 0)
          task.remainingTicks = task.periodTicks;
        else
          it.remove();
      }
    }
  }

  @Override
  public @NotNull Path getDataDirectory() {
    return this.dataDirectory;
  }

  @Override
  public void runAsync(final @NotNull Runnable runnable) {
    CompletableFuture.runAsync(runnable);
  }

  @Override
  public void runSync(final @NotNull Runnable runnable) {
    if (this.server.isSameThread())
      runnable.run();
    else
      this.server.execute(runnable);
  }

  @Override
  public void runLater(final @NotNull Runnable runnable, final long delayTicks) {
    this.tasks.add(new TickTask(runnable, delayTicks, 0));
  }

  @Override
  public void runTimer(final @NotNull Runnable runnable, final long delayTicks, final long periodTicks) {
    this.tasks.add(new TickTask(runnable, delayTicks, periodTicks));
  }

  @Override
  public @NotNull Optional<VoiceLocation> getPlayerLocation(final @NotNull UUID playerUuid) {
    final var player = this.server.getPlayerList().getPlayer(playerUuid);
    if (player == null)
      return Optional.empty();

    final var level = player.level();
    final var worldName = level.dimension().identifier().toString();
    return Optional.of(new VoiceLocation(worldName, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
  }

  @Override
  public @NotNull Optional<VoiceLocation> getPlayerEyeLocation(final @NotNull UUID playerUuid) {
    final var player = this.server.getPlayerList().getPlayer(playerUuid);
    if (player == null)
      return Optional.empty();

    final var level = player.level();
    final var worldName = level.dimension().identifier().toString();
    final var eye = player.getEyePosition();
    return Optional.of(new VoiceLocation(worldName, eye.x, eye.y, eye.z, player.getYRot(), player.getXRot()));
  }

  @Override
  public boolean isPlayerOnline(final @NotNull UUID playerUuid) {
    return this.server.getPlayerList().getPlayer(playerUuid) != null;
  }

  @Override
  public @NotNull String getPlayerName(final @NotNull UUID playerUuid) {
    final var player = this.server.getPlayerList().getPlayer(playerUuid);
    if (player != null)
      return player.getName().getString();
    return playerUuid.toString();
  }

  @Override
  public @NotNull Collection<UUID> getOnlinePlayers() {
    final var list = new ArrayList<UUID>();
    for (final var player : this.server.getPlayerList().getPlayers())
      list.add(player.getUUID());
    return list;
  }

  @Override
  public @NotNull Optional<VoiceLocation> getEntityLocation(final @NotNull UUID entityUuid) {
    for (final var level : this.server.getAllLevels()) {
      final var entity = level.getEntity(entityUuid);
      if (entity != null) {
        final var worldName = level.dimension().identifier().toString();
        return Optional.of(new VoiceLocation(worldName, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot()));
      }
    }
    return Optional.empty();
  }

  @Override
  public boolean isEntityValid(final @NotNull UUID entityUuid) {
    for (final var level : this.server.getAllLevels()) {
      final var entity = level.getEntity(entityUuid);
      if (entity != null && entity.isAlive())
        return true;
    }
    return false;
  }

  @Override
  public @Nullable ServerLevel getServerLevel(final @NotNull String worldName) {
    final var level = findLevel(worldName);
    if (level == null)
      return null;

    final var common = DreamVoiceCommon.getInstance();
    var api = common != null ? common.getAPI() : null;
    if (api == null)
      api = DreamVoiceVoicechatPlugin.getSvcApi();

    return api != null ? api.fromServerLevel(level) : null;
  }

  private @Nullable net.minecraft.server.level.ServerLevel findLevel(final @NotNull String worldName) {
    for (final var level : this.server.getAllLevels()) {
      final var id = level.dimension().identifier();
      if (id.toString().equalsIgnoreCase(worldName) || id.getPath().equalsIgnoreCase(worldName))
        return level;
    }
    return null;
  }

  @Override
  public double computeAcousticOcclusion(
    final @NotNull VoiceLocation from,
    final @NotNull VoiceLocation to,
    final @Nullable WallConfig wallConfig
  ) {
    final var level = findLevel(from.world());
    if (level == null)
      return 0.0;

    final var start = new Vec3(from.x(), from.y(), from.z());
    final var end = new Vec3(to.x(), to.y(), to.z());
    final var diff = end.subtract(start);
    final var distance = diff.length();
    if (distance <= 0.001)
      return 0.0;

    final var steps = (int) Math.ceil(distance * 4.0);
    final var stepVec = diff.scale(1.0 / steps);
    var current = start;
    var totalOcclusion = 0.0;
    BlockPos lastPos = null;

    for (var i = 0; i <= steps; i++) {
      final var pos = BlockPos.containing(current.x, current.y, current.z);

      if (!pos.equals(lastPos)) {
        lastPos = pos;
        final var state = level.getBlockState(pos);
        if (!state.isAir()) {
          final var blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
          final var loss = wallConfig != null ? wallConfig.getAttenuationDb(blockId) : 3.0;
          totalOcclusion += loss;
        }
      }
      current = current.add(stepVec);
    }

    return totalOcclusion;
  }

  @Override
  public boolean hasLineOfSight(final @NotNull VoiceLocation from, final @NotNull VoiceLocation to) {
    final var level = findLevel(from.world());
    if (level == null)
      return false;

    final var start = new Vec3(from.x(), from.y(), from.z());
    final var end = new Vec3(to.x(), to.y(), to.z());
    final var hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
    return hit.getType() == HitResult.Type.MISS;
  }

  @Override
  public void sendMessage(final @NotNull UUID playerUuid, final @NotNull String messageKey, final Object... args) {
    final var player = this.server.getPlayerList().getPlayer(playerUuid);
    if (player != null)
      player.sendSystemMessage(Component.translatable(messageKey, args));
  }

  @Override
  public void broadcastMessage(final @NotNull String messageKey, final @Nullable String permission, final Object... args) {
    final var component = Component.translatable(messageKey, args);
    for (final var player : this.server.getPlayerList().getPlayers()) {
      if (permission == null || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
        player.sendSystemMessage(component);
    }
  }

  @Override
  public void playSoundEffect(final @NotNull VoiceLocation location, final @NotNull String soundKey, final float volume, final float pitch) {
    final var level = findLevel(location.world());
    if (level == null)
      return;

    final var id = Identifier.tryParse(soundKey.toLowerCase().replace('_', '.'));
    if (id == null)
      return;

    final var sound = BuiltInRegistries.SOUND_EVENT.getValue(id);
    if (sound != null)
      level.playSound(null, location.x(), location.y(), location.z(), sound, SoundSource.MASTER, volume, pitch);
  }

  @Override
  public void renderDebugRay(final @NotNull UUID viewerUuid, final @NotNull VoiceLocation from, final @NotNull VoiceLocation to, final boolean directHit) {
    final var player = this.server.getPlayerList().getPlayer(viewerUuid);
    if (player == null)
      return;

    final var level = player.level();
    final var start = new Vec3(from.x(), from.y(), from.z());
    final var end = new Vec3(to.x(), to.y(), to.z());
    final var diff = end.subtract(start);
    final var dist = diff.length();
    if (dist <= 0.001)
      return;

    final var steps = (int) Math.ceil(dist * 2.0);
    final var step = diff.scale(1.0 / steps);
    final var particle = directHit
      ? new DustParticleOptions(0x00FF00, 0.7f)
      : new DustParticleOptions(0xFF0000, 0.7f);

    var cur = start;
    for (var i = 0; i <= steps; i++) {
      level.sendParticles(player, particle, true, false, cur.x, cur.y, cur.z, 1, 0.0, 0.0, 0.0, 0.0);
      cur = cur.add(step);
    }
  }

  @Override
  public boolean isPlayerSubmerged(final @NotNull UUID playerUuid) {
    final var player = this.server.getPlayerList().getPlayer(playerUuid);
    return player != null && (player.isEyeInFluid(FluidTags.WATER) || player.isEyeInFluid(FluidTags.LAVA));
  }

  @Override
  public boolean isPlayerInCave(final @NotNull UUID playerUuid) {
    final var player = this.server.getPlayerList().getPlayer(playerUuid);
    if (player == null)
      return false;

    final var pos = player.blockPosition();
    return player.level().getBrightness(LightLayer.SKY, pos) == 0 && pos.getY() < 50;
  }

  @Override
  public void dispatchEvent(final @NotNull VoiceEvent event) {
    DreamVoiceEvents.VOICE_EVENT.invoker().onVoiceEvent(event);
  }

  @Override
  public void logInfo(final @NotNull String message) {
    LOGGER.info(message);
  }

  @Override
  public void logWarning(final @NotNull String message) {
    LOGGER.warn(message);
  }

  @Override
  public void logError(final @NotNull String message, final @Nullable Throwable throwable) {
    LOGGER.error(message, throwable);
  }

  private static final class TickTask {
    private final Runnable runnable;
    private long remainingTicks;
    private final long periodTicks;

    private TickTask(final Runnable runnable, final long delayTicks, final long periodTicks) {
      this.runnable = runnable;
      this.remainingTicks = delayTicks;
      this.periodTicks = periodTicks;
    }
  }
}
