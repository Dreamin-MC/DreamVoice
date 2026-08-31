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

* **Frequency Channels**: Players tuned to the same frequency (e.g. `104.5 MHz`) communicate instantly across any distance.
* **DSP Radio Effect**: Built-in walkie-talkie band-pass filter with analog clicks and white noise.
* **Per-Frequency Volume**: Independent volume gain control.

---

## 🔄 Point-to-Point Transmitters

* **Direct Voice Linking**: Connect a transmitter to one or multiple specific receiver players without public frequency broadcast.

---

## 💻 Java API Usage

```java
VoiceRadioService radioService = DreamVoice.getService(VoiceRadioService.class);

// 1. Join a radio frequency:
radioService.joinFrequency(player, "104.5");

// 2. Leave the frequency:
radioService.leaveFrequency(player);

// 3. Adjust listening volume:
radioService.setVolume(player, "104.5", 0.8f);
```

---

## 🕹️ In-Game Commands

### 📻 Radios (`/radio`)

| Command | Permission | Description |
|---|---|---|
| `/radio join <frequency>` | `dreamvoice.radio.use` | Joins a radio frequency |
| `/radio leave` | `dreamvoice.radio.use` | Leaves the current frequency |
| `/radio volume <gain>` | `dreamvoice.radio.use` | Adjusts radio volume |
| `/radio list` | `dreamvoice.radio.use` | Lists active frequencies |

### 📡 Transmitters (`/transmitter`)

| Command | Permission | Description |
|---|---|---|
| `/transmitter create <name> <range>` | `dreamvoice.transmitter.manage` | Creates a transmitter |
| `/transmitter link <name> <player>` | `dreamvoice.transmitter.manage` | Links a player to the transmitter |
| `/transmitter unlink <name> <player>` | `dreamvoice.transmitter.manage` | Unlinks a player from the transmitter |
| `/transmitter list` | `dreamvoice.transmitter.use` | Lists all transmitters |
