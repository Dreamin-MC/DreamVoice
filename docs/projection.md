# 👻 Voice Projection & Camera / Fake Player Mode

The **Projection** module allows projecting a player's voice output to a remote target location (static or attached to an entity), while managing bidirectional listening for their camera environment (ideal for **security cameras, drones, astral projections, fake players, and remote intercoms**).

---

## 📑 Table of Contents
- [Concept & Architecture](#-concept--architecture)
- [Camera & Fake Player Mode](#-camera--fake-player-mode)
- [Dynamic Entity Attachment](#-dynamic-entity-attachment)
- [Integrated Voice Disguise Filter](#-integrated-voice-disguise-filter)
- [Java API Usage](#-java-api-usage)
- [In-Game Commands](#-in-game-commands)

---

## 🎯 Concept & Architecture

When a voice projection is active for a player:
1. **Voice Output**: Their spoken voice can emit at the remote anchor location, at the player body/camera, or both.
2. **Audio Input**:
   * `hearAnchorEnvironment`: The player hears audio surrounding the remote anchor point.
   * `hearPlayerEnvironment`: The player hears audio surrounding their camera or physical position.

---

## 🎥 Camera & Fake Player Mode

In roleplay and mystery scenarios (e.g. **Danganronpa**, detective minigames with surveillance cameras or drones):
* The player controls a camera view or a fake player body in another room.
* Their voice appears to originate from the remote room or camera speaker.
* The projection system handles directional audio packets while adhering to `VoiceWall` physics.

---

## 🧲 Dynamic Entity Attachment

Projections can be bound to any Bukkit entity (e.g. a flying drone, an animal, a villager, or an ArmorStand):
* When the entity moves or is scripted, the projected voice follows the entity seamlessly in real-time.

---

## 🎭 Integrated Voice Disguise Filter

Each projection can be assigned a specific DSP filter (e.g. `disguise`, `robot`, `phone`, `demon`) to automatically transform the speaker's voice identity.

---

## 💻 Java API Usage

```java
VoiceProjectionService projService = DreamVoice.getService(VoiceProjectionService.class);

// 1. Create a projection for a target player:
VoiceProjection proj = projService.createProjection(player, targetLocation);

// 2. Configure audio properties:
proj.setFilterId("disguise");          // Anonymizes the player's voice
proj.setHearPlayerEnvironment(true);   // Hears voices around the camera
proj.setHearAnchorEnvironment(true);   // Hears voices around the anchor
proj.setDistance(24.0);

// 3. Save & reload:
projService.save();
projService.load();
```

---

## 🕹️ In-Game Commands

| Command | Permission | Description |
|---|---|---|
| `/projection create [target]` | `dreamvoice.projection.use` | Creates a voice anchor at target player's position |
| `/projection remove [target]` | `dreamvoice.projection.use` | Removes the active voice anchor |
| `/projection attach [target]` | `dreamvoice.projection.use` | Attaches anchor to nearest entity (follows movement) |
| `/projection detach [target]` | `dreamvoice.projection.use` | Detaches anchor from entity |
| `/projection distance <target> <dist>` | `dreamvoice.projection.use` | Sets projection speaking/hearing range |
| `/projection filter <target> <filter>` | `dreamvoice.projection.use` | Sets voice filter on projection |
| `/projection emit-anchor <target> <bool>` | `dreamvoice.projection.use` | Toggles voice emission at anchor |
| `/projection emit-player <target> <bool>` | `dreamvoice.projection.use` | Toggles voice emission at camera/player |
| `/projection hear-anchor <target> <bool>` | `dreamvoice.projection.use` | Toggles hearing around anchor |
| `/projection hear-player <target> <bool>` | `dreamvoice.projection.use` | Toggles hearing around camera/player |
| `/projection wall <target> <bool>` | `dreamvoice.projection.use` | Toggles VoiceWall occlusion on projection |
| `/projection list` | `dreamvoice.projection.use` | Lists all active voice projections |
| `/projection info [target]` | `dreamvoice.projection.use` | Displays full projection settings |
| `/projection save` | `dreamvoice.projection.save` | Saves all projections to disk |
| `/projection reload` | `dreamvoice.projection.reload` | Reloads all projections from disk |
