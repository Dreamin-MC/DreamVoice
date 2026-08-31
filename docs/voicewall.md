# 🧱 VoiceWall & Acoustic Physics

The **VoiceWall** module is an advanced spatial acoustic engine for Minecraft Paper and Simple Voice Chat. It handles voice occlusion through solid walls, sound diffraction around obstacles, audio propagation through open doorways and apertures, high-frequency air absorption, and real-time visual particle debugging.

---

## 📑 Table of Contents
- [Occlusion Modes](#-occlusion-modes)
- [Simplified Material Configuration](#-simplified-material-configuration)
- [Diffraction & Obstacle Bypass](#-diffraction--obstacle-bypass)
- [Air Damping (High-Frequency Absorption)](#-air-damping-high-frequency-absorption)
- [Visual Particle Debug & Action Bar](#-visual-particle-debug--action-bar)
- [Java API Usage](#-java-api-usage)
- [In-Game Commands](#-in-game-commands)

---

## 🛡️ Occlusion Modes

The service provides 3 distinct operating modes via the `VoiceWallMode` enum:

| Mode | Description | Use Case |
|---|---|---|
| `STRICT_BLOCK` | **100% Soundproof Walls**. Whenever a solid wall separates players (without an open doorway/air path), sound is completely cut off (0 volume). | Danganronpa, Murder Mystery, investigation minigames, private rooms, and secret cells. |
| `REALISTIC` | **Realistic Acoustic Attenuation**. Sound is gradually muffled and reduced based on block thickness and material properties (wood, stone, glass, etc.). | Realistic survival, classic roleplay, immersive audio environments. |
| `OFF` | **Disabled**. Standard Simple Voice Chat behavior (no wall occlusion calculations). | Testing and vanilla voice behavior. |

---

## 🎛️ Simplified Material Configuration

No need to configure 800+ Minecraft block IDs one by one! The configuration file `codex.yml` utilizes **smart automatic categories** based on material names and tags, while still allowing granular overrides for specific blocks:

```yaml
voiceWall:
  enabled: true
  mode: STRICT_BLOCK          # STRICT_BLOCK, REALISTIC, OFF
  globalMultiplier: 1.0       # Global server-wide insulation multiplier (e.g. 1.3 for +30% soundproofing)
  airDamping: true            # High-frequency loss over distance
  defaultAttenuation: 15.0    # Fallback dB reduction for unclassified solid blocks
  
  # Smart automatic categories:
  categories:
    glass: 6.0                # Glass blocks, panes, tinted glass, beacons...
    wood: 10.0                # Planks, logs, wood doors, fences, gates, slabs, stairs...
    earth: 8.0                # Dirt, sand, gravel, clay, mud...
    foliage: 3.0              # Leaves, vines, bushes, moss...
    wool: 18.0                # Wool blocks, carpets, beds, banners (acoustic absorbers)
    stone: 25.0               # Stone, bricks, concrete, terracotta, deepslate, ores...
    metal: 35.0               # Iron blocks, gold, copper, anvils, iron doors...
    water: 20.0               # Water, lava...

  # High-priority specific block overrides:
  overrides:
    BEDROCK: 100.0            # 100 dB = guaranteed total silence
    BARRIER: 100.0
    IRON_DOOR: 30.0
```

---

## 🔀 Diffraction & Obstacle Bypass

In real acoustics, sound waves do not only travel in straight lines: they bend around obstacles and pass through open doorways.

```
       [Player A] (Speaker)
           |
       ====|==== Wall
      [Open Door] ----> Sound travels through the air opening!
           |
       [Player B] (Listener)
```

1. **Tier 1 - Fast Sweep (Pillars, Trees, Low Walls)**:
   * Instantly tests orthogonal offset rays ($\pm\text{width}$, $+\text{height}$) in microseconds.
   * If a player is hiding behind a 1-block pillar, tree trunk, or low half-wall, sound bends around it with configurable soft diffraction loss.
2. **Tier 2 - Bounded Air Pathfinding (`AcousticPathFinder`)**:
   * If a larger wall separates players, an ultra-fast 3D voxel BFS searches for a continuous air path (open doors, open trapdoors, open fence gates, windows, connecting corridors).
   * If an air path is found $\rightarrow$ Sound travels with the real air path distance $+$ diffraction corner penalty.
   * If all apertures are closed $\rightarrow$ Total cutoff in `STRICT_BLOCK` mode or material calculation in `REALISTIC` mode.

### Diffraction Configuration:
```yaml
voiceWall:
  diffraction:
    enabled: true
    maxBypassWidth: 2.5       # Max lateral bypass width in blocks (pillars, wall edges)
    maxBypassHeight: 2.5      # Max vertical bypass height in blocks (low walls, fences)
    maxPathDistance: 14.0     # Max search radius for open doorways and corridors
    diffractionLossDb: 4.0    # Base dB penalty when sound bends around a corner
    lossPerMeter: 1.2         # Additional dB penalty per extra meter of air detour
```

---

## 🌬️ Air Damping (High-Frequency Absorption)

In real environments, air naturally absorbs high audio frequencies over distance (distant voices sound warmer and darker).
* Enabled via `airDamping: true` or `/voicewall airdamping true`.
* Progressive DSP low-pass smoothing applied beyond 5 meters.

---

## 🔮 Visual Particle Debug & Action Bar

Diagnose acoustics in real-time with per-player visual debugging without creating any visual spam for regular players:

* **Command**: `/voicewall debug [player]` (individual ON / OFF toggle).
* **Particle Color Codes**:
  * 🟢 **Green**: Direct line of sight in free air (`DIRECT`).
  * 🟡 **Yellow / Orange**: Diffracted sound (`CONTOURNÉ`: particle trail shows the exact path through an open door or around a pillar).
  * 🔴 **Red**: Blocked wall (`ATTENUATED` or `BLOCKED 100%`).
* **Real-time Action Bar Diagnostics**:
  `[VoiceWall Debug] Target: Alex | CONTOURNÉ (Door/Corner) (-4.8 dB, 7.2m)`

---

## 💻 Java API Usage

```java
// Retrieve VoiceWall service
VoiceWallService wallService = DreamVoice.getService(VoiceWallService.class);

// Change occlusion mode
wallService.setMode(VoiceWallMode.STRICT_BLOCK);

// Enable/disable air damping
wallService.setAirDampingEnabled(true);

// Toggle visual debugging for an admin player
wallService.toggleDebugPlayer(adminPlayer);

// Perform a manual acoustic raycast check
RaycastResult result = VoiceRayCast.check(playerA, playerB);
System.out.println("Propagation Type: " + result.type()); // DIRECT, DIFFRACTED, WALL_ATTENUATED, WALL_BLOCKED
System.out.println("Attenuation: " + result.totalAttenuation() + " dB");
```

---

## 🕹️ In-Game Commands

| Command | Permission | Description |
|---|---|---|
| `/voicewall debug [player]` | `dreamvoice.wall.manage` | Toggles visual particle debug & Action Bar diagnostics for a player |
| `/voicewall mode <strict\|realistic\|off>` | `dreamvoice.wall.manage` | Changes wall occlusion mode |
| `/voicewall toggle` | `dreamvoice.wall.manage` | Quickly toggles VoiceWall on/off |
| `/voicewall airdamping <true\|false>` | `dreamvoice.wall.manage` | Enables/disables high-frequency air damping |
| `/voicewall info` | `dreamvoice.wall.manage` | Displays current status and diffraction settings |
