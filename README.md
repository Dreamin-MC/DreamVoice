# 🎙️ DreamVoice

<p align="center">
  <strong>Spatialized Audio Framework, Acoustic Physics Engine, Speech Recognition & Comms for Minecraft (Paper & Fabric)</strong>
  <br />
  <i>Developed by Dreamin’ Studios for the PaperMC, Fabric & Simple Voice Chat ecosystem</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-orange.svg" alt="Java 25" />
  <img src="https://img.shields.io/badge/Minecraft-26.1.2+-blue.svg" alt="Minecraft 26.1.2+" />
  <img src="https://img.shields.io/badge/Paper-26.1.2+-blue.svg" alt="Paper 26.1.2+" />
  <img src="https://img.shields.io/badge/Fabric-0.155+-purple.svg" alt="Fabric" />
  <img src="https://img.shields.io/badge/Simple%20Voice%20Chat-2.6.20+-green.svg" alt="Simple Voice Chat" />
  <a href="https://jitpack.io/#Dreamin-MC/DreamVoice"><img src="https://jitpack.io/v/Dreamin-MC/DreamVoice.svg" alt="JitPack" /></a>
  <a href="https://modrinth.com/plugin/dreamvoice"><img src="https://img.shields.io/badge/Modrinth-2.0.1-00AF5C?logo=modrinth&logoColor=white" alt="Modrinth" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="License: GPL v3" /></a>
</p>

---

## 🌟 Overview

**DreamVoice** is an enterprise-grade spatialized voice, acoustics, and speech intelligence solution built on top of **Simple Voice Chat (SVC)**. Designed for immersive gamemodes, roleplay servers, and narrative minigames (such as **Danganronpa**, Murder Mystery, and investigation games), it delivers realistic acoustic physics, 3D directional speakers, voice projections, covert wiretaps, dynamic file-based DSP voice filters (`.yml`, `.json`, `.java`), real-time offline speech recognition and keyword spotting, soundproof acoustic rooms, interactive cassette recordings, a live 3D acoustic visualizer, and independent per-module persistent data storage surviving server reboots.

Starting with **v2.0.1**, DreamVoice is fully **dual-platform**: it runs natively on **Paper / Purpur servers** (as a plugin) and **Fabric servers** (as a mod), sharing an identical high-performance core engine and unified API (`DreamVoiceAPI.get()`).

---

## ✨ Core Features

```
 🎙️ DREAMVOICE CORE ARCHITECTURE
 ├── 🚀 Dual-Platform Engine (Paper plugin & Fabric mod, shared core architecture)
 ├── 🧱 VoiceWall (Acoustic Engine, Material Attenuation, Open Door Bypass, Particle Raycasts)
 ├── 👁️ 3D Acoustic Visualizer (Real-time particle sound paths, diffraction routes & speaker bubbles)
 ├── 🏰 Acoustic Rooms (Soundproof booths, Acoustic zones, Dynamic reverb & Cuboid bounds)
 ├── 📢 3D Speakers & Broadcasts (3D Positional audio, Mobile entity tracking, Dual voice/playback channels)
 ├── 👻 Voice Projection (Remote voice projection, Security Camera/Drone listening, Fake Players)
 ├── 🕵️ Wiretaps (Covert spy mics, Mobile entity bugs, Direct cassette recording)
 ├── 📼 Voice Recordings & Export (Live audio capture, Zero-CPU Opus streaming, Physical Cassettes, MP3/OGG/WAV)
 ├── 📻 Radios & Transmitters (Multi-user frequencies, Analog bandpass, Push-to-talk clicks, Point-to-point links)
 ├── 🎛️ Dynamic DSP Filters (13 Built-in filters, Pipeline .yml/.json, Hot-compiled .java, Soft Limiter)
 ├── 🗣️ Speech & Keyword Spotting (Offline Vosk KWS, Grammar JSON, Whisper Sidecar, Analysis Stations)
 ├── 🛠️ Interactive Configuration GUI (Full visual dashboard via /voice config, Echo Shard tool)
 ├── 📡 Fabric Network Protocol (40+ packets for survival crafting mods & client companion addons)
 ├── 🎒 DreamAPI Item Handlers (VoiceItemHandler for armor, held items, right-click triggers)
 ├── 🌍 Decoupled i18n Localization (DreamAPI LangService integration with per-player locale)
 └── 💾 Modular System Persistence (Independent per-module storage under modules/*, Auto-Save)
```

### 1. 🧱 Acoustic Engine & VoiceWall
* **Occlusion Modes**:
  * `STRICT_BLOCK`: 100% soundproof walls (sound is fully cut off unless an open doorway or air aperture is found).
  * `REALISTIC`: Material-based dB attenuation (`wood`, `stone`, `glass`, `metal`, `wool`, etc.).
  * `OFF`: Disabled (vanilla SVC behavior).
