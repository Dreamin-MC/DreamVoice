package fr.dreamin.dreamvoice.api.persistence.service;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public interface VoicePersistenceService {

  void saveAll();

  void loadAll();

  // Speakers
  void saveSpeakers();

  void loadSpeakers();

  void saveSpeaker(final @NotNull UUID uuid);

  // Wiretaps
  void saveWiretaps();

  void loadWiretaps();

  // Projections
  void saveProjections();

  void loadProjections();

  // Radios
  void saveRadios();

  void loadRadios();

  // Transmitters
  void saveTransmitters();

  void loadTransmitters();

}
