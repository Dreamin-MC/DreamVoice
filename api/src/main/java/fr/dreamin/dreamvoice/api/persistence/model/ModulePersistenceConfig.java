package fr.dreamin.dreamvoice.api.persistence.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ModulePersistenceConfig {

  private boolean saveOnStop = true;
  private boolean autoSave = true;
  private int autoSaveIntervalSeconds = 300;

  // ###############################################################
  // ----------------------- PUBLIC METHODS ------------------------
  // ###############################################################

  public long getAutoSaveIntervalTicks() {
    if (this.autoSaveIntervalSeconds <= 0)
      return 6000L;

    return this.autoSaveIntervalSeconds * 20L;
  }

}