* **Diffraction & Obstacle Bypass**: Sound realistically bends around obstacles, through **open doors, windows, and around corners** (`AcousticPathFinder`).
* **Air Damping**: Natural high-frequency absorption over distance.
* **Visual Particle Debug & Action Bar**: Real-time particle ray visualization (🟢 Green=Direct Air, 🟡 Yellow=Bypassed/Diffracted, 🔴 Red=Blocked) and live Action Bar diagnostics via `/voicewall debug [player]`.

### 2. 👁️ Real-Time 3D Acoustic Visualizer (`/voice visualizer`)
* **Live 3D Raycasting**: Renders dynamic particle streams in real-time showing line-of-sight sound paths, wall collision impacts, aperture diffraction routes, and speaker audible coverage spheres.
* **Performance-Conscious Hand-Held Mode**: To protect client FPS and eliminate visual clutter, particles render **only** when the inspecting player holds the visualizer tool (Compass) in their main hand.

### 3. 🏰 Acoustic Rooms & Soundproofing
* **Soundproof Zones**: Define isolation zones (`Cuboid`) where outside voices are attenuated or 100% isolated (`isolationPct`).
* **Presets & Reverberation**: Built-in presets (`soundproof`, `studio`, `cathedral`, `bunker`) with spatial reverberation parameters (`decay`, `roomSizeMs`, `wetGain`).
* **Automatic Filter Stacking**: Automatically applies voice filters (e.g. `muffled`) when entering a zone.

### 4. 📢 Spatialized 3D Speakers & Physical Broadcasts
* Directional 3D audio positioned at static coordinates or dynamically attached to any **Entity** (NPC, drone, mob, vehicle, ArmorStand).
* **Independent Dual Channels**: Speak into a speaker's microphone while background music/sound effects play simultaneously with zero interruption or audio collision.
* **Access Modes**: Configure `GLOBAL` public broadcast or `RESTRICTED` mode linked to authorized players.
* **Podium & Intercom Broadcasts**: Broadcast points route voices within a capture radius to target speakers or across the whole server (`/voice broadcast`).

### 5. 👻 Voice Projections (Camera Mode & Fake Players)
* Projects a player's voice to a remote target location or moving entity while allowing them to hear their camera's surrounding environment (`hearPlayerEnvironment`).
* Perfect for security cameras, surveillance drones, astral projections, and intercoms.

### 6. 🕵️ Covert Wiretaps & Surveillance
* Place static or entity-attached spy microphones (mobile bugs).
* Live listening stream for investigators (`/wiretap listen <name>`).
* Direct recording to **interactive physical Cassette Items**.
* **Cross-Module Propagation**: Wiretaps realistically intercept voices emitted through voice projections.

### 7. 📼 Voice Recordings & Multi-Format Export
* Live session audio recording with timestamps and duration slicing (`/record slice`).
* **Zero-CPU Opus Streaming**: `OpusAudioPlayer` streams pre-encoded frames directly to clients without CPU transcoding overhead.
* **Audio Export**: Asynchronously export any recording to **MP3, OGG, or WAV** (`/record export <id> <mp3|ogg|wav> [name]`).
* Physical Cassette items playable on right-click in hand or via 3D speakers.

### 8. 📻 Radios & Transmitters
* **Multi-User Radio Channels**: Tune into frequencies with analog bandpass filters (300 Hz - 3400 Hz) and authentic push-to-talk mic clicks.
* **Transmitters**: Direct point-to-point voice broadcast to selected players with custom maximum ranges.

### 9. 🎛️ Dynamic DSP Filters & Soft Limiter
* **Dynamic File-Based Filters**: 13 built-in filters extracted to `modules/filter/filters/`.
* **3 Supported Formats**:
  * `.yml` & `.json`: Declarative DSP pipelines (`lowpass`, `highpass`, `gain`, `overdrive`, `ring_modulator`, `delay/echo`).
  * `.java`: Real-time on-the-fly compilation via JDK `JavaCompiler` with live classloading.
* **Filter Exporter**: Export active filters into pipeline YAML/JSON or Java source files (`/voice filter export`).
* **Soft-Knee Dynamic Limiter**: Real-time DSP anti-clipping limiter preventing distortion.

### 10. 🗣️ Speech-to-Text & Realtime Keyword Spotting (KWS)
* **Realtime Keyword Spotting**: Ultra-lightweight background keyword detection using offline **Vosk** models with constrained grammar JSON arrays (`[unk]`, zero hallucinations).
* **Noise Gate & Silence Reset**: Audio RMS energy gate and automatic phonetic buffer clearance on pauses (`silenceResetMs`).
* **Multiplatform Events**: Fires `VoiceKeywordSpokenEvent` for custom roleplay triggers, spells, and voice-activated doors.
* **Interactive Transcription Stations**: Right-click on a Lectern or Jukebox with a Cassette to transcribe recordings into a formatted **Written Book**.
* **Hybrid Engine**: Supports local embedded Vosk or external high-performance Whisper Sidecar HTTP container.

### 11. 🛠️ Interactive Configuration GUI (`/voice config`)
* Open a full visual GUI dashboard to configure and monitor all modules without touching config files.
* Interactive tool (Echo Shard) to target entities, blocks, or positions in real time.

