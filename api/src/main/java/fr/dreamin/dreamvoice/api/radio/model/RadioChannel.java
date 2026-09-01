package fr.dreamin.dreamvoice.api.radio.model;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model representing a radio frequency channel.
 * Manages connected member UUIDs, Roger Beep audio signals, and audio filter routing.
 */
@Getter
public final class RadioChannel {

  private final @NotNull String name;
  private final @NotNull Set<UUID> members = ConcurrentHashMap.newKeySet();
  @Setter
  private boolean rogerBeep = true;
  @Setter
  private @Nullable String filterId = "radio";

  public RadioChannel(final @NotNull String name) {
    this.name = name.toLowerCase();
  }

  /**
   * Adds a member player to this radio channel.
   *
   * @param playerUuid the UUID of the player
   */
  public void addMember(final @NotNull UUID playerUuid) {
    this.members.add(playerUuid);
  }

  /**
   * Removes a member player from this radio channel.
   *
   * @param playerUuid the UUID of the player
   */
  public void removeMember(final @NotNull UUID playerUuid) {
    this.members.remove(playerUuid);
  }

  /**
   * Checks whether a player is connected to this radio channel.
   *
   * @param playerUuid the UUID of the player
   * @return {@code true} if the player is a member
   */
  public boolean hasMember(final @NotNull UUID playerUuid) {
    return this.members.contains(playerUuid);
  }

  /**
   * Returns an unmodifiable view of all member UUIDs.
   *
   * @return set of member UUIDs
   */
  public @NotNull Set<UUID> getMembers() {
    return Collections.unmodifiableSet(this.members);
  }

}
