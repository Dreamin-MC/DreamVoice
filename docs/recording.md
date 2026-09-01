# 📼 Audio Recording & Cassettes

The **Recording** module captures live player voices, streams external audio files and web URLs, and produces **Interactive Audio Cassette Items** playable on right-click or through spatial speakers.

---

## 📑 Table of Contents
- [Key Features](#-key-features)
- [Interactive Audio Cassettes](#-interactive-audio-cassettes)
- [Audio Slicing & Extraction](#-audio-slicing--extraction)
- [Java API Usage](#-java-api-usage)
- [In-Game Commands](#-in-game-commands)

---

## ✨ Key Features

* **Live Voice Recording**: Captures PCM / Opus audio streams with exact timestamps and durations.
* **Audio File & URL Streaming**: Transcodes and converts external audio files or URLs into voice recordings.
* **Physical Cassette Items**: Generates interactive `ItemStack` items with metadata, timestamps, and durations.
* **Right-Click Playback**: Plays the recorded audio directly in the listener's headset.
* **Speaker Integration**: Broadcast recordings into 3D spatialized speakers via `/speaker play record`.

---

## 💽 Interactive Audio Cassettes

An audio cassette item stores:
* Recording title and UUID identifier in persistent PDC data (`cassette_id`).
* Total audio duration and author name.
* Playable on right-click or in 3D speakers.

---

## ✂️ Audio Slicing & Extraction

* **Exact Timestamp Slicing**: Slice specific audio chunks via start timestamp and length (`/record slice`).
* **Instant Replay**: Grab the last $N$ seconds of a recorded session (`/record slice-last`).

---

## 💻 Java API Usage

```java
VoiceRecordingService recService = DreamVoice.getService(VoiceRecordingService.class);

// 1. Start an audio recording session:
VoiceRecording rec = recService.startRecording(player.getUniqueId());

// 2. Stop the session:
recService.stopRecording(rec.getUuid());

// 3. Create a physical interactive Cassette Item from a recording:
ItemStack cassette = recService.createCassette(rec);
player.getInventory().addItem(cassette);

// 4. Play a recording directly to a player:
recService.playRecordingTo(connection, rec);
```

---

## 🕹️ In-Game Commands

| Command | Permission | Description |
|---|---|---|
| `/record start` | `dreamvoice.record.start` | Starts recording your voice |
| `/record stop [id]` | `dreamvoice.record.stop` | Stops active recording (or by ID) |
| `/record list` | `dreamvoice.record.list` | Lists recorded audio sessions |
| `/record play <id> [player]` | `dreamvoice.record.play` | Plays an audio recording to a player |
| `/record cassette <id> [player]` | `dreamvoice.record.cassette` | Gives a physical Cassette item |
| `/record cassette file <file> [player]` | `dreamvoice.record.cassette` | Creates and gives a cassette from a local file |
| `/record cassette url <url> [player]` | `dreamvoice.record.cassette` | Creates and gives a cassette from a web audio URL |
| `/record slice <id> <startMs> <durMs> [give]` | `dreamvoice.record.slice` | Slices a specific segment from a recording |
| `/record slice-last <id> <durMs> [give]` | `dreamvoice.record.slice` | Extracts the last $N$ milliseconds from a recording |
| `/record delete <id>` | `dreamvoice.record.delete` | Deletes a recording |
