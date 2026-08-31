# 📼 Audio Recording & Cassettes

The **Recording** module captures live player voices, streams external MP3 files and web URLs, and produces **Interactive Audio Cassette Items** playable on right-click or through spatial speakers.

---

## 📑 Table of Contents
- [Key Features](#-key-features)
- [Interactive Audio Cassettes](#-interactive-audio-cassettes)
- [MP3 & Web URL Loading](#-mp3--web-url-loading)
- [Java API Usage](#-java-api-usage)
- [In-Game Commands](#-in-game-commands)

---

## ✨ Key Features

* **Live Voice Recording**: Captures PCM / Opus audio streams with exact timestamps and durations.
* **MP3 & Web URL Streaming**: Transcodes and plays external audio files into Simple Voice Chat streams.
* **Physical Cassette Items**: Generates interactive `ItemStack` items with lore, metadata, timestamps, and durations.
* **Right-Click Playback**: Plays the recorded audio directly in the listener's headset with Action Bar progress.
* **Speaker Integration**: Broadcast recordings into 3D spatialized speakers via `/speaker play`.

---

## 💽 Interactive Audio Cassettes

An audio cassette item stores:
* Recording title and ID.
* Total audio duration.
* Creation timestamp.
* Associated sound stream.

Right-clicking the cassette in the player's main hand begins direct playback with real-time Action Bar diagnostics.

---

## 💻 Java API Usage

```java
VoiceRecordingService recService = DreamVoice.getService(VoiceRecordingService.class);

// 1. Start an audio recording session:
UUID sessionId = recService.startRecording();

// 2. Stop the session:
VoiceRecording recording = recService.stopRecording(sessionId);

// 3. Create a physical interactive Cassette Item from a recording:
ItemStack cassette = recService.createCassette(recording);
player.getInventory().addItem(cassette);

// 4. Bind an external MP3 file / Web URL to an existing item:
ItemStack customItem = new ItemStack(Material.MUSIC_DISC_5);
recService.bindAudioToItem(customItem, "https://example.com/audio/clue_01.mp3", 45.0 /* duration in seconds */);

// 5. Play a recording directly to a player:
recService.playRecording(player, recording);
```

---

## 🕹️ In-Game Commands

| Command | Permission | Description |
|---|---|---|
| `/recording start` | `dreamvoice.recording.use` | Starts a voice recording session |
| `/recording stop [giveCassette]` | `dreamvoice.recording.use` | Stops recording and optionally gives a cassette item |
| `/recording play <id> [player]` | `dreamvoice.recording.use` | Plays an audio recording |
| `/recording pause <id>` | `dreamvoice.recording.use` | Pauses audio playback |
| `/recording cassette <id> <player>` | `dreamvoice.recording.use` | Gives a cassette item for an existing recording |
| `/recording list` | `dreamvoice.recording.use` | Lists recorded audio sessions |
| `/recording load <url\|file> <name>` | `dreamvoice.recording.manage` | Loads an external MP3 file or web URL |