### 12. 📡 Fabric Network Packets & Inter-Mod Ecosystem
* 40+ custom packets (`@DreamPacket`) allowing third-party survival, tech, and companion mods to create and control speakers, wiretaps, radios, cassettes, and filters without hard compile dependencies.

### 13. 💾 Modular Storage & Persistence
* Clean per-module directory layout (`wall`, `speaker`, `radio`, `record`, `wiretap`, `projection`, `transmitter`, `room`, `filter`, `speech`).
  * **Paper**: `plugins/DreamVoice/`
  * **Fabric**: `config/dreamvoice/`
* Independent `persistence` configuration with custom `autoSaveIntervalSeconds` per module.

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
* 🗣️ [**Speech Recognition & Keyword Spotting Guide**](https://github.com/Dreamin-MC/DreamVoice/wiki/Speech-to-Text-&-Keyword-Spotting)
* 📡 [**Network Packets & Protocols Guide**](https://github.com/Dreamin-MC/DreamVoice/wiki/Network-Packets-&-Protocols)
* 💻 [**Developer API Guide**](https://github.com/Dreamin-MC/DreamVoice/wiki/Developer-API)
* ⚡ [**Events & Hooks Reference**](https://github.com/Dreamin-MC/DreamVoice/wiki/Events-&-Hooks)
* 🕹️ [**Complete Command Reference**](https://github.com/Dreamin-MC/DreamVoice/wiki/Commands-&-Permissions)

---

## 🕹️ Quick Commands Overview

| Command | Description |
|---|---|
| `/dv status` | Displays system status and active module counts |
| `/voice config` | Opens the interactive visual configuration GUI |
| `/voice visualizer [toggle\|on\|off]` | Toggles 3D acoustic visualizer (hand-held tool required) |
| `/dv reload [all\|config\|data]` | Reloads configuration, saved data, or both |
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
| `/speaker create <name> [dist] [mode]` | Creates a 3D locational speaker |
| `/speaker play <name> <source>` | Plays audio or recording through the speaker |
| `/speaker link <name> [player]` | Links player microphone to broadcast through speaker |
| `/wiretap create <name> [dist]` | Places a covert wiretap listening point |
| `/wiretap listen <name>` | Connects a player to live wiretap eavesdropping |
| `/wiretap record <name>` | Records secret audio and creates a cassette |
| `/projection create <name> [target]` | Creates a voice projection / body anchor |
| `/radio join <channel>` / `/radio leave` | Tunes into or leaves a radio frequency channel |
| `/transmitter toggle` / `/transmitter add <player>` | Manages point-to-point voice transmitter |
| `/record start <name>` / `/record stop <name>` | Records voice and saves `.dv` binary capture |
| `/record cassette <name>` | Gives a physical, playable Cassette item |
| `/record export <id> <mp3\|ogg\|wav> [file]` | Exports a recording to MP3, OGG, or WAV |

---

## 📦 Installation

### For Paper / Purpur Servers:
1. Make sure your server runs **Paper** or **Purpur** (version 26.1.2+ with **Java 25+**).
2. Install **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)** (2.6.20+).
3. Drop `DreamVoice-paper-2.0.1.jar` into your server's `plugins/` directory.
4. Restart your server.

### For Fabric Servers:
1. Make sure your server runs **Fabric Loader 0.19.3+** for Minecraft 26.1.2+ with **Java 25+**.
2. Install **Fabric API** and **[Simple Voice Chat Fabric](https://modrinth.com/plugin/simple-voice-chat)**.
3. Drop `DreamVoice-fabric-2.0.1.jar` into your server's `mods/` directory.
4. Restart your server.

---

## 💻 Developer API Dependency

DreamVoice provides a unified entrypoint via `DreamVoiceAPI.get()` across both Paper and Fabric.

### 📦 Gradle Kotlin DSL (`build.gradle.kts`)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.github.Dreamin-MC.DreamVoice:api:2.0.1")
}
```

### 📦 Gradle Groovy (`build.gradle`)

```groovy
repositories {
    mavenCentral()
    maven { url = "https://jitpack.io" }
    maven { url = "https://repo.papermc.io/repository/maven-public/" }
}

dependencies {
    compileOnly "com.github.Dreamin-MC.DreamVoice:api:2.0.1"
}
```

### 📦 Fabric Mod Setup (Fabric Loom)

```groovy
repositories {
    mavenCentral()
    maven { url = "https://jitpack.io" }
}

dependencies {
    modCompileOnly "com.github.Dreamin-MC.DreamVoice:api:2.0.1"
}
```

### 📦 Maven (`pom.xml`)

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
    <repository>
        <id>papermc</id>
        <url>https://repo.papermc.io/repository/maven-public/</url>
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
    compileOnly "fr.dreamin:dreamvoice-api:2.0.1"
}
```

</details>

---

## 📄 License
This project is licensed under the **GNU General Public License v3.0** (GPLv3) - see the [LICENSE](LICENSE) file for details.
