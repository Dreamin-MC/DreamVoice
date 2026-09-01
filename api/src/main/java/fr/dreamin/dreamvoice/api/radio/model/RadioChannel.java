package fr.dreamin.dreamvoice.api.radio.model;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

  public void addMember(final @NotNull UUID playerUuid) {
    this.members.add(playerUuid);
  }

  public void removeMember(final @NotNull UUID playerUuid) {
    this.members.remove(playerUuid);
  }

  public boolean hasMember(final @NotNull UUID playerUuid) {
    return this.members.contains(playerUuid);
  }

  public @NotNull Set<UUID> getMembers() {
    return Collections.unmodifiableSet(this.members);
  }

}
