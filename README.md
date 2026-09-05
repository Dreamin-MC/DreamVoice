# 🎙️ DreamVoice

<p align="center">
  <strong>Spatialized Audio Framework, Acoustic Physics Engine & In-Game Comms for Minecraft Paper</strong>
  <br />
  <i>Developed by Dreamin’ Studios for the PaperMC & Simple Voice Chat ecosystem</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-orange.svg" alt="Java 25" />
  <img src="https://img.shields.io/badge/Paper-26.1.2+-blue.svg" alt="Paper 26.1.2+" />
  <img src="https://img.shields.io/badge/Simple%20Voice%20Chat-2.6.x-green.svg" alt="Simple Voice Chat" />
  <a href="https://jitpack.io/#Dreamin-MC/DreamVoice"><img src="https://jitpack.io/v/Dreamin-MC/DreamVoice.svg" alt="JitPack" /></a>
  <a href="https://modrinth.com/plugin/dreamvoice"><img src="https://img.shields.io/badge/Modrinth-1.0.9-00AF5C?logo=modrinth&logoColor=white" alt="Modrinth" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="License: GPL v3" /></a>
</p>

---

## 🌟 Overview

**DreamVoice** is a complete spatialized voice and acoustic solution built on top of **Simple Voice Chat (SVC)**. Designed for immersive gamemodes, roleplay servers, and narrative minigames (such as **Danganronpa**, Murder Mystery, and investigation games), it delivers realistic acoustic physics, 3D directional speakers, voice projections, covert wiretaps, real-time DSP voice filters, interactive cassette recordings, and independent persistent data storage surviving server reboots.

---

## ✨ Core Features

```
 🎙️ DREAMVOICE CORE ARCHITECTURE
 ├── 🧱 VoiceWall (Acoustic Engine, Material Attenuation, Open Door Bypass, Particle Raycast Debug)
 ├── 📢 3D Speakers (3D Positional audio, Mobile entity tracking, Dual voice/playback channels)
 ├── 👻 Voice Projection (Remote voice projection, Security Camera/Drone listening, Fake Players)
 ├── 🕵️ Wiretaps (Covert spy mics, Mobile entity bugs, Direct cassette recording)
 ├── 📼 Voice Recordings (Live audio capture, Physical Cassette items, MP3 & URL player)
 ├── 📻 Radios & Transmitters (Multi-user frequencies, Roger Beep, Point-to-point links)
 ├── 🎛️ DSP Filters & Soft Limiter (Voice Disguiser, Vocoder, Demon, Anti-clipping Limiter)
 └── 💾 Independent System Persistence (Automatic save & load for all modules on server restart)
```

### 1. 🧱 Acoustic Engine & VoiceWall
* **Occlusion Modes**:
  * `STRICT_BLOCK`: 100% soundproof walls (sound is fully cut off unless an open doorway or air aperture is found).
  * `REALISTIC`: Material-based dB attenuation (`wood`, `stone`, `glass`, `metal`, `wool`, etc.).
  * `OFF`: Disabled (vanilla SVC behavior).
* **Diffraction & Obstacle Bypass**: Sound realistically bends around pillars, trees, and half-walls, and travels through **open doors and windows** (`AcousticPathFinder`).
* **Air Damping**: Natural high-frequency absorption over distance.
* **Visual Particle Debug & Action Bar**: Real-time particle ray visualization (🟢 Green=Direct Air, 🟡 Yellow=Bypassed/Diffracted, 🔴 Red=Blocked) and live Action Bar diagnostics via `/voicewall debug [player]`.

### 2. 📢 Spatialized 3D Speakers
* Directional 3D audio positioned at static coordinates or dynamically attached to any **Bukkit Entity** (NPC, drone, mob, vehicle, ArmorStand).
* **Independent Dual Channels**: Speak into a speaker's microphone while background music/sound effects play simultaneously with zero interruption or audio collision.
* **Access Modes**: Configure `GLOBAL` public broadcast or `RESTRICTED` mode linked to authorized players.

### 3. 👻 Voice Projections (Camera Mode & Fake Players)
* Projects a player's voice to a remote target location or moving entity while allowing them to hear their camera's surrounding environment (`hearPlayerEnvironment`).
* Perfect for security cameras, surveillance drones, astral projections, and intercoms.

### 4. 🕵️ Covert Wiretaps & Cassettes
* Place static or entity-attached spy microphones (mobile bugs).
* Live listening stream for investigators (`/wiretap listen`).
* Direct recording to **interactive physical Cassette Items**.

### 5. 📼 Voice Recordings & Audio Cassettes
* Live session audio recording with timestamps and duration slicing (`/record slice`).
* External MP3 file and web URL streaming.
* Physical Cassette items playable on right-click in hand or via 3D speakers.

### 6. 📻 Radios & Transmitters
* **Multi-User Radio Channels**: Tune into frequencies with custom audio filters and configurable Roger Beep end-of-transmission tones.
* **Transmitters**: Direct point-to-point voice broadcast to selected players with custom maximum ranges.

### 7. 🎛️ DSP Processing & Audio Stream Isolation
* **Zero Audio Crackling**: Fully isolated audio streams indexed by composite key `(Sender:Receiver:Source)`.
* **Soft-Knee Dynamic Limiter**: Real-time DSP anti-clipping limiter preventing distortion.
* **DSP Voice Filters**: Voice anonymizer `disguise`, `robot`, `phone`, `demon`, `whisper`, and custom filter API.

### 8. 💾 Full Data Persistence
* Automatically loads all active speakers, wiretaps, projections, radios, and transmitters on startup (`onServerStarted`) and saves them on shutdown (`onDreamDisable`).
* Independent JSON files under `plugins/DreamVoice/data/` for easy backup and module reload.

---

## 📚 Detailed Documentation

Explore the comprehensive module guides in the [`docs/`](docs/) directory:

* 🧱 [**VoiceWall & Acoustic Physics Guide**](docs/voicewall.md)
* 📢 [**Spatial Speakers Guide**](docs/speakers.md)
* 👻 [**Voice Projection & Camera Mode Guide**](docs/projection.md)
* 🕵️ [**Covert Wiretaps Guide**](docs/wiretap.md)
* 📼 [**Voice Recording & Cassettes Guide**](docs/recording.md)
* 📻 [**Radios & Transmitters Guide**](docs/radio_transmitter.md)
* 🎛️ [**DSP Voice Filters Guide**](docs/filters.md)
* 🕹️ [**Complete Command Reference**](docs/commands.md)

---

## 🕹️ Quick Commands Overview

| Command | Description |
|---|---|
| `/dreamvoice status` | Displays system status and active module counts |
| `/dreamvoice reload [all\|config\|data]` | Reloads configuration, saved data, or both |
| `/voicewall mode <strict\|realistic\|off>` | Changes wall occlusion mode |
| `/voicewall debug [player]` | Toggles visual particle sound raycast diagnostics |
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
  compileOnly 'com.github.Dreamin-MC.DreamVoice:api:1.0.9'
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
    <version>1.0.9</version>
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
  compileOnly 'fr.dreamin:dreamvoice-api:1.0.9'
}
```

</details>

---

## 📄 License
This project is licensed under the **GNU General Public License v3.0** (GPLv3) - see the [LICENSE](LICENSE) file for details.
