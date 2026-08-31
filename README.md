# 🎙️ DreamVoice

<p align="center">
  <strong>Spatialized Audio Framework & Advanced Acoustic Engine for Minecraft Paper</strong>
  <br />
  <i>Developed by Dreamin’ Studios for the PaperMC & Simple Voice Chat ecosystem</i>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-25-orange.svg" alt="Java 25" />
  <img src="https://img.shields.io/badge/Paper-1.21.4-blue.svg" alt="Paper 1.21.4" />
  <img src="https://img.shields.io/badge/Simple%20Voice%20Chat-2.6.x-green.svg" alt="Simple Voice Chat" />
  <img src="https://img.shields.io/badge/License-Proprietary-red.svg" alt="License" />
</p>


---

## 🌟 Overview

**DreamVoice** is a complete spatialized voice and acoustic solution built on top of **Simple Voice Chat (SVC)**. Designed for immersive gamemodes, roleplay servers, and narrative minigames (such as **Danganronpa**, Murder Mystery, investigation games), it delivers realistic acoustic physics, 3D directional speakers, voice projections, covert wiretaps, real-time DSP voice filters, and interactive cassette recordings.

---

## ✨ Core Features

```
 🎙️ DREAMVOICE CORE ARCHITECTURE
 ├── 🧱 VoiceWall (Acoustic Engine, Diffraction, Open Door Bypass, Particle Debug)
 ├── 📢 Speakers (3D Locational speakers, Mobile entity tracking, Dual voice/music channels)
 ├── 👻 Voice Projection (Voice projection, Camera/Drone listening, Fake Players)
 ├── 🕵️ Wiretaps (Covert spy mics, Mobile entity bugs, Direct cassette recording)
 ├── 📼 Voice Recording (Live audio capture, Physical Cassette items, MP3/URL player)
 ├── 📻 Radios & Transmitters (Multi-user frequencies, Walkie-talkies, Direct links)
 └── 🎛️ DSP Filters & Soft Limiter (Voice Disguiser, Vocoder, Demon, Anti-clipping)
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

### 3. 👻 Voice Projections (Camera Mode & Fake Players)
* Projects a player's voice to a remote target location or moving entity while allowing them to hear their camera's surrounding environment (`hearPlayerEnvironment`).
* Perfect for security cameras, surveillance drones, astral projections, and intercoms.

### 4. 🕵️ Covert Wiretaps & Cassettes
* Place static or entity-attached spy microphones (mobile bugs).
* Live listening stream for investigators (`listen` / `unlisten`).
* Direct recording to **interactive physical Cassette Items**.

### 5. 📼 Voice Recordings & Audio Cassettes
* Live session audio recording.
* External MP3 file and web URL streaming.
* Physical Cassette items playable on right-click in hand or via 3D speakers.

### 6. 🎛️ DSP Processing & Audio Stream Isolation
* **Zero Audio Crackling**: Fully isolated audio streams indexed by composite key `(Sender:Receiver:Source)`.
* **Soft-Knee Dynamic Limiter**: Real-time DSP anti-clipping limiter preventing distortion.
* **DSP Voice Filters**: Voice anonymizer `disguise`, `robot`, `phone`, `demon`, `whisper`, and custom filter API.

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

## 🚀 Installation & Integration

### Prerequisites
* **Java 25+**
* **Paper / Purpur 1.21.4+**
* **Simple Voice Chat 2.6.x+**

### Gradle Dependency (GitHub Packages)

Add the GitHub Packages repository and credentials to your `build.gradle` / `settings.gradle`:

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
    compileOnly 'fr.dreamin:dreamvoice-api:1.0.0'
}
```

> [!TIP]
> In your `~/.gradle/gradle.properties` (or project root), define `gpr.user=YourGitHubUsername` and `gpr.token=YourPersonalAccessToken` with `read:packages` permission.


---

## 💻 Java API Example

```java
// Retrieve services
VoiceWallService wallService = DreamVoice.getService(VoiceWallService.class);
VoiceSpeakerService speakerService = DreamVoice.getService(VoiceSpeakerService.class);
VoiceWiretapService wiretapService = DreamVoice.getService(VoiceWiretapService.class);
VoiceRecordingService recService = DreamVoice.getService(VoiceRecordingService.class);

// 1. Configure VoiceWall in soundproof mode
wallService.setMode(VoiceWallMode.STRICT_BLOCK);

// 2. Create a mobile speaker attached to a drone entity
Speaker droneSpeaker = speakerService.createSpeaker("drone_01", droneEntity, 24.0);

// 3. Attach a covert wiretap bug to an NPC
VoiceWiretap spyMic = wiretapService.createWiretap("spy_npc", npcEntity);
spyMic.addListener(inspectorPlayer.getUniqueId());

// 4. Record secretly and generate an interactive cassette
wiretapService.startRecording("spy_npc");
VoiceRecording rec = wiretapService.stopRecording("spy_npc");
ItemStack cassette = recService.createCassette(rec);
inspectorPlayer.getInventory().addItem(cassette);
```

---

## 🕹️ Quick Commands Overview

| Command | Description |
|---|---|
| `/voicewall debug [player]` | Toggles visual particle debug & Action Bar diagnostics |
| `/voicewall mode <strict\|realistic\|off>` | Changes wall occlusion mode |
| `/speaker create <name> [distance]` | Creates a 3D speaker |
| `/speaker attach <name>` | Attaches the speaker to the nearest entity |
| `/projection create <name>` | Creates a voice projection point |
| `/projection attach <name>` | Attaches the projection to an entity |
| `/wiretap create <name>` | Places a covert wiretap |
| `/wiretap record start <name>` | Starts covert recording |
| `/wiretap record stop <name> true` | Stops recording and gives a physical cassette |
| `/recording start` / `/recording stop` | Manages voice recording sessions |
| `/radio join <frequency>` | Joins a walkie-talkie frequency |

---

## 📄 License
This project is **Proprietary & Confidential** - All rights reserved by **Dreamin’ Studios**.
Unauthorized copying, modification, or distribution is strictly prohibited. See [LICENSE](LICENSE) for details.

