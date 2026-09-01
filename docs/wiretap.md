# 🕵️ Covert Wiretaps & Eavesdropping

The **Wiretaps** module allows deploying covert listening points (static coordinates or attached to moving entities) to eavesdrop on conversations in real-time or record them directly onto **Audio Cassettes**.

---

## 📑 Table of Contents
- [Key Features](#-key-features)
- [Mobile Entity Attachment (Bugging NPCs/Drones)](#-mobile-entity-attachment-bugging-npcsdrones)
- [Direct Cassette Recording](#-direct-cassette-recording)
- [Java API Usage](#-java-api-usage)
- [In-Game Commands](#-in-game-commands)

---

## ✨ Key Features

* **Live Eavesdropping**: Multiple investigators can subscribe to a wiretap's audio feed simultaneously (`listen` / `unlisten`).
* **Mobile Bugs**: Attach microphones to NPCs, animals, drones, vehicles, or undercover players.
* **VoiceWall Compliant**: Wiretaps pick up speech according to spatial distance and intervening walls.
* **Optional Audio Filter**: Apply custom radio or surveillance mic distortion.
* **Data Persistence**: Wiretaps are automatically saved to `plugins/DreamVoice/data/wiretaps.json`.

---

## 🧲 Mobile Entity Attachment (Bugging NPCs/Drones)

Attach a covert wiretap to any moving entity:
* The wiretap dynamically tracks the entity wherever it walks or flies, capturing all speech within its radius.
* If the entity dies or is removed, the wiretap stays frozen at its last known coordinates.

---

## 📼 Direct Cassette Recording

The wiretap system is integrated with the recording framework:
1. Start covert recording: `/wiretap record start <name>`
2. Stop recording and generate evidence: `/wiretap record stop <name> true`
3. A **Physical Audio Cassette item** is placed directly in your inventory, ready to be replayed on right-click!

---

## 💻 Java API Usage

```java
VoiceWiretapService wiretapService = DreamVoice.getService(VoiceWiretapService.class);
VoiceRecordingService recordingService = DreamVoice.getService(VoiceRecordingService.class);

// 1. Create a wiretap on a target location or entity:
VoiceWiretap wt = wiretapService.createWiretap("room_01_bug", location);
wt.setDistance(16.0);

// 2. Add an investigator as a live listener:
wt.addListener(investigatorPlayer.getUniqueId());

// 3. Start covert recording and obtain a physical evidence cassette:
wiretapService.startRecording("room_01_bug");

// ... Later ...
VoiceRecording recording = wiretapService.stopRecording("room_01_bug");
if (recording != null) {
  ItemStack cassetteItem = recordingService.createCassette(recording);
  investigatorPlayer.getInventory().addItem(cassetteItem);
}

// 4. Save & reload:
wiretapService.save();
wiretapService.load();
```

---

## 🕹️ In-Game Commands

| Command | Permission | Description |
|---|---|---|
| `/wiretap add <name> [range] [filter]` | `dreamvoice.wiretap.manage` | Places a static wiretap at player position (alias: `create`) |
| `/wiretap remove <name>` | `dreamvoice.wiretap.manage` | Deletes a wiretap (alias: `delete`) |
| `/wiretap attach <name>` | `dreamvoice.wiretap.manage` | Attaches wiretap to the nearest entity (5 blocks) |
| `/wiretap detach <name>` | `dreamvoice.wiretap.manage` | Detaches wiretap (freezes at current coordinates) |
| `/wiretap listen <name> [player]` | `dreamvoice.wiretap.use` | Subscribes a player to live wiretap audio |
| `/wiretap unlisten <name> [player]` | `dreamvoice.wiretap.use` | Unsubscribes a player from live wiretap audio |
| `/wiretap record start <name>` | `dreamvoice.wiretap.record` | Starts covert audio recording 🔴 |
| `/wiretap record stop <name> [giveCassette]` | `dreamvoice.wiretap.record` | Stops recording and optionally gives a cassette item |
| `/wiretap cassette <name> [player]` | `dreamvoice.wiretap.record` | Gives the latest recording cassette to a player |
| `/wiretap list` | `dreamvoice.wiretap.use` | Lists all active wiretaps |
| `/wiretap info <name>` | `dreamvoice.wiretap.manage` | Displays wiretap status (attached entity, listeners, recording) |
| `/wiretap save` | `dreamvoice.wiretap.save` | Saves all wiretaps to disk |
| `/wiretap reload` | `dreamvoice.wiretap.reload` | Reloads all wiretaps from disk |
