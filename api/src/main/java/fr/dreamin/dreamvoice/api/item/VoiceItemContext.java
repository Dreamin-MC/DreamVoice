package fr.dreamin.dreamvoice.api.item;

import fr.dreamin.dreamapi.api.DreamAPI;
import fr.dreamin.dreamapi.api.item.ItemContext;
import fr.dreamin.dreamvoice.api.filter.service.VoiceFilterService;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

public record VoiceItemContext(@NotNull ItemContext context) {

  public @NotNull Player player() {
    return this.context.player();
  }

  public @NotNull ItemStack item() {
    return this.context.item();
  }

  public @NotNull Event event() {
    return this.context.event();
  }

  public @Nullable VoiceFilterService filterService() {
    return DreamAPI.getAPI().getService(VoiceFilterService.class);
  }

  public @Nullable VoiceRadioService radioService() {
    return DreamAPI.getAPI().getService(VoiceRadioService.class);
  }

  public boolean addFilter(final @NotNull String filterId) {
    final var service = filterService();
    if (service == null)
      return false;

    service.addFilter(player().getUniqueId(), filterId);
    return true;
  }

  public boolean removeFilter(final @NotNull String filterId) {
    final var service = filterService();
    if (service == null)
      return false;

    service.removeFilter(player().getUniqueId(), filterId);
    return true;
  }

  public boolean hasFilter(final @NotNull String filterId) {
    final var service = filterService();
    if (service == null)
      return false;

    return service.getActiveFilters(player().getUniqueId()).stream()
      .anyMatch(f -> f.getId().equalsIgnoreCase(filterId));
  }

  public boolean toggleFilter(final @NotNull String filterId) {
    final var service = filterService();
    if (service == null)
      return false;

    final var uuid = player().getUniqueId();
    final var active = service.getActiveFilters(uuid).stream()
      .anyMatch(f -> f.getId().equalsIgnoreCase(filterId));

    if (active)
      service.removeFilter(uuid, filterId);
    else
      service.addFilter(uuid, filterId);

    return !active;
  }

  public boolean timedFilter(final @NotNull String filterId, final @NotNull Duration duration) {
    final var service = filterService();
    if (service == null)
      return false;

    final var uuid = player().getUniqueId();
    service.addFilter(uuid, filterId);

    Bukkit.getScheduler().runTaskLater(DreamAPI.getAPI().plugin(), () -> {
      final var s = filterService();
      if (s != null)
        s.removeFilter(uuid, filterId);
    }, Math.max(1L, duration.toMillis() / 50L));

    return true;
  }

  public boolean connectRadio(final @NotNull String frequency) {
    final var service = radioService();
    if (service == null)
      return false;

    service.joinChannel(player().getUniqueId(), frequency);
    return true;
  }

  public boolean disconnectRadio() {
    final var service = radioService();
    if (service == null)
      return false;

    service.leaveChannel(player().getUniqueId());
    return true;
  }

}
