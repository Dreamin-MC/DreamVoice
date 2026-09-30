# 🎙️ DreamVoice

<p align="center">
  <strong>Spatialized Audio Framework, Acoustic Physics Engine, Speech Recognition & Comms for Minecraft Paper</strong>
  <br />
  <i>Developed by Dreamin’ Studios for the PaperMC & Simple Voice Chat ecosystem</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-orange.svg" alt="Java 25" />
  <img src="https://img.shields.io/badge/Paper-26.1.2+-blue.svg" alt="Paper 26.1.2+" />
  <img src="https://img.shields.io/badge/Simple%20Voice%20Chat-2.6.21+-green.svg" alt="Simple Voice Chat" />
  <a href="https://jitpack.io/#Dreamin-MC/DreamVoice"><img src="https://jitpack.io/v/Dreamin-MC/DreamVoice.svg" alt="JitPack" /></a>
  <a href="https://modrinth.com/plugin/dreamvoice"><img src="https://img.shields.io/badge/Modrinth-2.0.1-00AF5C?logo=modrinth&logoColor=white" alt="Modrinth" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="License: GPL v3" /></a>
</p>

---

## 🌟 Overview

**DreamVoice** is an enterprise-grade spatialized voice, acoustics, and speech intelligence solution built on top of **Simple Voice Chat (SVC)**. Designed for immersive gamemodes, roleplay servers, and narrative minigames (such as **Danganronpa**, Murder Mystery, and investigation games), it delivers realistic acoustic physics, 3D directional speakers, voice projections, covert wiretaps, dynamic file-based DSP voice filters (`.yml`, `.json`, `.java`), real-time offline speech recognition and keyword spotting, soundproof acoustic rooms, interactive cassette recordings, and independent per-module persistent data storage surviving server reboots.

---

## ✨ Core Features

```
 🎙️ DREAMVOICE CORE ARCHITECTURE
 ├── 🧱 VoiceWall (Acoustic Engine, Material Attenuation, Open Door Bypass, Particle Raycast Debug)
 ├── 🏰 Acoustic Rooms (Soundproof booths, Acoustic zones, Dynamic reverb & Cuboid bounds)
 ├── 📢 3D Speakers (3D Positional audio, Mobile entity tracking, Dual voice/playback channels)
 ├── 👻 Voice Projection (Remote voice projection, Security Camera/Drone listening, Fake Players)
 ├── 🕵️ Wiretaps (Covert spy mics, Mobile entity bugs, Direct cassette recording)
 ├── 📼 Voice Recordings & Export (Live audio capture, Physical Cassettes, MP3/OGG/WAV export)
 ├── 📻 Radios & Transmitters (Multi-user frequencies, Roger Beep, Point-to-point links)
 ├── 🎛️ Dynamic DSP Filters (13 Built-in filters, Pipeline .yml/.json, Hot-compiled .java, Soft Limiter)
 ├── 🗣️ Speech & Keyword Spotting (Offline Vosk KWS, Grammar JSON, Whisper Sidecar, Analysis Stations)
 ├── 🎒 DreamAPI Item Handlers (VoiceItemHandler for armor, held items, right-click triggers)
 ├── 🌍 Decoupled i18n Localization (DreamAPI LangService integration with per-player locale)
 └── 💾 Modular System Persistence (Independent per-module storage under modules/*, Auto-Save)
```

### 1. 🧱 Acoustic Engine & VoiceWall
* **Occlusion Modes**:
  * `STRICT_BLOCK`: 100% soundproof walls (sound is fully cut off unless an open doorway or air aperture is found).
  * `REALISTIC`: Material-based dB attenuation (`wood`, `stone`, `glass`, `metal`, `wool`, etc.).
  * `OFF`: Disabled (vanilla SVC behavior).
* **Diffraction & Obstacle Bypass**: Sound realistically bends around pillars, trees, and half-walls, and travels through **open doors and windows** (`AcousticPathFinder`).
* **Air Damping**: Natural high-frequency absorption over distance.
* **Visual Particle Debug & Action Bar**: Real-time particle ray visualization (🟢 Green=Direct Air, 🟡 Yellow=Bypassed/Diffracted, 🔴 Red=Blocked) and live Action Bar diagnostics via `/voicewall debug [player]`.

### 2. 🏰 Acoustic Rooms & Soundproofing
* **Soundproof Zones**: Define isolation zones (`Cuboid`) where outside voices are attenuated or 100% isolated (`isolation_pct`).
* **Presets & Reverberation**: Built-in presets (`soundproof`, `studio`, `cathedral`, `bunker`) with spatial reverberation parameters (`decay`, `roomSizeMs`, `wetGain`).
* **Automatic Filter Stacking**: Automatically applies voice filters (e.g. `muffled`) when entering a zone.
* **DreamAPI Cuboid Integration**: Managed via `/voice room` commands.

