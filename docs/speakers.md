# 📢 Spatial Speakers (3D Locational Audio)

The **Speakers** module allows placing 3D spatialized speakers in the world, positioned at **static coordinates** or dynamically **attached to any Bukkit Entity** (NPC, ArmorStand, drone, vehicle, mob, player).

---

## 📑 Table of Contents
- [Key Features](#-key-features)
- [Independent Dual Voice & Music Channels](#-independent-dual-voice--music-channels)
- [Dynamic Entity Attachment](#-dynamic-entity-attachment)
- [Java API Usage](#-java-api-usage)
- [In-Game Commands](#-in-game-commands)

---

## ✨ Key Features

* **3D Positional Audio**: Sound originates from the exact location of the speaker with realistic distance falloff.
* **VoiceWall Occlusion**: Audio emitted from speakers respects wall thickness and obstacle diffraction.
* **Static or Mobile**: Speakers can remain fixed at coordinates or dynamically follow moving entities.
* **Access Control**: Configure `GLOBAL` public broadcast or `RESTRICTED` mode linked to authorized players.
* **Data Persistence**: Automatically saved to `plugins/DreamVoice/data/speakers.json` across server restarts.

---

## 🎵 Independent Dual Voice & Music Channels

Every speaker maintains **two independent locational audio channels**:
1. **Voice Channel (`voiceChannel`)**: For live speech relay from one or multiple authorized players.
2. **Playback Channel (`speakerChannel`)**: For background music, sound effects, external MP3 files, or cassette playback.

> [!NOTE]
> Thanks to this dual-channel architecture, players can speak through the speaker's microphone while background music is playing without cutting the music or causing audio crackling!

---

## 🧲 Dynamic Entity Attachment

Speakers can be attached to any Minecraft entity:
* If the entity moves or teleports (e.g. a moving drone or an animated NPC), the sound origin dynamically follows its position in real-time.
* If the entity is killed or despawned, the speaker freezes at its last valid coordinates.

---

## 💻 Java API Usage

```java
VoiceSpeakerService speakerService = DreamVoice.getService(VoiceSpeakerService.class);

// 1. Create a static speaker at coordinates:
Speaker speaker = speakerService.createSpeaker("courtroom_main", location, 32.0);

// 2. Create a mobile speaker attached to an entity (e.g. a drone):
Speaker mobileSpeaker = speakerService.createSpeaker("drone_speaker", droneEntity, 24.0);

// 3. Attach or detach an existing speaker dynamically:
speakerService.attachToEntity("courtroom_main", npcEntity);
speakerService.detachFromEntity("courtroom_main");

// 4. Configure permissions & audio parameters:
speaker.addAllowedSpeaker(player.getUniqueId());
speaker.setDistance(40.0);

// 5. Save & reload:
speakerService.save();
speakerService.load();
```

---

## 🕹️ In-Game Commands

| Command | Permission | Description |
|---|---|---|
| `/speaker add <name> [range]` | `dreamvoice.speaker.manage` | Creates a 3D speaker at player position (alias: `create`) |
| `/speaker remove <name>` | `dreamvoice.speaker.manage` | Deletes a speaker (alias: `delete`) |
| `/speaker list` | `dreamvoice.speaker.use` | Lists all active speakers |
| `/speaker info <name>` | `dreamvoice.speaker.manage` | Displays speaker details (location, attached entity, mode, allowed speakers) |
| `/speaker mode <name> <global\|restricted>` | `dreamvoice.speaker.manage` | Sets speaking access mode |
| `/speaker link <name> <player>` | `dreamvoice.speaker.manage` | Authorizes a player to speak through a restricted speaker |
| `/speaker unlink <name> <player>` | `dreamvoice.speaker.manage` | Revokes speaking authorization from a player |
| `/speaker play record <name> <recordId>` | `dreamvoice.speaker.manage` | Plays an audio recording on the speaker |
| `/speaker play file <name> <fileName> [loop]` | `dreamvoice.speaker.manage` | Plays a local audio file from `sounds/` |
| `/speaker play url <name> <url> [loop]` | `dreamvoice.speaker.manage` | Streams a web audio URL through the speaker |
| `/speaker stop <name>` | `dreamvoice.speaker.manage` | Stops audio playback on the speaker |
| `/speaker save` | `dreamvoice.speaker.save` | Saves all speakers to disk |
| `/speaker reload` | `dreamvoice.speaker.reload` | Reloads all speakers from disk |
