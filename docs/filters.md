# 🎛️ DSP Voice Filters

The **Voice Filters** module provides real-time digital signal processing (DSP) over raw audio samples (16-bit PCM 48kHz), modifying voice identity for players, speakers, walkie-talkies, and projections.

---

## 📑 Table of Contents
- [Built-In Filters](#-built-in-filters)
- [Creating a Custom Filter](#-creating-a-custom-filter)
- [Java API Usage](#-java-api-usage)

---

## 🎭 Built-In Filters

| Filter ID | Name | Audio Effect |
|---|---|---|
| `disguise` | **Voice Anonymizer** | Spectral cutting, amplitude modulation, and non-linear distortion that renders the speaker's voice completely unrecognizable while preserving crystal-clear intelligibility. |
| `robot` | **Robot / Vocoder** | 50Hz metallic ring-modulation. |
| `phone` | **Telephone / Radio** | Band-pass filter (300Hz - 3400Hz) with subtle white noise and analog saturation. |
| `pitch_high` | **High Pitch / Chipmunk** | Upward frequency resampler (+6 semitones). |
| `pitch_low` | **Low Pitch / Demon** | Downward frequency resampler (-6 semitones). |
| `whisper` | **Whisper** | Fundamental harmonic dampening and breath resonance boost. |
| `reverse` | **Spectral Inversion** | Reverses time blocks of PCM data. |

---

## 🛠️ Creating a Custom Filter

Create your own DSP audio filters by implementing the `VoiceFilter` interface:

```java
public final class MonokumaVoiceFilter implements VoiceFilter {

  @Override
  public @NotNull String getId() {
    return "monokuma";
  }

  @Override
  public short[] process(final short[] pcm) {
    short[] output = new short[pcm.length];
    
    // DSP processing (gain, modulation, soft distortion)
    for (int i = 0; i < pcm.length; i++) {
      double sample = pcm[i] / 32768.0;
      
      // Example: soft asymmetric saturation
      sample = Math.tanh(sample * 1.5);
      
      output[i] = (short) (sample * 32767.0);
    }
    
    return output;
  }
}
```

Register the filter with the service:
```java
VoiceFilterService filterService = DreamVoice.getService(VoiceFilterService.class);
filterService.registerFilter(new MonokumaVoiceFilter());
```

---

## 💻 Java API Usage

```java
VoiceFilterService filterService = DreamVoice.getService(VoiceFilterService.class);

// 1. Apply an active filter to a player:
filterService.addActiveFilter(player.getUniqueId(), "disguise");

// 2. Remove an active filter:
filterService.removeActiveFilter(player.getUniqueId(), "disguise");

// 3. Check if a player has active filters:
boolean isDisguised = filterService.hasActiveFilters(player.getUniqueId());
```
