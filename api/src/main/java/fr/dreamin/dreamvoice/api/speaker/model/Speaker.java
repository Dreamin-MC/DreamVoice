package fr.dreamin.dreamvoice.api.speaker.model;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import fr.dreamin.dreamapi.api.DreamAPI;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Getter
public final class Speaker {

  private final @Nullable VoiceSpeakerService speakerService = DreamAPI.getAPI().getService(VoiceSpeakerService.class);

  private final @NotNull UUID uuid;
  private final @NotNull String name;
  private @NotNull Location location;

  @Setter
  private @NotNull SpeakerMode mode = SpeakerMode.GLOBAL;
  private final @NotNull Set<UUID> allowedSpeakers = ConcurrentHashMap.newKeySet();

  @Setter
  private @Nullable AudioPlayer activeAudioPlayer = null;

  // Optionnels (nullables)
  private @Nullable Float distance;
  private @Nullable Predicate<ServerPlayer> filter;

  // Runtime (non-builder)
  private final @NotNull ServerLevel serverLevel;
  private @NotNull Position position;
  private final @NotNull LocationalAudioChannel speakerChannel;
  private final @Nullable LocationalAudioChannel voiceChannel;
  @Setter
  private @Nullable Entity targetEntity = null;


  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  private @NotNull VoiceSpeakerService speakerService() {
    return Objects.requireNonNull(this.speakerService, "VoiceSpeakerService is unavailable");
  }

  private Speaker(final @NotNull Builder builder) {
    final var speakerService = speakerService();

    this.uuid = builder.uuid != null ? builder.uuid : UUID.randomUUID();
    this.name = builder.name;
    this.location = builder.location;
    this.distance = builder.distance;
    this.filter = builder.filter;
    this.mode = builder.mode != null ? builder.mode : SpeakerMode.GLOBAL;
    this.allowedSpeakers.addAll(builder.allowedSpeakers);

    this.serverLevel = speakerService.getAPI().fromServerLevel(location.getWorld());
    this.position = speakerService.getAPI().createPosition(
      location.getX(),
      location.getY(),
      location.getZ()
    );

    final var channel = speakerService.getAPI()
      .createLocationalAudioChannel(
        this.uuid,
        this.serverLevel,
        this.position
      );

    if (channel == null)
      throw new IllegalArgumentException("Cannot create locational audio channel");

    channel.setCategory(speakerService.getVolumeCategory().getId());

    if (builder.distance != null)
      channel.setDistance(builder.distance);

    if (builder.filter != null)
      channel.setFilter(builder.filter);

    this.speakerChannel = channel;

    final var vChan = speakerService.getAPI()
      .createLocationalAudioChannel(
        UUID.randomUUID(),
        this.serverLevel,
        this.position
      );
    if (vChan != null) {
      vChan.setCategory(speakerService.getVolumeCategory().getId());
      if (builder.distance != null)
        vChan.setDistance(builder.distance);
      if (builder.filter != null)
        vChan.setFilter(builder.filter);
    }
    this.voiceChannel = vChan;

    speakerService.register(this);
  }


  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  public boolean isSpeakerAllowed(final @NotNull UUID speakerUuid) {
    if (this.mode == SpeakerMode.GLOBAL)
      return true;
    return this.allowedSpeakers.contains(speakerUuid);
  }

  public void linkSpeaker(final @NotNull UUID playerUuid) {
    this.allowedSpeakers.add(playerUuid);
  }

  public void unlinkSpeaker(final @NotNull UUID playerUuid) {
    this.allowedSpeakers.remove(playerUuid);
  }

  public void clearAllowedSpeakers() {
    this.allowedSpeakers.clear();
  }

  public @NotNull Set<UUID> getAllowedSpeakers() {
    return Collections.unmodifiableSet(this.allowedSpeakers);
  }

  public void stopPlaying() {
    if (this.activeAudioPlayer != null) {
      this.activeAudioPlayer.stopPlaying();
      this.activeAudioPlayer = null;
    }
  }

  public boolean isPlaying() {
    return this.activeAudioPlayer != null && this.activeAudioPlayer.isPlaying();
  }

  public @NotNull Location getLocation() {
    if (this.targetEntity != null && this.targetEntity.isValid()) {
      final var loc = this.targetEntity.getLocation();
      if (!loc.equals(this.location))
        updatePosition(loc);
      return loc;
    }
    return this.location;
  }

  public void updatePosition(final @NotNull Location location) {
    this.location = location;
    this.position = speakerService().getAPI()
      .createPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    this.speakerChannel.updateLocation(this.position);
    if (this.voiceChannel != null)
      this.voiceChannel.updateLocation(this.position);
  }

  public void updateDistance(final @NotNull Float distance) {
    this.distance = distance;
    this.speakerChannel.setDistance(distance);
    if (this.voiceChannel != null)
      this.voiceChannel.setDistance(distance);
  }

  public void updateFilter(final @Nullable Predicate<ServerPlayer> filter) {
    this.filter = filter;
    this.speakerChannel.setFilter(filter);
    if (this.voiceChannel != null)
      this.voiceChannel.setFilter(filter);
  }


  // ###############################################################
  // -------------------------- BUILDER ----------------------------
  // ###############################################################

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private UUID uuid;
    private String name;
    private Location location;
    private Float distance = null;
    private Predicate<ServerPlayer> filter = null;
    private SpeakerMode mode = SpeakerMode.GLOBAL;
    private final Set<UUID> allowedSpeakers = ConcurrentHashMap.newKeySet();

    public Builder uuid(final @NotNull UUID uuid) {
      this.uuid = uuid;
      return this;
    }

    public Builder name(final @NotNull String name) {
      this.name = name;
      return this;
    }

    public Builder location(final @NotNull Location location) {
      this.location = location;
      return this;
    }

    public Builder distance(final @NotNull Float distance) {
      this.distance = distance;
      return this;
    }

    public Builder filter(final @NotNull Predicate<ServerPlayer> filter) {
      this.filter = filter;
      return this;
    }

    public Builder mode(final @NotNull SpeakerMode mode) {
      this.mode = mode;
      return this;
    }

    public Builder allowSpeaker(final @NotNull UUID playerUuid) {
      this.allowedSpeakers.add(playerUuid);
      return this;
    }

    public Speaker build() {
      if (this.name == null || this.location == null)
        throw new IllegalStateException("Cannot build Speaker without name or location");

      return new Speaker(this);
    }

  }

}