### 3. 📢 Spatialized 3D Speakers
* Directional 3D audio positioned at static coordinates or dynamically attached to any **Bukkit Entity** (NPC, drone, mob, vehicle, ArmorStand).
* **Independent Dual Channels**: Speak into a speaker's microphone while background music/sound effects play simultaneously with zero interruption or audio collision.
* **Access Modes**: Configure `GLOBAL` public broadcast or `RESTRICTED` mode linked to authorized players.

### 4. 👻 Voice Projections (Camera Mode & Fake Players)
* Projects a player's voice to a remote target location or moving entity while allowing them to hear their camera's surrounding environment (`hearPlayerEnvironment`).
* Perfect for security cameras, surveillance drones, astral projections, and intercoms.

### 5. 🕵️ Covert Wiretaps & Cassettes
* Place static or entity-attached spy microphones (mobile bugs).
* Live listening stream for investigators (`/wiretap listen`).
* Direct recording to **interactive physical Cassette Items**.

### 6. 📼 Voice Recordings & Multi-Format Export
* Live session audio recording with timestamps and duration slicing (`/record slice`).
* **Audio Export**: Asynchronously export any recording to **MP3, OGG, or WAV** (`/record export <id> <mp3|ogg|wav> [name]`).
* Physical Cassette items playable on right-click in hand or via 3D speakers.

### 7. 📻 Radios & Transmitters
* **Multi-User Radio Channels**: Tune into frequencies with custom audio filters and configurable Roger Beep end-of-transmission tones.
* **Transmitters**: Direct point-to-point voice broadcast to selected players with custom maximum ranges.

### 8. 🎛️ Dynamic DSP Filters & Soft Limiter
* **Dynamic File-Based Filters**: 13 built-in filters extracted to `modules/filter/filters/`.
* **3 Supported Formats**:
  * `.yml` & `.json`: Declarative DSP pipelines (`lowpass`, `highpass`, `gain`, `overdrive`, `ring_modulator`, `delay/echo`).
  * `.java`: Real-time on-the-fly compilation via JDK `JavaCompiler` with live classloading.
* **Filter Exporter**: Export active filters into pipeline YAML/JSON or Java source files (`/voice filter export`).
* **Soft-Knee Dynamic Limiter**: Real-time DSP anti-clipping limiter preventing distortion.

### 9. 🗣️ Speech-to-Text & Realtime Keyword Spotting (KWS)
* **Realtime Keyword Spotting**: Ultra-lightweight background keyword detection using offline **Vosk** models with constrained grammar JSON arrays (`[unk]`, zero hallucinations).
* **Noise Gate & Silence Reset**: Audio RMS energy gate and automatic phonetic buffer clearance on pauses (`silence_reset_ms`).
* **Bukkit Event**: Fires `VoiceKeywordSpokenEvent` for custom roleplay triggers, spells, and voice-activated doors.
* **Interactive Transcription Stations**: Right-click on a Lectern or Jukebox with a Cassette to transcribe recordings into a formatted **Written Book** (`Material.WRITTEN_BOOK`).
* **Hybrid Engine**: Supports local embedded Vosk or external high-performance Whisper Sidecar HTTP container.
* **Model Downloader**: One-click download for small and big models (`/voice speech download <lang>`).

### 10. 🎒 DreamAPI Item Handlers & i18n
* **VoiceItemHandler**: Easily bind voice filters or radio frequencies to custom items via DreamAPI's item builder (`addFilter`, `removeFilter`, `toggleFilter`, `timedFilter`, `connectRadio`).
* **Decoupled Localization**: Fully integrated with DreamAPI `LangService`, using `plugins/DreamVoice/lang/lang-dreamvoice.json` with per-player client locale resolution.

### 11. 💾 Modular Storage & Persistence
* Clean per-module directory layout under `plugins/DreamVoice/modules/` (`wall`, `speaker`, `radio`, `record`, `wiretap`, `projection`, `transmitter`, `room`, `filter`, `speech`).
* Independent `persistence` configuration with custom `auto_save` intervals per module.
* Zero-loss hot migration from legacy 1.x layouts.

---

## 📚 Detailed Documentation

