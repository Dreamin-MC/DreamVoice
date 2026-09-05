# 🎙️ DreamVoice

**The ultimate spatial audio & acoustic expansion for Simple Voice Chat on Minecraft Paper!**

Bring real sound physics, 3D directional speakers, covert spy microphones, walkie-talkie radios, voice disguisers, and playable audio cassette tapes directly into your Minecraft server.

---

## 🌟 What is DreamVoice?

**DreamVoice** transforms proximity voice chat into a truly immersive audio experience. Designed for **Roleplay servers, Survival SMPs, Investigation games, and Custom Minigames**, it bridges the gap between Minecraft acoustics and real-world audio propagation.

Sound no longer magically passes through thick walls—it bends around open doorways, muffles through solid materials, and interacts realistically with the environment.

---

## ✨ Features

### 🧱 1. Realistic Sound Physics & Soundproof Walls (VoiceWall)
* **Smart Sound Occlusion**: Sound realistically stops or muffles behind solid stone, wood, glass, and metal blocks.
* **Acoustic Pathfinding**: Sound naturally travels through **open doors, windows, and around corners**.
* **Visual Debugging**: Check raycast sound propagation in real-time with visual particles via `/voicewall debug`.

### 📢 2. 3D Locational Speakers
* Place directional 3D speakers anywhere in your world.
* **Attach to Entities**: Mount speakers to moving vehicles, drones, ArmorStands, or NPCs!
* **Dual Channels**: Speak through a microphone while playing background music/sound effects without any sound collision.

### 👻 3. Voice Projections & Security Cameras
* Project your voice remotely to any target block or moving entity.
* Hear what the camera or remote point hears—ideal for **CCTV security cameras, intercoms, surveillance drones**, or magical projections.

### 🕵️ 4. Covert Wiretaps & Spy Bugs
* Plant hidden microphones at secret coordinates or plant a bug directly on an NPC or player.
* Listen in real-time to secret conversations or record them directly to an in-game cassette tape!

### 📼 5. Physical Audio Cassettes
* Record live voice conversations or stream audio from external MP3 links.
* Generates a **physical Cassette item** that players can hold and play with a right-click or load into 3D speakers.

### 📻 6. Walkie-Talkies & Radio Channels
* Connect players to specific radio frequencies for team tactical comms, police dispatch, or survival walkie-talkies.

### 🎛️ 7. Real-Time DSP Voice Filters
* Transform player voices on the fly with built-in DSP effects:
  * 🎭 **Disguise** / Voice Anonymizer
  * 🤖 **Robot** / Cybernetic
  * 😈 **Demon** / Deep monster
  * 📞 **Phone** / Low-bandwidth radio
  * 🤫 **Whisper**
* Built-in **Soft-Knee Dynamic Limiter** prevents microphone clipping and distortion.

---

## 🕹️ Simple Commands

| Command | Description |
|---|---|
| `/voicewall mode <strict|realistic|off>` | Change soundproof wall physics mode |
| `/voicewall debug` | Toggle visual sound path particle diagnostics |
| `/speaker create <name> [range]` | Create a 3D speaker at your position |
| `/speaker attach <name>` | Attach a speaker to the entity you are looking at |
| `/projection create <name>` | Create a voice projection point |
| `/wiretap create <name>` | Place a hidden spy microphone |
| `/wiretap record start <name>` | Start recording audio on a spy bug |
| `/wiretap record stop <name> true` | Stop recording and receive a playable Cassette item |
| `/radio join <frequency>` | Tune into a walkie-talkie radio channel |

---

## 📦 Installation

1. Make sure your server runs **Paper** or **Purpur** (26.1.2+ with **Java 25+**).
2. Install **[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat)** (2.6.21+).
3. Drop `DreamVoice.jar` and `voicechat-bukkit-<version>.jar` into your server's `plugins/` directory.
4. Restart your server and enjoy!

---

## 💻 Developer API

DreamVoice includes a public API to build custom plugins, custom voice filters, and audio integrations.

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

## 📄 License & Links

* **Source Code**: [GitHub - Dreamin-MC/DreamVoice](https://github.com/Dreamin-MC/DreamVoice)
* **JavaDoc API**: [GitHub Pages - DreamVoice API](https://dreamin-mc.github.io/DreamVoice/)
* **License**: [GNU General Public License v3.0](https://github.com/Dreamin-MC/DreamVoice/blob/main/LICENSE)
