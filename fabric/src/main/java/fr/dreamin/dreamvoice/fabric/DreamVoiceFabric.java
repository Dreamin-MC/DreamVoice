package fr.dreamin.dreamvoice.fabric;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.fabric.command.FabricCommandUtils;
import fr.dreamin.dreamvoice.fabric.command.scanner.CmdAnnotationProcessor;
import fr.dreamin.dreamvoice.fabric.item.FabricCassetteItem;
import fr.dreamin.dreamvoice.fabric.lang.LanguageServerManager;
import fr.dreamin.dreamvoice.fabric.network.scanner.PacketAnnotationProcessor;
import fr.dreamin.dreamvoice.fabric.network.scanner.ServerPacketAnnotationProcessor;
import fr.dreamin.dreamvoice.fabric.platform.FabricVoicePlatform;
import fr.dreamin.dreamvoice.fabric.scanner.ClassScanner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.fabric.FabricServerCommandManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public final class DreamVoiceFabric implements ModInitializer {

  public static final String MOD_ID = "dreamvoice";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  public static final Set<Class<?>> CLASSES = new HashSet<>();
  public static final int MAX_PACKET_STRING_LENGTH = 2097152;

  private static DreamVoiceFabric instance;
  private FabricVoicePlatform platform;
  private DreamVoiceCommon common;
  private MinecraftServer server;
  private FabricServerCommandManager<CommandSourceStack> commandManager;
  private AnnotationParser<CommandSourceStack> annotationParser;

  public static @NotNull Identifier id(final @NotNull String path) {
    return Identifier.fromNamespaceAndPath(MOD_ID, path);
  }

  @Override
  public void onInitialize() {
    instance = this;

    LanguageServerManager.load();
    scanClasses();

    new PacketAnnotationProcessor(CLASSES, LOGGER).process();
    new ServerPacketAnnotationProcessor(CLASSES, LOGGER).process();

    setupCommands();

    ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
    ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
    ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

    ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
      if (this.common != null)
        this.common.onPlayerDisconnect(handler.getPlayer().getUUID());
    });

    UseItemCallback.EVENT.register((player, world, hand) -> {
      final var stack = player.getItemInHand(hand);
      final var recordingUuid = FabricCassetteItem.getRecordingUuid(stack);
      if (recordingUuid != null) {
        if (!world.isClientSide() && player instanceof ServerPlayer serverPlayer) {
          final var api = DreamVoiceAPI.get();
          if (api.getRecordingService() != null && api.getAPI() != null) {
            final var rec = api.getRecordingService().getVoiceRecording(recordingUuid);
            if (rec != null) {
              final var conn = api.getAPI().getConnectionOf(serverPlayer.getUUID());
              if (conn != null) {
                api.getRecordingService().playRecordingTo(conn, rec);
                FabricCommandUtils.sendSuccess(serverPlayer.createCommandSourceStack(), "record.playing", recordingUuid.toString().substring(0, 8), serverPlayer.getName().getString());
              } else
                FabricCommandUtils.sendFailure(serverPlayer.createCommandSourceStack(), "record.player_not_connected", serverPlayer.getName().getString());
            } else
              FabricCommandUtils.sendFailure(serverPlayer.createCommandSourceStack(), "record.not_found", recordingUuid.toString().substring(0, 8));
          }
        }
        return InteractionResult.SUCCESS;
      }
      return InteractionResult.PASS;
    });
  }

  private void scanClasses() {
    try {
      CLASSES.addAll(ClassScanner.getClasses(MOD_ID, "fr.dreamin.dreamvoice.fabric", true));
      LOGGER.info("{} classes found in fabric package", CLASSES.size());
    } catch (final Exception e) {
      LOGGER.error("Failed to scan classes: {}", e.getMessage(), e);
    }
  }

  private void setupCommands() {
    this.commandManager = new FabricServerCommandManager<>(
      ExecutionCoordinator.simpleCoordinator(),
      SenderMapper.identity()
    );

    this.annotationParser = new AnnotationParser<>(this.commandManager, CommandSourceStack.class);
    new CmdAnnotationProcessor(this.annotationParser, CLASSES, LOGGER).process();
  }

  private void onServerStarting(final @NotNull MinecraftServer server) {
    this.server = server;
    this.platform = new FabricVoicePlatform(this, server);
    this.common = new DreamVoiceCommon(this.platform);

    final var svcApi = DreamVoiceVoicechatPlugin.getSvcApi();
    if (svcApi != null) {
      this.common.onSvcStarted(svcApi);
      final var persistence = this.common.getPersistenceService();
      if (persistence != null)
        persistence.loadAll();
    }
  }

  private void onServerStarted(final @NotNull MinecraftServer server) {
    LOGGER.info("DreamVoice Fabric initialized successfully.");
  }

  private void onServerStopping(final @NotNull MinecraftServer server) {
    if (this.common != null)
      this.common.shutdown();
  }

  public static @Nullable DreamVoiceFabric getInstance() {
    return instance;
  }

  public static @Nullable DreamVoiceCommon getCommon() {
    return instance != null ? instance.common : null;
  }

  public @Nullable MinecraftServer getServer() {
    return this.server;
  }

  public @Nullable FabricVoicePlatform getPlatform() {
    return this.platform;
  }
}
