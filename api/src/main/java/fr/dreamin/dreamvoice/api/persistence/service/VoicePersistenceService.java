package fr.dreamin.dreamvoice.api.persistence.service;

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

}
