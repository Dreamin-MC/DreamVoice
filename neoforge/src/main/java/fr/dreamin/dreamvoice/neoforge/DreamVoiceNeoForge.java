package fr.dreamin.dreamvoice.neoforge;

import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.common.DreamVoiceCommon;
import fr.dreamin.dreamvoice.neoforge.command.NeoForgeCommandUtils;
import fr.dreamin.dreamvoice.neoforge.command.scanner.CmdAnnotationProcessor;
import fr.dreamin.dreamvoice.neoforge.item.NeoForgeCassetteItem;
import fr.dreamin.dreamvoice.neoforge.lang.LanguageServerManager;
import fr.dreamin.dreamvoice.neoforge.network.scanner.PayloadAnnotationProcessor;
import fr.dreamin.dreamvoice.neoforge.platform.NeoForgeVoicePlatform;
import fr.dreamin.dreamvoice.neoforge.scanner.ClassScanner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.neoforge.NeoForgeServerCommandManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

@Mod(DreamVoiceNeoForge.MOD_ID)
public final class DreamVoiceNeoForge {

  public static final String MOD_ID = "dreamvoice";
  public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

  public static final Set<Class<?>> CLASSES = new HashSet<>();
  public static final int MAX_PACKET_STRING_LENGTH = 2097152;

  private static DreamVoiceNeoForge instance;
  private NeoForgeVoicePlatform platform;
  private DreamVoiceCommon common;
  private MinecraftServer server;
  private NeoForgeServerCommandManager<CommandSourceStack> commandManager;
  private AnnotationParser<CommandSourceStack> annotationParser;

  public static @NotNull Identifier id(final @NotNull String path) {
    return Identifier.fromNamespaceAndPath(MOD_ID, path);
  }

  public DreamVoiceNeoForge(final @NotNull IEventBus modEventBus) {
    instance = this;

    LanguageServerManager.load();
    scanClasses();

    setupCommands();

    modEventBus.addListener(this::registerPayloads);
    modEventBus.addListener(this::onCommonSetup);

    NeoForge.EVENT_BUS.addListener(this::onGatherPermissions);
    NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
    NeoForge.EVENT_BUS.addListener(this::onRightClickItem);
  }

  private void onCommonSetup(final @NotNull FMLCommonSetupEvent event) {
    LOGGER.info("DreamVoice NeoForge common setup completed.");
  }

  private void registerPayloads(final @NotNull RegisterPayloadHandlersEvent event) {
    new PayloadAnnotationProcessor(CLASSES, LOGGER).process(event);
  }

  private void setupCommands() {
    this.commandManager = new NeoForgeServerCommandManager<>(
      ExecutionCoordinator.simpleCoordinator(),
      SenderMapper.identity()
    );

    this.annotationParser = new AnnotationParser<>(this.commandManager, CommandSourceStack.class);
    new CmdAnnotationProcessor(this.annotationParser, CLASSES, LOGGER).process();
  }

  private void onGatherPermissions(final @NotNull PermissionGatherEvent.Nodes event) {
    final var permStrings = new HashSet<String>();
    for (final var clazz : CLASSES) {
      for (final var m : clazz.getDeclaredMethods()) {
        final var perm = m.getAnnotation(Permission.class);
        if (perm != null && perm.value() != null) {
          for (final var val : perm.value()) {
            if (val != null && !val.isBlank())
              permStrings.add(val);
          }
        }
      }
    }

    for (final var fullPerm : permStrings) {
      final var path = fullPerm.startsWith(MOD_ID + ".") ? fullPerm.substring(MOD_ID.length() + 1) : fullPerm;
      final var isAdmin = fullPerm.toLowerCase().contains("admin");
      final var node = new PermissionNode<>(
        MOD_ID,
        path,
        PermissionTypes.BOOLEAN,
        (player, uuid, contexts) -> !isAdmin || player == null || player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)
      );
      event.addNodes(node);
    }
    LOGGER.info("Registered {} permission nodes for DreamVoice", permStrings.size());
  }

  private void scanClasses() {
    try {
      CLASSES.addAll(ClassScanner.getClasses(MOD_ID, "fr.dreamin.dreamvoice.neoforge", true));
      LOGGER.info("{} classes found in neoforge package", CLASSES.size());
    } catch (final Exception e) {
      LOGGER.error("Failed to scan classes: {}", e.getMessage(), e);
    }
  }

  private void onServerStarting(final @NotNull ServerStartingEvent event) {
    this.server = event.getServer();
    this.platform = new NeoForgeVoicePlatform(this, this.server);
    this.common = new DreamVoiceCommon(this.platform);

    final var svcApi = DreamVoiceVoicechatPlugin.getSvcApi();
    if (svcApi != null) {
      this.common.onSvcStarted(svcApi);
      final var persistence = this.common.getPersistenceService();
      if (persistence != null)
        persistence.loadAll();
    }
  }

  private void onServerStarted(final @NotNull ServerStartedEvent event) {
    LOGGER.info("DreamVoice NeoForge initialized successfully.");
  }

  private void onServerStopping(final @NotNull ServerStoppingEvent event) {
    if (this.common != null)
      this.common.shutdown();
  }

  private void onPlayerLoggedOut(final @NotNull PlayerEvent.PlayerLoggedOutEvent event) {
    if (this.common != null)
      this.common.onPlayerDisconnect(event.getEntity().getUUID());
  }

  private void onRightClickItem(final @NotNull PlayerInteractEvent.RightClickItem event) {
    final var stack = event.getItemStack();
    final var recordingUuid = NeoForgeCassetteItem.getRecordingUuid(stack);
    if (recordingUuid != null) {
      final var player = event.getEntity();
      final var level = event.getLevel();
      if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
        final var api = DreamVoiceAPI.get();
        if (api.getRecordingService() != null && api.getAPI() != null) {
          final var rec = api.getRecordingService().getVoiceRecording(recordingUuid);
          if (rec != null) {
            final var conn = api.getAPI().getConnectionOf(serverPlayer.getUUID());
            if (conn != null) {
              api.getRecordingService().playRecordingTo(conn, rec);
              NeoForgeCommandUtils.sendSuccess(serverPlayer.createCommandSourceStack(), "record.playing", recordingUuid.toString().substring(0, 8), serverPlayer.getName().getString());
            } else
              NeoForgeCommandUtils.sendFailure(serverPlayer.createCommandSourceStack(), "record.player_not_connected", serverPlayer.getName().getString());
          } else
            NeoForgeCommandUtils.sendFailure(serverPlayer.createCommandSourceStack(), "record.not_found", recordingUuid.toString().substring(0, 8));
        }
      }
      event.setCancellationResult(InteractionResult.SUCCESS);
      event.setCanceled(true);
    }
  }

  public static @Nullable DreamVoiceNeoForge getInstance() {
    return instance;
  }

  public static @Nullable DreamVoiceCommon getCommon() {
    return instance != null ? instance.common : null;
  }

  public @Nullable MinecraftServer getServer() {
    return this.server;
  }

  public @Nullable NeoForgeVoicePlatform getPlatform() {
    return this.platform;
  }
}
