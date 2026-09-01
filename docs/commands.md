# 🕹️ Complete Command Reference

This document provides a comprehensive reference of all in-game commands available in **DreamVoice**, including syntax, arguments, and required permissions.

---

## 📑 Table of Contents
- [`/dreamvoice` or `/dv` (Central Admin Hub & Persistence)](#-dreamvoice-or-dv)
- [`/voicewall` (Acoustics & Occlusion)](#-voicewall)
- [`/speaker` (3D Spatial Speakers)](#-speaker)
- [`/projection` (Voice Projections & Anchors)](#-projection)
- [`/wiretap` (Covert Wiretaps & Bugs)](#-wiretap)
- [`/radio` (Radios & Walkie-Talkies)](#-radio)
- [`/transmitter` (Point-to-Point Transmitters)](#-transmitter)
- [`/record` (Recordings & Audio Cassettes)](#-record)
- [`/voice` (Debug & Voice State)](#-voice)

---

## 🛡️ `/dreamvoice` (or `/dv`)
Global administration, live status overview, and modular data persistence.

| Command | Permission | Description |
|---|---|---|
| `/dv status` | `dreamvoice.admin` | Shows global status of all voice systems and active module counts |
| `/dv save [all\|speakers\|wiretaps\|projections\|radios\|transmitters]` | `dreamvoice.admin.save` | Saves persistent data modules to disk |
| `/dv load [all\|speakers\|wiretaps\|projections\|radios\|transmitters]` | `dreamvoice.admin.load` | Reloads persistent data modules from disk |
| `/dv reload [all\|config\|data\|<module>]` | `dreamvoice.admin.reload` | Reloads configuration, saved data, or both |

---

## 🧱 `/voicewall`
Manages wall voice occlusion, acoustic diffraction, and visual debugging.

| Command | Permission | Description |
|---|---|---|
| `/voicewall info` | `dreamvoice.wall.manage` | Displays VoiceWall settings and diffraction status |
| `/voicewall toggle` | `dreamvoice.wall.manage` | Enables or disables the VoiceWall system |
| `/voicewall mode <strict\|realistic\|off>` | `dreamvoice.wall.manage` | Changes wall occlusion mode (`strict`, `realistic`, `off`) |
| `/voicewall airdamping <true\|false>` | `dreamvoice.wall.manage` | Enables or disables high-frequency air damping |
| `/voicewall debug [player]` | `dreamvoice.wall.manage` | Toggles visual particle debug (Green/Yellow/Red) and Action Bar diagnostics |

---

## 📢 `/speaker`
Manages 3D locational speakers (static or mobile entity-attached) and dual-channel audio.

| Command | Permission | Description |
|---|---|---|
| `/speaker add <name> [range]` | `dreamvoice.speaker.manage` | Creates a 3D speaker at current position (alias: `create`) |
| `/speaker remove <name>` | `dreamvoice.speaker.manage` | Deletes a speaker (alias: `delete`) |
| `/speaker list` | `dreamvoice.speaker.use` | Lists all active speakers with range, mode, and position |
| `/speaker info <name>` | `dreamvoice.speaker.manage` | Displays full speaker details (attached entity, mode, allowed speakers) |
| `/speaker mode <name> <global\|restricted>` | `dreamvoice.speaker.manage` | Sets speaking access mode |
| `/speaker link <name> <player>` | `dreamvoice.speaker.manage` | Authorizes a player to speak through a restricted speaker |
| `/speaker unlink <name> <player>` | `dreamvoice.speaker.manage` | Revokes speaking authorization from a player |
| `/speaker play record <name> <recordId>` | `dreamvoice.speaker.manage` | Plays a recorded voice session through the speaker |
| `/speaker play file <name> <fileName> [loop]` | `dreamvoice.speaker.manage` | Plays a local audio file from `sounds/` |
| `/speaker play url <name> <url> [loop]` | `dreamvoice.speaker.manage` | Streams a web audio URL through the speaker |
| `/speaker stop <name>` | `dreamvoice.speaker.manage` | Stops audio playback on the speaker |
| `/speaker save` | `dreamvoice.speaker.save` | Saves all speakers to disk |
| `/speaker reload` | `dreamvoice.speaker.reload` | Reloads all speakers from disk |

---

## 👻 `/projection`
Manages voice projections, camera view listening, and fake player bodies.

| Command | Permission | Description |
|---|---|---|
| `/projection create [target]` | `dreamvoice.projection.use` | Creates a voice anchor / projection at target's position |
| `/projection remove [target]` | `dreamvoice.projection.use` | Removes the active voice anchor |
| `/projection attach [target]` | `dreamvoice.projection.use` | Attaches anchor to the nearest entity (follows movement) |
| `/projection detach [target]` | `dreamvoice.projection.use` | Detaches anchor from entity (freezes coordinates) |
| `/projection distance <target> <distance>` | `dreamvoice.projection.use` | Configures hearing/speaking range of projection |
| `/projection filter <target> <filter>` | `dreamvoice.projection.use` | Configures voice filter on projection |
| `/projection emit-anchor <target> <enabled>` | `dreamvoice.projection.use` | Toggles emitting voice at anchor location |
| `/projection emit-player <target> <enabled>` | `dreamvoice.projection.use` | Toggles emitting voice at player/camera location |
| `/projection hear-anchor <target> <enabled>` | `dreamvoice.projection.use` | Toggles hearing audio around anchor location |
| `/projection hear-player <target> <enabled>` | `dreamvoice.projection.use` | Toggles hearing audio around player/camera location |
| `/projection wall <target> <enabled>` | `dreamvoice.projection.use` | Toggles VoiceWall occlusion on projection |
| `/projection list` | `dreamvoice.projection.use` | Lists all active voice projections |
| `/projection info [target]` | `dreamvoice.projection.use` | Displays full projection configuration |
| `/projection save` | `dreamvoice.projection.save` | Saves all projections to disk |
| `/projection reload` | `dreamvoice.projection.reload` | Reloads all projections from disk |

---

## 🕵️ `/wiretap`
Manages covert listening points and direct cassette recording.

| Command | Permission | Description |
|---|---|---|
| `/wiretap add <name> [range] [filter]` | `dreamvoice.wiretap.manage` | Places a static wiretap at current position (alias: `create`) |
| `/wiretap remove <name>` | `dreamvoice.wiretap.manage` | Deletes a wiretap (alias: `delete`) |
| `/wiretap attach <name>` | `dreamvoice.wiretap.manage` | Attaches wiretap to the nearest entity (follows movement) |
| `/wiretap detach <name>` | `dreamvoice.wiretap.manage` | Detaches wiretap from entity |
| `/wiretap listen <name> [player]` | `dreamvoice.wiretap.use` | Subscribes a player to live wiretap audio |
| `/wiretap unlisten <name> [player]` | `dreamvoice.wiretap.use` | Unsubscribes a player from live wiretap audio |
| `/wiretap record start <name>` | `dreamvoice.wiretap.record` | Starts covert audio recording 🔴 |
| `/wiretap record stop <name> [giveCassette]` | `dreamvoice.wiretap.record` | Stops recording and optionally gives a physical cassette |
| `/wiretap cassette <name> [player]` | `dreamvoice.wiretap.record` | Gives the latest recording cassette to a player |
| `/wiretap list` | `dreamvoice.wiretap.use` | Lists all active wiretaps |
| `/wiretap info <name>` | `dreamvoice.wiretap.manage` | Displays wiretap status (entity, listeners, recording status) |
| `/wiretap save` | `dreamvoice.wiretap.save` | Saves all wiretaps to disk |
| `/wiretap reload` | `dreamvoice.wiretap.reload` | Reloads all wiretaps from disk |

---

## 📻 `/radio`
Manages walkie-talkies and frequency channels.

| Command | Permission | Description |
|---|---|---|
| `/radio add <channel> [filter] [rogerBeep]` | `dreamvoice.radio.manage` | Creates a radio frequency channel (alias: `create`) |
| `/radio remove <channel>` | `dreamvoice.radio.manage` | Deletes a radio channel (alias: `delete`) |
| `/radio join <channel>` | `dreamvoice.radio.use` | Tunes into a radio frequency channel |
| `/radio leave` | `dreamvoice.radio.use` | Leaves current radio frequency channel |
| `/radio kick <channel> <player>` | `dreamvoice.radio.manage` | Removes a player from a radio frequency channel |
| `/radio rogerbeep <channel> <enabled>` | `dreamvoice.radio.manage` | Toggles Roger Beep sound at transmission end |
| `/radio filter <channel> <filter>` | `dreamvoice.radio.manage` | Sets audio filter for the radio channel |
| `/radio list` | `dreamvoice.radio.use` | Lists all active radio frequencies and member counts |
| `/radio info <channel>` | `dreamvoice.radio.use` | Shows channel details (filter, Roger Beep, members) |
| `/radio save` | `dreamvoice.radio.save` | Saves all radio channels to disk |
| `/radio reload` | `dreamvoice.radio.reload` | Reloads all radio channels from disk |

---

## 📡 `/transmitter`
Manages point-to-point voice transmitters.

| Command | Permission | Description |
|---|---|---|
| `/transmitter enable` | `dreamvoice.transmitter.enable` | Enables transmitter mode |
| `/transmitter disable` | `dreamvoice.transmitter.disable` | Disables transmitter mode |
| `/transmitter add <player> [distance]` | `dreamvoice.transmitter.modify` | Adds a receiver player with optional max distance |
| `/transmitter remove <player>` | `dreamvoice.transmitter.modify` | Removes a receiver player |
| `/transmitter clear` | `dreamvoice.transmitter.modify` | Clears all receivers |
| `/transmitter list` | `dreamvoice.transmitter.list` | Lists all configured receivers and their ranges |
| `/transmitter save` | `dreamvoice.transmitter.save` | Saves all transmitters to disk |
| `/transmitter reload` | `dreamvoice.transmitter.reload` | Reloads all transmitters from disk |

---

## 📼 `/record`
Manages voice recording sessions, segment slicing, and physical audio cassettes.

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

---

## 🔊 `/voice`
Debug and testing tools.

| Command | Permission | Description |
|---|---|---|
| `/voice playsound <sound>` | `dreamvoice.debug` | Plays a test sound |
| `/voice state <state>` | `dreamvoice.debug` | Changes local voice state (`normal`, `whisper`, `radio`, etc.) |
| `/voice filter <filter>` | `dreamvoice.debug` | Applies a DSP filter to your voice |
| `/voice airdamping <enabled>` | `dreamvoice.debug` | Toggles air damping high-frequency loss |
