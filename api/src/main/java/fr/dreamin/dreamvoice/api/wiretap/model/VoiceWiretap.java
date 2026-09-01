package fr.dreamin.dreamvoice.api.wiretap.model;

import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model representing a covert spy microphone / wiretap listening point.
 * Can be fixed in the world or attached to moving entities with live eavesdropping and recording.
 */
@Getter
public final class VoiceWiretap {

  private final @NotNull UUID uuid;
  private final @NotNull String name;
  @Setter
  private @NotNull Location location;
  @Setter
  private @Nullable Entity targetEntity = null;

  @Setter
  private double distance = 12.0;

  @Setter
  private boolean applyVoiceWall = true;

  @Setter
  private @Nullable String filterId = null;

  private final @NotNull Set<UUID> listeners = ConcurrentHashMap.newKeySet();
  private final @NotNull List<VoiceRecording> recordings = new ArrayList<>();

  @Setter
  private @Nullable VoiceRecording activeRecording = null;

  public VoiceWiretap(final @NotNull String name, final @NotNull Location location) {
    this(UUID.randomUUID(), name, location);
  }

  public VoiceWiretap(final @NotNull String name, final @NotNull Entity targetEntity) {
    this(UUID.randomUUID(), name, targetEntity.getLocation());
    this.targetEntity = targetEntity;
  }

  public VoiceWiretap(final @NotNull UUID uuid, final @NotNull String name, final @NotNull Location location) {
    this.uuid = uuid;
    this.name = name.toLowerCase();
    this.location = location;
  }

  /**
   * Resolves the wiretap position, dynamically tracking the target entity if attached and valid.
   *
   * @return current {@link Location}
   */
  public @NotNull Location getLocation() {
    if (this.targetEntity != null && this.targetEntity.isValid())
      return this.targetEntity.getLocation();
    return this.location;
  }

  /**
   * Checks whether this wiretap is actively attached to a valid entity.
   *
   * @return {@code true} if attached
   */
  public boolean isAttachedToEntity() {
    return this.targetEntity != null && this.targetEntity.isValid();
  }

  /**
   * Adds an eavesdropping listener player to this wiretap.
   *
   * @param playerUuid the player UUID
   */
  public void addListener(final @NotNull UUID playerUuid) {
    this.listeners.add(playerUuid);
  }

  /**
   * Removes an eavesdropping listener player from this wiretap.
   *
   * @param playerUuid the player UUID
   */
  public void removeListener(final @NotNull UUID playerUuid) {
    this.listeners.remove(playerUuid);
  }

  /**
   * Checks whether a player is actively listening to this wiretap.
   *
   * @param playerUuid the player UUID
   * @return {@code true} if listening
   */
  public boolean hasListener(final @NotNull UUID playerUuid) {
    return this.listeners.contains(playerUuid);
  }

  /**
   * Returns an unmodifiable set of all active listener player UUIDs.
   *
   * @return set of listener UUIDs
   */
  public @NotNull Set<UUID> getListeners() {
    return Collections.unmodifiableSet(this.listeners);
  }

  /**
   * Starts secret audio recording on this wiretap.
   *
   * @return the started {@link VoiceRecording}
   */
  public @NotNull VoiceRecording startRecording() {
    stopRecording();
    final var rec = new VoiceRecording(this.uuid);
    rec.start();
    this.activeRecording = rec;
    return rec;
  }

  /**
   * Stops active secret recording and stores the completed recording in history.
   *
   * @return the finished {@link VoiceRecording}, or {@code null} if not recording
   */
  public @Nullable VoiceRecording stopRecording() {
    if (this.activeRecording != null) {
      this.activeRecording.stop();
      final var rec = this.activeRecording;
      this.recordings.add(rec);
      this.activeRecording = null;
      return rec;
    }
    return null;
  }

  /**
   * Checks whether this wiretap is actively recording audio.
   *
   * @return {@code true} if recording
   */
  public boolean isRecording() {
    return this.activeRecording != null && this.activeRecording.isRecording();
  }

  /**
   * Returns an unmodifiable list of all completed recordings from this wiretap.
   *
   * @return list of {@link VoiceRecording}s
   */
  public @NotNull List<VoiceRecording> getRecordings() {
    return Collections.unmodifiableList(this.recordings);
  }

}
