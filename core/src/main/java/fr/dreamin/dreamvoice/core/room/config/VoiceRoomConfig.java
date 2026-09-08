package fr.dreamin.dreamvoice.core.room.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fr.dreamin.dreamvoice.api.persistence.model.ModulePersistenceConfig;
import fr.dreamin.dreamvoice.api.room.model.RoomPreset;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration file model for modules/room/config.json.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class VoiceRoomConfig {

  private boolean enabled = true;
  private ModulePersistenceConfig persistence = new ModulePersistenceConfig(true, true, 300);
  private Map<String, RoomPreset> presets = new HashMap<>();

}
