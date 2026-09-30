package fr.dreamin.dreamvoice.api.speaker.model;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import fr.dreamin.dreamvoice.api.DreamVoiceAPI;
import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import fr.dreamin.dreamvoice.api.speaker.event.SpeakerLinkPlayerEvent;
import fr.dreamin.dreamvoice.api.speaker.event.SpeakerUnlinkPlayerEvent;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Model representing a 3D locational speaker in the Minecraft world.
 * Supports dual channels (speech + music), static or mobile entity attachment, and access controls.
 */
@Getter
public final class Speaker {

  private final @NotNull UUID uuid;
  private final @NotNull String name;
  private @NotNull VoiceLocation location;

  @Setter
  private @NotNull SpeakerMode mode;
  private final @NotNull Set<UUID> allowedSpeakers = ConcurrentHashMap.newKeySet();

  @Setter
  private @Nullable AudioPlayer activeAudioPlayer = null;

  private @Nullable Float distance;
  private @Nullable Predicate<ServerPlayer> filter;

  private final @NotNull ServerLevel serverLevel;
  private @NotNull Position position;
  private final @NotNull LocationalAudioChannel speakerChannel;
  private final @Nullable LocationalAudioChannel voiceChannel;
  @Setter
  private @Nullable UUID targetEntityUuid;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  private static @NotNull VoiceSpeakerService speakerService() {
    final var api = DreamVoiceAPI.get();
    return Objects.requireNonNull(api.speakerService(), "VoiceSpeakerService is unavailable");
  }

  private Speaker(final @NotNull Builder builder) {
    final var speakerService = speakerService();

    this.uuid = builder.uuid != null ? builder.uuid : UUID.randomUUID();
    this.name = builder.name;
    this.location = builder.location;
    this.distance = builder.distance;
    this.filter = builder.filter;
    this.mode = builder.mode != null ? builder.mode : SpeakerMode.GLOBAL;
    this.targetEntityUuid = builder.targetEntityUuid;
    this.allowedSpeakers.addAll(builder.allowedSpeakers);

    this.serverLevel = speakerService.getServerLevel(location.world());
    this.position = speakerService.getAPI().createPosition(
      location.x(),
      location.y(),
      location.z()
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
    final var event = new SpeakerLinkPlayerEvent(this, playerUuid);
    if (!event.callEvent())
      return;
    this.allowedSpeakers.add(playerUuid);
  }

  public void unlinkSpeaker(final @NotNull UUID playerUuid) {
    if (this.allowedSpeakers.remove(playerUuid))
      new SpeakerUnlinkPlayerEvent(this, playerUuid).callEvent();
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

  public @NotNull VoiceLocation getLocation() {
    return this.location;
  }

  public void updatePosition(final @NotNull VoiceLocation location) {
    this.location = location;
    this.position = speakerService().getAPI()
      .createPosition(location.x(), location.y(), location.z());
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
    private VoiceLocation location;
    private Float distance = null;
    private Predicate<ServerPlayer> filter = null;
    private SpeakerMode mode = SpeakerMode.GLOBAL;
    private UUID targetEntityUuid = null;
    private final Set<UUID> allowedSpeakers = ConcurrentHashMap.newKeySet();

    public Builder uuid(final @NotNull UUID uuid) {
      this.uuid = uuid;
      return this;
    }

    public Builder name(final @NotNull String name) {
      this.name = name;
      return this;
    }

    public Builder location(final @NotNull VoiceLocation location) {
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

    public Builder targetEntity(final @Nullable UUID entityUuid) {
      this.targetEntityUuid = entityUuid;
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
