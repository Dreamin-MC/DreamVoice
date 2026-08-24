package fr.dreamin.dreamvoice.api.voice.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import fr.dreamin.dreamvoice.api.voice.model.VoiceSoundBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

public interface VoiceService {

  boolean isDebug();
  void setDebug(final boolean value);

  // ###############################################################
  // --------------------------- SOUNDS ---------------------------
  // ###############################################################

  VoicechatServerApi getAPI();

  void playSound(final @NotNull VoiceSoundBuilder builder);

  int getActiveSoundCount();

  Set<UUID> getActiveSoundIds();

  boolean stopSound(final @NotNull UUID soundId);

  void clearAllSounds();

  // ###############################################################
  // -------------------------- PLAYERS ----------------------------
  // ###############################################################

  boolean isPlayerConnected(final @NotNull UUID uuid);

  OpusDecoder getDecoder(final @NotNull UUID uuid);
  OpusEncoder getEncoder(final @NotNull UUID uuid);

}
