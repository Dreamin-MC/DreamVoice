# 🕹️ Complete Command Reference

This document provides a comprehensive reference of all in-game commands available in **DreamVoice**, including syntax, arguments, and required permissions.

---

## 📑 Table of Contents
- [`/voicewall` (Acoustics & Occlusion)](#-voicewall)
- [`/speaker` (3D Spatial Speakers)](#-speaker)
- [`/projection` (Voice Projections)](#-projection)
- [`/wiretap` (Covert Wiretaps)](#-wiretap)
- [`/recording` (Recordings & Cassettes)](#-recording)
- [`/radio` (Radios & Walkie-Talkies)](#-radio)
- [`/transmitter` (Transmitters)](#-transmitter)

---

## 🧱 `/voicewall`
Manages wall voice occlusion, acoustic diffraction, and visual debugging.

| Command | Permission | Description |
|---|---|---|
| `/voicewall debug [player]` | `dreamvoice.wall.manage` | Toggles visual particle debug (Green/Yellow/Red) and Action Bar diagnostics for a player |
| `/voicewall mode <strict\|realistic\|off>` | `dreamvoice.wall.manage` | Changes wall occlusion mode (`STRICT_BLOCK`, `REALISTIC`, `OFF`) |
| `/voicewall toggle` | `dreamvoice.wall.manage` | Enables or disables the VoiceWall system |
| `/voicewall airdamping <true\|false>` | `dreamvoice.wall.manage` | Enables or disables high-frequency air damping |
| `/voicewall info` | `dreamvoice.wall.manage` | Displays VoiceWall settings and diffraction status |

---

## 📢 `/speaker`
Manages 3D locational speakers (static or mobile entity-attached).

| Command | Permission | Description |
|---|---|---|
| `/speaker create <name> [distance]` | `dreamvoice.speaker.manage` | Creates a static 3D speaker at current position |
| `/speaker attach <name>` | `dreamvoice.speaker.manage` | Attaches the speaker to the nearest entity (5 blocks) |
| `/speaker detach <name>` | `dreamvoice.speaker.manage` | Detaches the speaker (freezes at current position) |
| `/speaker delete <name>` | `dreamvoice.speaker.manage` | Deletes a speaker |
| `/speaker play <name> <recordId>` | `dreamvoice.speaker.manage` | Plays an audio recording on the speaker |
| `/speaker stop <name>` | `dreamvoice.speaker.manage` | Stops audio playback on the speaker |
| `/speaker volume <name> <gain>` | `dreamvoice.speaker.manage` | Adjusts speaker volume gain |
| `/speaker list` | `dreamvoice.speaker.use` | Lists all active speakers |
| `/speaker info <name>` | `dreamvoice.speaker.manage` | Displays full speaker details |

---

## 👻 `/projection`
Manages voice projections, camera view listening, and fake player bodies.

| Command | Permission | Description |
|---|---|---|
| `/projection create <name> [distance] [filter]` | `dreamvoice.projection.manage` | Creates a static voice projection point |
| `/projection attach <name>` | `dreamvoice.projection.manage` | Attaches projection to the nearest entity (5 blocks) |
| `/projection detach <name>` | `dreamvoice.projection.manage` | Detaches projection from entity |
| `/projection set <name> <player> [hearEnv]` | `dreamvoice.projection.manage` | Assigns a player to the voice projection |
| `/projection delete <name>` | `dreamvoice.projection.manage` | Deletes the projection point |
| `/projection list` | `dreamvoice.projection.use` | Lists all active projections |
| `/projection info <name>` | `dreamvoice.projection.manage` | Displays projection details |

---

## 🕵️ `/wiretap`
Manages covert listening points and direct cassette recording.

| Command | Permission | Description |
|---|---|---|
| `/wiretap create <name> [distance] [filter]` | `dreamvoice.wiretap.manage` | Places a static wiretap at current position |
| `/wiretap attach <name>` | `dreamvoice.wiretap.manage` | Attaches wiretap to the nearest entity (5 blocks) |
| `/wiretap detach <name>` | `dreamvoice.wiretap.manage` | Detaches wiretap (freezes at current position) |
| `/wiretap delete <name>` | `dreamvoice.wiretap.manage` | Deletes a wiretap |
| `/wiretap listen <name> [player]` | `dreamvoice.wiretap.use` | Listens to a wiretap in real-time |
| `/wiretap unlisten <name> [player]` | `dreamvoice.wiretap.use` | Stops listening to a wiretap |
| `/wiretap record start <name>` | `dreamvoice.wiretap.record` | Starts covert audio recording 🔴 |
| `/wiretap record stop <name> [giveCassette]` | `dreamvoice.wiretap.record` | Stops recording and optionally gives a cassette item |
| `/wiretap cassette <name> <player>` | `dreamvoice.wiretap.record` | Gives the latest recording cassette to a player |
| `/wiretap list` | `dreamvoice.wiretap.use` | Lists all active wiretaps |
| `/wiretap info <name>` | `dreamvoice.wiretap.manage` | Displays wiretap status (attached entity, listeners, rec) |

---

## 📼 `/recording`
Manages voice recording sessions and physical cassettes.

| Command | Permission | Description |
|---|---|---|
| `/recording start` | `dreamvoice.recording.use` | Starts a voice recording session |
| `/recording stop [giveCassette]` | `dreamvoice.recording.use` | Stops recording and optionally gives a cassette item |
| `/recording play <id> [player]` | `dreamvoice.recording.use` | Plays an audio recording |
| `/recording pause <id>` | `dreamvoice.recording.use` | Pauses audio playback |
| `/recording cassette <id> <player>` | `dreamvoice.recording.use` | Gives a physical cassette item |
| `/recording list` | `dreamvoice.recording.use` | Lists recorded audio sessions |
| `/recording load <url\|file> <name>` | `dreamvoice.recording.manage` | Loads an external MP3 file or web URL |

---

## 📻 `/radio`
Manages walkie-talkies and frequency channels.

| Command | Permission | Description |
|---|---|---|
| `/radio join <frequency>` | `dreamvoice.radio.use` | Joins a radio frequency |
| `/radio leave` | `dreamvoice.radio.use` | Leaves the current frequency |
| `/radio volume <gain>` | `dreamvoice.radio.use` | Adjusts radio volume |
| `/radio list` | `dreamvoice.radio.use` | Lists active frequencies |

---

## 📡 `/transmitter`
Manages point-to-point voice transmitters.

| Command | Permission | Description |
|---|---|---|
| `/transmitter create <name> <range>` | `dreamvoice.transmitter.manage` | Creates a transmitter |
| `/transmitter link <name> <player>` | `dreamvoice.transmitter.manage` | Links a player to the transmitter |
| `/transmitter unlink <name> <player>` | `dreamvoice.transmitter.manage` | Unlinks a player from the transmitter |
| `/transmitter list` | `dreamvoice.transmitter.use` | Lists all transmitters |
