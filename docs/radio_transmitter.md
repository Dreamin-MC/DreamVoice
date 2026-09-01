# 📻 Radios & Transmitters

The **Radio** and **Transmitters** modules allow creating long-range frequency networks, tactical walkie-talkies, and encrypted point-to-point voice links.

---

## 📑 Table of Contents
- [Multi-User Frequency Radios](#-multi-user-frequency-radios)
- [Point-to-Point Transmitters](#-point-to-point-transmitters)
- [Java API Usage](#-java-api-usage)
- [In-Game Commands](#-in-game-commands)

---

## 📡 Multi-User Frequency Radios

* **Frequency Channels**: Players tuned to the same channel (e.g. `tactical`, `alpha`, `104.5`) communicate instantly across any distance.
* **Roger Beep**: Optional audio feedback tone triggered at the end of speech.
* **Audio Filters**: Assign voice filters (`phone`, `robot`, `disguise`) to specific frequencies.
* **Data Persistence**: Radio channels and members are saved to `plugins/DreamVoice/data/radios.json`.

---

## 🔄 Point-to-Point Transmitters

* **Direct Voice Linking**: Connect a transmitter to one or multiple specific receiver players without public frequency broadcast.
* **Range Limits**: Configure optional maximum broadcast distance per receiver or allow infinite range.
* **Data Persistence**: Transmitter lists are saved to `plugins/DreamVoice/data/transmitters.json`.

---

## 💻 Java API Usage

```java
VoiceRadioService radioService = DreamVoice.getService(VoiceRadioService.class);
VoiceTransmitterService transmitterService = DreamVoice.getService(VoiceTransmitterService.class);

// 1. Join a radio channel:
radioService.joinChannel(player.getUniqueId(), "tactical");

// 2. Configure channel Roger Beep & Filter:
RadioChannel channel = radioService.getChannel("tactical");
channel.setRogerBeep(true);
channel.setFilterId("phone");

// 3. Create a transmitter with target receivers:
transmitterService.createTransmitter(player);
transmitterService.addReceiver(player, officerPlayer, 50.0 /* max distance */);

// 4. Save & reload:
radioService.save();
transmitterService.save();
```

---

## 🕹️ In-Game Commands

### 📻 Radios (`/radio`)

| Command | Permission | Description |
|---|---|---|
| `/radio add <channel> [filter] [rogerBeep]` | `dreamvoice.radio.manage` | Creates a radio frequency channel (alias: `create`) |
| `/radio remove <channel>` | `dreamvoice.radio.manage` | Deletes a radio channel (alias: `delete`) |
| `/radio join <channel>` | `dreamvoice.radio.use` | Tunes into a radio frequency channel |
| `/radio leave` | `dreamvoice.radio.use` | Leaves current frequency channel |
| `/radio kick <channel> <player>` | `dreamvoice.radio.manage` | Expels a player from a radio channel |
| `/radio rogerbeep <channel> <enabled>` | `dreamvoice.radio.manage` | Toggles Roger Beep sound at transmission end |
| `/radio filter <channel> <filter>` | `dreamvoice.radio.manage` | Sets audio filter for the radio channel |
| `/radio list` | `dreamvoice.radio.use` | Lists active frequencies |
| `/radio info <channel>` | `dreamvoice.radio.use` | Shows channel details and connected members |
| `/radio save` | `dreamvoice.radio.save` | Saves all radio channels to disk |
| `/radio reload` | `dreamvoice.radio.reload` | Reloads all radio channels from disk |

### 📡 Transmitters (`/transmitter`)

| Command | Permission | Description |
|---|---|---|
| `/transmitter enable` | `dreamvoice.transmitter.enable` | Enables transmitter mode |
| `/transmitter disable` | `dreamvoice.transmitter.disable` | Disables transmitter mode |
| `/transmitter add <player> [distance]` | `dreamvoice.transmitter.modify` | Adds a receiver player with optional max distance |
| `/transmitter remove <player>` | `dreamvoice.transmitter.modify` | Removes a receiver |
| `/transmitter clear` | `dreamvoice.transmitter.modify` | Clears all configured receivers |
| `/transmitter list` | `dreamvoice.transmitter.list` | Lists all configured receivers and their ranges |
| `/transmitter save` | `dreamvoice.transmitter.save` | Saves all transmitters to disk |
| `/transmitter reload` | `dreamvoice.transmitter.reload` | Reloads all transmitters from disk |
