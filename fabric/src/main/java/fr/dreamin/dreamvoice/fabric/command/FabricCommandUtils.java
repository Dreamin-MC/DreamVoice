package fr.dreamin.dreamvoice.fabric.command;

import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.fabric.lang.LanguageServerManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FabricCommandUtils {

  public static VoiceLocation toVoiceLocation(final @NotNull ServerPlayer player) {
    return new VoiceLocation(
      player.level().dimension().identifier().toString(),
      player.getX(), player.getY(), player.getZ(),
      player.getYRot(), player.getXRot()
    );
  }

  public static @Nullable ServerPlayer resolvePlayer(final @NotNull CommandSourceStack source, final @Nullable String targetName) {
    if (targetName != null && !targetName.isBlank())
      return source.getServer().getPlayerList().getPlayerByName(targetName);
    return source.isPlayer() ? source.getPlayer() : null;
  }

  public static String resolvePlayerName(final @NotNull MinecraftServer server, final @NotNull UUID uuid) {
    final var online = server.getPlayerList().getPlayer(uuid);
    if (online != null)
      return online.getName().getString();
    try {
      final var cached = server.services().profileResolver().fetchById(uuid);
      if (cached.isPresent())
        return cached.get().name();
    } catch (final Exception ignored) {}
    return uuid.toString().substring(0, 8);
  }

  public static List<String> suggestPlayers(final @NotNull CommandSourceStack source, final @NotNull String input) {
    final var list = new ArrayList<String>();
    for (final var name : source.getServer().getPlayerNames())
      if (name.toLowerCase().startsWith(input.toLowerCase()))
        list.add(name);
    return list;
  }

  public static void sendSuccess(final @NotNull CommandSourceStack source, final @NotNull String key, final Object... args) {
    LanguageServerManager.sendSuccess(source, key, args);
  }

  public static void sendFailure(final @NotNull CommandSourceStack source, final @NotNull String key, final Object... args) {
    LanguageServerManager.sendFailure(source, key, args);
  }

  public static Component translate(final @NotNull CommandSourceStack source, final @NotNull String key, final Object... args) {
    return LanguageServerManager.translate(source, key, args);
  }
}
