# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning is
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] — 2026-07-20

Rebuilt around how the mall is actually laid out, after seeing screenshots of the hand-built rooms.
0.1.0 modelled the wrong thing throughout; this is not a refinement of it.

### Changed

- **Topology.** Rooms bud off a spine hallway perpendicular, on both sides, shoulder to shoulder —
  not a linear chain of rooms joined end to end. A room's whole hallway-facing side is **open**; the
  pillars framing each opening are the corridor wall, present between rooms and absent where a room
  is. So a room has five faces, not six.
- **The mod carves and nothing else.** There is no build phase. A room is 250 blocks of mining and
  decorating the recesses is done by hand. Removed `buildOrder`, the skin/air split, the `BUILDING`
  and `PAUSED_NO_MATERIAL` states, and build-block hotbar selection.
- **The depth axis has one extreme, not two.** The front is a hole, so a room is 125 interior + 125
  face recesses = 250 carved, with 44 framing left standing (was 150 skin / 68 framing).
- **Carve order is slice by slice, near to far.** Whole-ceiling-first is right for a room you stand
  inside and **deadlocks** for an alcove: the far ceiling is ~5.25 blocks away, past reach, and you
  cannot walk in to close the gap. Floor recesses now run as a final pass across the whole job, so
  everything is worked from the original standing height and the one-block drop happens at the end.
- **Anchoring.** Stand in the corridor facing the wall you want opened; facing picks the side. The
  opening plane is found by **scanning forward for the first solid block**, so it does not matter
  where across the corridor you stand — and facing an already-open room is a clean refusal.
- **Command.** `/mallroom build|preview [both]` replaces the room-count and hall-length arguments.
- Removed `coverDoorThreshold` and the doorway-threshold feature entirely: it modelled a punched
  doorway, and these rooms have no doorways.

### Added

- **Framing backfill.** The mod never mines framing, but gravel falls. It re-checks the framing every
  second and replaces anything missing with cobblestone — the only block it ever places. Runs
  between blocks, never mid-break, since a hotbar swap mid-break resets destroy progress. Keeping
  cobblestone in your off hand avoids the hotbar entirely.
- `hallDepth`, `finishHallway`, `maxWallScan`, `autoBackfillFraming`, `backfillBlock`,
  `framingScanInterval` config keys.

## [0.1.0] — 2026-07-20

Initial release. Client-side, NeoForge 21.1.233, Minecraft 1.21.1.

### Added

- **Mall geometry.** 5×5×5 finished rooms inside 7×7×7 carved envelopes, joined by 3-wide × 5-tall
  hallways. Only the 150 visible face-plate blocks per room are mined and skinned; the 68 framing
  blocks on the envelope's edges and corners are left as natural stone.
- **`/mallroom preview [rooms] [hallLength]`** — block counts, stacks of cobble needed, and the
  footprint, with no side effects.
- **`/mallroom build [rooms] [hallLength]`** — carves the whole volume, then skins it in
  cobblestone automatically.
- **`/mallroom status`** and **`/mallroom stop`**.
- **Real vanilla mining** via `continueDestroyBlock`: progressive break, your pickaxe takes the
  durability, you keep every drop. Reach is checked with zero padding — strictly tighter than the
  server's own rule.
- **Auto-walk** when the next block is out of reach, riding NeoForge's `MovementInputUpdateEvent`.
- **Dead-man's switch**: any key, mouse button, screen, or more than 1° of mouse-look aborts
  immediately. Also aborts on low health, lava, dimension change, and leaving the world.
- **Doorway thresholds** (`coverDoorThreshold`, default on): cobbles the floor and ceiling strips
  across each doorway, which the strict framing rule would leave as exposed stone.
- **Carve ordering** that will not strand you: ceiling first so falling gravel lands on solid
  ground, body top-down, floor plate last and near-to-far, and the block underfoot deferred until
  you have walked off it.
- **Verify sweeps** that re-derive progress from the world, recovering from server rejections,
  falling blocks, and blocks placed by other players with one mechanism.
- Progress HUD, 26 config options, and a mod-list icon.

### Notes

- **No mixins.** The first mod in this collection without any — auto-walk has a NeoForge event, and
  mining and placing are public methods. Nothing can fail at class-transform time.
- 116 unit tests cover the pure geometry, ordering invariants, and config defaults. The in-game
  behaviour has not yet been played through; see the checklist in the README.
- This mod automates mining and movement. It sends only vanilla packets, but anti-cheat plugins will
  flag it. Intended for singleplayer and private servers.
