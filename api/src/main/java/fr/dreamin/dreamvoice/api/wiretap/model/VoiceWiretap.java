package fr.dreamin.dreamvoice.api.wiretap.model;

import fr.dreamin.dreamvoice.api.recording.model.VoiceRecording;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public final class VoiceWiretap {

  private final @NotNull UUID uuid;
  private final @NotNull String name;
  @Setter
  private @NotNull Location location;

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

  public VoiceWiretap(final @NotNull UUID uuid, final @NotNull String name, final @NotNull Location location) {
    this.uuid = uuid;
    this.name = name.toLowerCase();
    this.location = location;
  }

  public void addListener(final @NotNull UUID playerUuid) {
    this.listeners.add(playerUuid);
  }

  public void removeListener(final @NotNull UUID playerUuid) {
    this.listeners.remove(playerUuid);
  }

  public boolean hasListener(final @NotNull UUID playerUuid) {
    return this.listeners.contains(playerUuid);
  }

  public @NotNull Set<UUID> getListeners() {
    return Collections.unmodifiableSet(this.listeners);
  }

  public @NotNull VoiceRecording startRecording() {
    stopRecording();
    final var rec = new VoiceRecording(this.uuid);
    rec.start();
    this.activeRecording = rec;
    return rec;
  }

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

  public boolean isRecording() {
    return this.activeRecording != null && this.activeRecording.isRecording();
  }

  public @NotNull List<VoiceRecording> getRecordings() {
    return Collections.unmodifiableList(this.recordings);
  }

}
