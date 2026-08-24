package fr.dreamin.dreamvoice.core.speaker.service;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import fr.dreamin.dreaminvoice.api.speaker.model.Speaker;
import fr.dreamin.dreaminvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreaminvoice.api.voice.event.MicrophonePacketEvent;
import fr.dreamin.dreamvoice.core.DreamVoice;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public final class VoiceSpeakerServiceImpl implements VoiceSpeakerService, Listener {

  private final @NotNull DreamVoice plugin;
  private @NotNull VoicechatServerApi api;

  private VolumeCategory volumeCategory;

  private final @NotNull Map<UUID, Speaker> speakers = new ConcurrentHashMap<>();

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoiceSpeakerServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;

    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  // ##############################################################
  // ---------------------- SERVICE METHODS -----------------------
  // ##############################################################

  @Override
  public VoicechatServerApi getAPI() {
    return this.api;
  }

  @Override
  public void init(final @NotNull VoicechatServerApi api) {
    this.api = api;

    this.volumeCategory = this.api.volumeCategoryBuilder()
      .setId("speaker_volume")
      .setName("Speaker")
      .setDescription("Speaker Volume")
      .build();

    this.api.registerVolumeCategory(this.volumeCategory);
  }

  @Override
  public Collection<Speaker> getSpeakers() {
    return this.speakers.values();
  }

  @Override
  public void register(@NotNull Speaker speaker) {
    this.speakers.put(speaker.getUuid(), speaker);
  }

  @Override
  public void unregister(@NotNull UUID uuid) {
    this.speakers.remove(uuid);
  }

  @Override
  public void unregister(@NotNull Speaker speaker) {
    unregister(speaker.getUuid());
  }

  @Override
  public void unregisterAll() {
    getSpeakers().forEach(this::unregister);
  }

  @Override
  public VolumeCategory getVolumeCategory() {
    return this.volumeCategory;
  }

  // ###############################################################
  // ---------------------- LISTENER METHODS -----------------------
  // ###############################################################

  @EventHandler
  private void onMicrophonePacket(final @NotNull MicrophonePacketEvent event) {
    getSpeakers().forEach(speaker -> {
      speaker.getSpeakerChannel().send(event.getPacket());
    });
  }

}
