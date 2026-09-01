package fr.dreamin.dreamvoice.core.persistence.service;

import fr.dreamin.dreamvoice.api.persistence.service.VoicePersistenceService;
import fr.dreamin.dreamvoice.api.projection.service.VoiceProjectionService;
import fr.dreamin.dreamvoice.api.radio.service.VoiceRadioService;
import fr.dreamin.dreamvoice.api.speaker.service.VoiceSpeakerService;
import fr.dreamin.dreamvoice.api.transmitter.service.VoiceTransmitterService;
import fr.dreamin.dreamvoice.api.wiretap.service.VoiceWiretapService;
import fr.dreamin.dreamvoice.core.DreamVoice;
import fr.dreamin.dreamvoice.core.projection.storage.ProjectionsPersistence;
import fr.dreamin.dreamvoice.core.radio.storage.RadiosPersistence;
import fr.dreamin.dreamvoice.core.speaker.storage.SpeakersPersistence;
import fr.dreamin.dreamvoice.core.transmitter.storage.TransmittersPersistence;
import fr.dreamin.dreamvoice.core.wiretap.storage.WiretapsPersistence;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.UUID;

/**
 * Implementation of {@link VoicePersistenceService} coordinating modular JSON persistence across disk.
 */
public final class VoicePersistenceServiceImpl implements VoicePersistenceService {

  // ###############################################################
  // --------------------- INSTANCE FIELDS -------------------------
  // ###############################################################

  private final @NotNull DreamVoice plugin;
  private final @NotNull File dataDir;

  // ###############################################################
  // --------------------- CONSTRUCTOR METHODS ---------------------
  // ###############################################################

  public VoicePersistenceServiceImpl(final @NotNull DreamVoice plugin) {
    this.plugin = plugin;
    this.dataDir = new File(plugin.getDataFolder(), "data");
    if (!this.dataDir.exists())
      this.dataDir.mkdirs();
  }

  // ###############################################################
  // ------------------- PUBLIC SERVICE METHODS --------------------
  // ###############################################################

  @Override
  public void saveAll() {
    this.plugin.getLogger().info("Saving all DreamVoice persistent data...");
    saveSpeakers();
    saveWiretaps();
    saveProjections();
    saveRadios();
    saveTransmitters();
    this.plugin.getLogger().info("All DreamVoice data saved successfully.");
  }

  @Override
  public void loadAll() {
    this.plugin.getLogger().info("Loading all DreamVoice persistent data...");
    loadSpeakers();
    loadWiretaps();
    loadProjections();
    loadRadios();
    loadTransmitters();
    this.plugin.getLogger().info("All DreamVoice data loaded successfully.");
  }

  // ------------------------------------------------------------
  // Speakers
  // ------------------------------------------------------------

  @Override
  public void saveSpeakers() {
    final var service = DreamVoice.getService(VoiceSpeakerService.class);
    if (service != null)
      SpeakersPersistence.save(service, this.dataDir);
  }

  @Override
  public void loadSpeakers() {
    final var service = DreamVoice.getService(VoiceSpeakerService.class);
    if (service != null) {
      service.unregisterAll();
      SpeakersPersistence.load(this.dataDir);
    }
  }

  @Override
  public void saveSpeaker(final @NotNull UUID uuid) {
    saveSpeakers();
  }

  // ------------------------------------------------------------
  // Wiretaps
  // ------------------------------------------------------------

  @Override
  public void saveWiretaps() {
    final var service = DreamVoice.getService(VoiceWiretapService.class);
    if (service != null)
      WiretapsPersistence.save(service, this.dataDir);
  }

  @Override
  public void loadWiretaps() {
    final var service = DreamVoice.getService(VoiceWiretapService.class);
    if (service != null)
      WiretapsPersistence.load(service, this.dataDir);
  }

  // ------------------------------------------------------------
  // Projections
  // ------------------------------------------------------------

  @Override
  public void saveProjections() {
    final var service = DreamVoice.getService(VoiceProjectionService.class);
    if (service != null)
      ProjectionsPersistence.save(service, this.dataDir);
  }

  @Override
  public void loadProjections() {
    final var service = DreamVoice.getService(VoiceProjectionService.class);
    if (service != null) {
      service.clearProjections();
      ProjectionsPersistence.load(service, this.dataDir);
    }
  }

  // ------------------------------------------------------------
  // Radios
  // ------------------------------------------------------------

  @Override
  public void saveRadios() {
    final var service = DreamVoice.getService(VoiceRadioService.class);
    if (service != null)
      RadiosPersistence.save(service, this.dataDir);
  }

  @Override
  public void loadRadios() {
    final var service = DreamVoice.getService(VoiceRadioService.class);
    if (service != null)
      RadiosPersistence.load(service, this.dataDir);
  }

  // ------------------------------------------------------------
  // Transmitters
  // ------------------------------------------------------------

  @Override
  public void saveTransmitters() {
    final var service = DreamVoice.getService(VoiceTransmitterService.class);
    if (service != null)
      TransmittersPersistence.save(service, this.dataDir);
  }

  @Override
  public void loadTransmitters() {
    final var service = DreamVoice.getService(VoiceTransmitterService.class);
    if (service != null)
      TransmittersPersistence.load(service, this.dataDir);
  }

}