Explore the comprehensive guides on our **[Official GitHub Wiki](https://github.com/Dreamin-MC/DreamVoice/wiki)**:

* 🧱 [**VoiceWall & Acoustic Physics Guide**](https://github.com/Dreamin-MC/DreamVoice/wiki/VoiceWall-Acoustic-Engine)
* 🏰 [**Acoustic Rooms & Soundproof Zones Guide**](https://github.com/Dreamin-MC/DreamVoice/wiki/Acoustic-Rooms)
* 📢 [**3D Spatial Speakers Guide**](https://github.com/Dreamin-MC/DreamVoice/wiki/3D-Spatial-Speakers)
* 👻 [**Voice Projection & Camera Mode Guide**](https://github.com/Dreamin-MC/DreamVoice/wiki/Voice-Projections)
* 🕵️ [**Covert Wiretaps Guide**](https://github.com/Dreamin-MC/DreamVoice/wiki/Wiretaps-&-Bugs)
* 📼 [**Voice Recording & Cassettes Guide**](https://github.com/Dreamin-MC/DreamVoice/wiki/Audio-Recordings-&-Cassettes)
* 📻 [**Radios & Transmitters Guide**](https://github.com/Dreamin-MC/DreamVoice/wiki/Radios-&-Transmitters)
* 🎛️ [**DSP Voice Filters Guide**](https://github.com/Dreamin-MC/DreamVoice/wiki/DSP-Voice-Filters)
* 🗣️ [**Speech Recognition & Keyword Spotting Guide**](https://github.com/Dreamin-MC/DreamVoice/wiki/Speech-Recognition)
* 🕹️ [**Complete Command Reference**](https://github.com/Dreamin-MC/DreamVoice/wiki/Commands-&-Permissions)

---

## 🕹️ Quick Commands Overview

| Command | Description |
|---|---|
| `/dreamvoice status` | Displays system status and active module counts |
| `/dreamvoice reload [all\|config\|data]` | Reloads configuration, saved data, or both |
| `/voicewall mode <strict\|realistic\|off>` | Changes wall occlusion mode |
| `/voicewall debug [player]` | Toggles visual particle sound raycast diagnostics |
| `/voice room list` / `/voice room info <id>` | Lists and inspects acoustic rooms |
| `/voice room reload` | Reloads acoustic room presets and definitions |
| `/voice filter list` / `/voice filter reload` | Lists active voice filters and reloads files |
| `/voice filter export <filter> <yml\|json\|java>` | Exports a voice filter to YAML, JSON, or Java |
| `/voice speech download <lang>` | Downloads an official speech recognition model |
| `/voice speech models` / `/voice speech setmodel <id>` | Lists installed models and sets active model |
| `/voice speech transcribe <id> [book\|chat]` | Transcribes a voice recording into a book or chat |
| `/voice speech debug` | Toggles realtime speech & keyword debug notifications |
| `/voice speech reload` | Reloads speech config and keyword definitions |
| `/speaker add <name> [range]` | Creates a 3D locational speaker |
| `/speaker info <name>` | Shows detailed speaker info and status |
| `/speaker play <name> <record\|file\|url> <source>` | Plays audio or recording through the speaker |
| `/wiretap add <name> [range] [filter]` | Places a covert wiretap listening point |
| `/wiretap listen <name> [player]` | Connects a player to live wiretap eavesdropping |
| `/wiretap record start <name>` / `stop <name>` | Records secret audio and creates a cassette |
| `/projection create [player]` | Creates a voice projection / body anchor |
| `/projection info [player]` | Shows projection settings (distance, filter, emission/hearing) |
| `/radio join <channel>` / `/radio leave` | Tunes into or leaves a radio frequency channel |
| `/transmitter enable` / `/transmitter add <player>` | Manages point-to-point voice transmitter |
| `/record start` / `/record stop` | Records your voice and creates playable cassettes |
| `/record cassette <id> [player]` | Gives a physical Cassette item |
| `/record export <id> <mp3\|ogg\|wav> [fileName]` | Exports a recording to MP3, OGG, or WAV |

---

## 📦 Installation & Integration

### Prerequisites
* **Java 25+**
* **Paper / Purpur 26.1.2+**
* **Simple Voice Chat 2.6.x+**

### 📦 Download & Installation

1. Make sure your server runs **Paper 26.1.2+** and **[Simple Voice Chat 2.6.x+](https://modrinth.com/plugin/simple-voice-chat)**.
2. Download the latest release from **[Modrinth](https://modrinth.com/plugin/dreamvoice)** or [GitHub Releases](https://github.com/Dreamin-MC/DreamVoice/releases).
3. Drop `DreamVoice.jar` into your server's `plugins/` directory.
4. Restart your server!

---

## 💻 Developer API Dependency

### 📦 Gradle (JitPack)

```groovy
repositories {
  mavenCentral()
  maven { url = 'https://jitpack.io' }
}

dependencies {
  compileOnly 'com.github.Dreamin-MC.DreamVoice:api:2.0.1'
}
```

### 📦 Maven (JitPack)

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.github.Dreamin-MC.DreamVoice</groupId>
    <artifactId>api</artifactId>
    <version>2.0.1</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

<details>
<summary><b>Alternative: GitHub Packages</b></summary>

```groovy
repositories {
  maven {
    name = "GitHubPackages"
    url = uri("https://maven.pkg.github.com/Dreamin-MC/DreamVoice")
    credentials {
      username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") ?: ""
      password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.token") ?: ""
    }
  }
}

dependencies {
  compileOnly 'fr.dreamin:dreamvoice-api:2.0.1'
}
```

</details>

---

## 📄 License
This project is licensed under the **GNU General Public License v3.0** (GPLv3) - see the [LICENSE](LICENSE) file for details.
