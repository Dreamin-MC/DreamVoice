package fr.dreamin.dreamvoice.api.broadcast.model;

import fr.dreamin.dreamvoice.api.model.VoiceLocation;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model representing a physical microphone, podium, or intercom broadcast point.
 * When players speak within its radius, audio is broadcast directly to targeted speakers.
 */
@Getter
public final class BroadcastPoint {

  private final @NotNull UUID uuid;
  private final @NotNull String name;
  @Setter
  private @NotNull VoiceLocation location;
  @Setter
  private @Nullable UUID targetEntityUuid;
  @Setter
  private double radius;
  @Setter
  private boolean allSpeakers;
  private final @NotNull Set<String> targetSpeakers = ConcurrentHashMap.newKeySet();
  @Setter
  private @Nullable String filterId;
  @Setter
  private boolean enabled;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public BroadcastPoint(
    final @NotNull UUID uuid,
    final @NotNull String name,
    final @NotNull VoiceLocation location,
    final double radius,
    final boolean allSpeakers,
    final @Nullable String filterId,
    final boolean enabled
  ) {
    this.uuid = Objects.requireNonNull(uuid, "uuid cannot be null");
    this.name = Objects.requireNonNull(name, "name cannot be null");
    this.location = Objects.requireNonNull(location, "location cannot be null");
    this.radius = Math.max(0.5, radius);
    this.allSpeakers = allSpeakers;
    this.filterId = filterId;
    this.enabled = enabled;
  }

  public BroadcastPoint(final @NotNull String name, final @NotNull VoiceLocation location) {
    this(UUID.randomUUID(), name, location, 2.0, true, null, true);
  }

  public BroadcastPoint(final @NotNull String name, final @NotNull VoiceLocation location, final @Nullable UUID targetEntityUuid) {
    this(UUID.randomUUID(), name, location, 2.0, true, null, true);
    this.targetEntityUuid = targetEntityUuid;
  }

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  public boolean isInRange(final @NotNull VoiceLocation loc) {
    if (!this.enabled)
      return false;

    if (!loc.world().equalsIgnoreCase(this.location.world()))
      return false;

    return loc.distanceSquared(this.location) <= (this.radius * this.radius);
  }

  public boolean isInRange(final double x, final double y, final double z, final @NotNull String world) {
    if (!this.enabled || !world.equalsIgnoreCase(this.location.world()))
      return false;

    final var dx = x - this.location.x();
    final var dy = y - this.location.y();
    final var dz = z - this.location.z();
    return (dx * dx + dy * dy + dz * dz) <= (this.radius * this.radius);
  }

  public boolean toggle() {
    this.enabled = !this.enabled;
    return this.enabled;
  }

  public void linkSpeaker(final @NotNull String speakerName) {
    this.targetSpeakers.add(speakerName.toLowerCase());
  }

  public void unlinkSpeaker(final @NotNull String speakerName) {
    this.targetSpeakers.remove(speakerName.toLowerCase());
  }

  public boolean targetsSpeaker(final @NotNull String speakerName) {
    if (this.allSpeakers)
      return true;
    return this.targetSpeakers.contains(speakerName.toLowerCase());
  }

  public @NotNull Set<String> getTargetSpeakers() {
    return Collections.unmodifiableSet(this.targetSpeakers);
  }

}
