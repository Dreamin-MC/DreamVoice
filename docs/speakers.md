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
* **Access Control**: Configure which players are authorized to broadcast their voice through the speaker.
* **Custom Gain & Range**: Adjustable volume gain and broadcast radius.

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
speaker.setVolume(1.2f);
```

---

## 🕹️ In-Game Commands

| Command | Permission | Description |
|---|---|---|
| `/speaker create <name> [distance]` | `dreamvoice.speaker.manage` | Creates a 3D speaker at player position |
| `/speaker attach <name>` | `dreamvoice.speaker.manage` | Attaches speaker to the nearest entity (5 blocks) |
| `/speaker detach <name>` | `dreamvoice.speaker.manage` | Detaches speaker (freezes at current position) |
| `/speaker delete <name>` | `dreamvoice.speaker.manage` | Deletes a speaker |
| `/speaker list` | `dreamvoice.speaker.use` | Lists all active speakers |
| `/speaker info <name>` | `dreamvoice.speaker.manage` | Displays speaker details (location, attached entity, volume, range) |
| `/speaker play <name> <recordId>` | `dreamvoice.speaker.manage` | Plays an audio recording on the speaker |
| `/speaker stop <name>` | `dreamvoice.speaker.manage` | Stops audio playback on the speaker |
| `/speaker volume <name> <gain>` | `dreamvoice.speaker.manage` | Adjusts speaker volume gain |
