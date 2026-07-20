# Mall Room Builder

Client-side NeoForge mod for **Minecraft Java 1.21.1** that carves and skins grid-aligned "mall"
rooms. Stand where you want the room, aim down the hallway, run one command.

> ⚠️ **This mod automates mining and movement.** It sends only vanilla packets and never extends
> reach, but it initiates block breaking and steers your character without input. Anti-cheat
> plugins will flag it. Use it in singleplayer or on a private server. Any key press or mouse
> movement stops it instantly.

## What it builds

| | Finished | Carved envelope | Mined | Skinned |
|---|---|---|---|---|
| Room | 5×5×5 | 7×7×7 | 275 | 150 |
| Hallway (per block of length) | 3 wide × 5 tall | 5 wide × 7 tall | 31 | 16 |

A room is a **5×5×5 interior** wrapped in a 1-block decorated skin, so the carved envelope is
**7×7×7**. Of the 218 shell blocks, only the **150** that form the six flat 5×5 faces are ever
visible from inside — the other **68**, lying on the cube's 12 edges and 8 corners, are the
**framing**. The mod never touches them. That is the whole point: you mine 275 blocks instead of
343, and the hidden stone stays where it is.

Hallways are **3 wide and 5 tall** — full room height, narrower — so their floor and ceiling planes
line up exactly with the room's and the envelopes meet flush. Room pitch is `7 + hallLength`.

```
        7×7×7 envelope                      the framing rule
   ┌───────────────────┐            ██ ▓▓ ▓▓ ▓▓ ▓▓ ▓▓ ██     ██ framing  (2+ extremes,
   │  ┌─────────────┐  │            ▓▓ ·· ·· ·· ·· ·· ▓▓                  never mined)
   │  │             │  │            ▓▓ ·· ·· ·· ·· ·· ▓▓     ▓▓ face plate (1 extreme,
   │  │   5×5×5     │  │            ▓▓ ·· ·· ·· ·· ·· ▓▓                  mined + cobbled)
   │  │  interior   │  │            ▓▓ ·· ·· ·· ·· ·· ▓▓
   │  │             │  │            ▓▓ ·· ·· ·· ·· ·· ▓▓     ·· interior  (0 extremes,
   │  └─────────────┘  │            ██ ▓▓ ▓▓ ▓▓ ▓▓ ▓▓ ██                  mined to air)
   └───────────────────┘
```

Where a hallway pierces a room wall, the envelope's bottom edge runs along the doorway floor. By
the strict framing rule that is hidden stone — but once the doorway is open you walk right over it.
So the hallway's floor and ceiling strips are extended across the threshold (6 extra blocks per
doorway). Set `coverDoorThreshold = false` for the strict rule.

## Usage

```
/mallroom preview [rooms] [hallLength]    what it would cost — no side effects
/mallroom build   [rooms] [hallLength]    carve, then skin, automatically
/mallroom status                          current phase and progress
/mallroom stop                            abort cleanly
```

Where you stand and which way you face when you run `build` is the anchor, and it is **snapshotted**
— the room floor is at your feet, the room is centred on you laterally, and you are standing inside
room 0. Later rooms extend in the direction you were facing, snapped to the nearest cardinal.

A two-room mall at the default hallway length is 355 air + 362 cobblestone = **717 blocks to mine**,
about six stacks of cobble. Run `preview` first.

### How it mines

Through the real client mining path (`continueDestroyBlock`), so blocks break progressively at the
speed of whatever you are holding, **your pickaxe takes the durability**, and **you keep every
drop**. Reach is checked against the server's own rule with zero padding, so it is strictly tighter
than what the server would accept — the mod never reaches further than you can.

When nothing is in reach it walks you to the next block. It always prefers standing still and
mining.

### Order of operations

Ceiling first (so gravel above is disturbed while the room is still solid and lands on un-carved
ground), then top-down through the body (never undermining), then the floor plate last, near to far
— so you are always mining floor behind and beside yourself. The block you are actually standing on
is skipped and picked up on a later pass, once you have walked forward.

Building is the carve order reversed, which lands as floor → walls → ceiling and starts at the far
end, so there is no walk back.

## Stopping it

Any of these aborts immediately: **W/A/S/D, jump, sneak, sprint, left/right/middle click, drop,
inventory, opening any screen, or moving the mouse more than 1°.** Mouse-look is a reliable signal
precisely because the mod never rotates your camera (the server does not validate look direction
for breaking or placing), so any rotation at all is unambiguously you.

It also stops on low health, on entering lava, on changing dimension, and on leaving the world.
Running out of cobblestone **pauses** rather than aborts — restock and it resumes.

## Install

Needs NeoForge 21.1.x for Minecraft 1.21.1. Drop the jar in `mods/`. Client only; the server
neither needs nor sees it.

Build from source with `./gradlew build` (JDK 21; set `JAVA_HOME` if your default is newer). Output
lands at `build/libs/mallroombuilder-<version>.jar`.

## Config

`config/mallroombuilder-client.toml`. The ones you are most likely to touch:

| Key | Default | |
|---|---|---|
| `defaultRoomCount` / `defaultHallLength` | `1` / `5` | used when `build` gets no arguments |
| `buildBlock` | `minecraft:cobblestone` | any block item you are carrying |
| `coverDoorThreshold` | `true` | cobble the doorway thresholds |
| `autoWalkEnabled` | `true` | off = only mine what is already in reach, then stop |
| `abortOnPlayerInput` | `true` | the dead-man's switch. Turning it off is unsupported |
| `lookAbortDegrees` | `1.0` | mouse-look sensitivity of the switch |
| `autoSelectTool` | `false` | off by design — you chose which pickaxe to hold |
| `maxQueuedBlocks` | `20000` | safety net against a mistyped room count |

## Verification status

The geometry is covered by **116 unit tests** (`./gradlew test`) — every count in the table above is
asserted, along with the ordering invariants that keep you from digging the floor out from under
yourself. The mod is confirmed to load cleanly in a dev client.

**The in-game behaviour has not been played through yet.** If you are the first to run it, this is
the checklist:

- [ ] `/mallroom preview 3 7` prints sane counts and the right direction
- [ ] One room in flat stone: 275 mined, all drops collected, durability consumed, break animation visible
- [ ] Dead-man's switch: tap W → "forward key"; nudge the mouse → "mouse look"; open inventory → "a screen was opened"; left-click → "attack button"
- [ ] Auto-walk crosses a 12-block pitch and steps up a 1-block lip
- [ ] Floor plate: you never fall more than 1 block and never suffocate; the last floor block still gets cobbled
- [ ] Gravel column above the ceiling → the verify sweep collects what falls, room ends clean
- [ ] Run out of cobble mid-build → pauses; restock → resumes
- [ ] `/mallroom stop` mid-carve leaves no stuck cracking overlay
- [ ] Hotbar slot restored afterwards; no mid-break slot switch resetting progress
- [ ] A 3-room mall, including across a chunk boundary — doorways line up between rooms

## Licence

Apache-2.0.
