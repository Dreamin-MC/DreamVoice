package fr.dreamin.dreamvoice.api.speaker.model;

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.ServerLevel;
import de.maxhenkel.voicechat.api.ServerPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import fr.dreamin.dreamapi.api.DreamAPI;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import lombok.Getter;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Predicate;

@Getter
public final class Speaker {

  private final @NotNull VoiceSpeakerService speakerService = DreamAPI.getAPI().getService(VoiceSpeakerService.class);

  private final @NotNull UUID uuid;
  private final @NotNull String name;
  private @NotNull Location location;

  // Optionnels (nullables)
  private @Nullable Float distance;
  private @Nullable Predicate<ServerPlayer> filter;

  // Runtime (non-builder)
  private final @NotNull ServerLevel serverLevel;
  private @NotNull Position position;
  private final @NotNull LocationalAudioChannel speakerChannel;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  private Speaker(final @NotNull Builder builder) {
    this.uuid = builder.uuid != null ? builder.uuid : UUID.randomUUID();
    this.name = builder.name;
    this.location = builder.location;
    this.distance = builder.distance;
    this.filter = builder.filter;

    this.serverLevel = this.speakerService.getAPI().fromServerLevel(location.getWorld());
    this.position = this.speakerService.getAPI().createPosition(
      location.getX(),
      location.getY(),
      location.getZ()
    );

    final var channel = this.speakerService.getAPI()
      .createLocationalAudioChannel(
        this.uuid,
        this.serverLevel,
        this.position
      );

    if (channel == null)
      throw new IllegalArgumentException("Cannot create locational audio channel");

    channel.setCategory(this.speakerService.getVolumeCategory().getId());

    if (builder.distance != null)
      channel.setDistance(builder.distance);

    if (builder.filter != null)
      channel.setFilter(builder.filter);

    this.speakerChannel = channel;

    this.speakerService.register(this);
  }

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  public void updatePosition(final @NotNull Location location) {
    this.location = location;
    this.position = speakerService.getAPI()
      .createPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    this.speakerChannel.updateLocation(this.position);
  }

  public void updateDistance(final @NotNull Float distance) {
    this.distance = distance;
    this.speakerChannel.setDistance(distance);
  }

  public void updateFilter(final @Nullable Predicate<ServerPlayer> filter) {
    this.filter = filter;
    this.speakerChannel.setFilter(filter);
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

    public Speaker build() {
      if (this.name == null || this.location == null)
        throw new IllegalStateException("Cannot build Speaker without name or location");

      return new Speaker(this);
    }

  }

}

