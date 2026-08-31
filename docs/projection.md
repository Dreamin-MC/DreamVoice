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
1. **Voice Output**: Their spoken voice no longer emits from their physical player body, but projects directly from the remote projection point.
2. **Audio Input (`hearPlayerEnvironment`)**:
   * If enabled (`true`, default): The player continues to hear players speaking around their camera/drone or physical position.
   * If disabled (`false`): The player only hears audio return from the remote projection point.

---

## 🎥 Camera & Fake Player Mode

In roleplay and mystery scenarios (e.g. **Danganronpa**, detective minigames with surveillance cameras or drones):
* The player controls a camera view or a fake player body in another room.
* Their voice must appear to originate from the remote room or camera speaker.
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

// 1. Create a static projection point:
VoiceProjection proj = projService.createProjection("lab_camera", targetLocation);

// 2. Create a projection bound to a mobile drone entity:
VoiceProjection droneProj = projService.createProjection("drone_01", droneEntity);

// 3. Assign a player and configure audio properties:
proj.setPlayerUuid(player.getUniqueId());
proj.setFilterId("disguise");          // Anonymizes the player's voice
proj.setHearPlayerEnvironment(true);   // Hears voices around the camera
proj.setDistance(24.0);
```

---

## 🕹️ In-Game Commands

| Command | Permission | Description |
|---|---|---|
| `/projection create <name> [distance] [filter]` | `dreamvoice.projection.manage` | Creates a projection point at current position |
| `/projection attach <name>` | `dreamvoice.projection.manage` | Attaches projection to the nearest entity (5 blocks) |
| `/projection detach <name>` | `dreamvoice.projection.manage` | Detaches projection from entity |
| `/projection set <name> <player> [hearEnv]` | `dreamvoice.projection.manage` | Assigns a player to the voice projection |
| `/projection delete <name>` | `dreamvoice.projection.manage` | Deletes the projection point |
| `/projection list` | `dreamvoice.projection.use` | Lists all active projections |
| `/projection info <name>` | `dreamvoice.projection.manage` | Displays full projection details (assigned player, entity, filter) |
