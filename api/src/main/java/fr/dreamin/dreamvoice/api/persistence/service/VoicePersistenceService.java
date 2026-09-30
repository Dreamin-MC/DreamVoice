package fr.dreamin.dreamvoice.api.persistence.service;

import fr.dreamin.dreamvoice.api.persistence.model.ModulePersistenceConfig;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Service managing independent modular JSON persistence for speakers, wiretaps,
 * projections, radio frequencies, and transmitters across server restarts.
 */
public interface VoicePersistenceService {

  /**
   * Saves all active voice modules to their respective JSON files on disk.
   */
  void saveAll();

  /**
   * Loads all saved voice modules from disk.
   */
  void loadAll();

  /**
   * Returns the persistence configuration of a specific module.
   *
   * @param moduleName name of the module (e.g. speaker, radio)
   * @return persistence config of the module
   */
  @NotNull ModulePersistenceConfig getModuleConfig(final @NotNull String moduleName);

  /**
   * Reloads all module persistence configurations from disk.
   */
  void reloadModuleConfigs();

  /**
   * Starts or restarts all scheduled auto-save tasks.
   */
  void startAutoSaveTasks();

  /**
   * Cancels all scheduled auto-save tasks.
   */
  void cancelAutoSaveTasks();

  /**
   * Saves all 3D locational speakers to disk.
   */
  void saveSpeakers();

  /**
   * Loads all 3D locational speakers from disk.
   */
  void loadSpeakers();

  /**
   * Saves a single speaker to disk by its UUID.
   *
   * @param uuid the unique ID of the speaker
   */
  void saveSpeaker(final @NotNull UUID uuid);

  /**
   * Saves all active wiretaps to disk.
   */
  void saveWiretaps();

  /**
   * Loads all active wiretaps from disk.
   */
  void loadWiretaps();

  /**
   * Saves all active voice projections to disk.
   */
  void saveProjections();

  /**
   * Loads all active voice projections from disk.
   */
  void loadProjections();

  /**
   * Saves all active radio channels to disk.
   */
  void saveRadios();

  /**
   * Loads all active radio channels from disk.
   */
  void loadRadios();

  /**
   * Saves all active point-to-point transmitters to disk.
   */
  void saveTransmitters();

  /**
   * Loads all active point-to-point transmitters from disk.
   */
  void loadTransmitters();

  /**
   * Saves all acoustic rooms to disk.
   */
  void saveRooms();

  /**
   * Loads all acoustic rooms from disk.
   */
  void loadRooms();

  /**
   * Saves all broadcast points to disk.
   */
  void saveBroadcasts();

  /**
   * Loads all broadcast points from disk.
   */
  void loadBroadcasts();

}
