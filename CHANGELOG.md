# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning is
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.6.0] — 2026-07-20

### Added

- **Fill recesses from your hotbar.** Name a surface and a hotbar slot and the job lays that material
  into the recess after cutting it:

  ```
  /mallroom spine ceiling 4 floor 3
  /mallroom room finish floor 3 walls 5 ceiling 4 beam 6
  ```

  Surfaces are `floor`, `walls`, `ceiling` and `beam` for a room; `floor` and `ceiling` for a spine.
  Slots are hotbar positions 1-9 as you see them. Every surface is optional and they may be given in
  **any order**, so `floor 3` alone works and `beam 6 ceiling 4` is the same as `ceiling 4 beam 6`.
  Naming none is carve-only, exactly as before.
- **The beam** — the ceiling row across the entrance plane, 5 wide, with its own material. The
  ceiling surface starts one block deeper so the two never overlap. The cells over the pillars stay
  framing and are neither carved nor filled.
- **`/mallroom fill room|spine <surface> <slot>...`** fills without carving, for finishing something
  already dug. Same anchor rule.
- Filling runs **floor → walls → ceiling → beam**. Floor first puts your walking level back where the
  carve dropped it from, so the rest is placed from normal standing height; the ceiling goes last
  from below where it is always in reach. The floor itself is laid far-to-near so you back out of the
  room rather than stranding yourself on the last cell.
- Running out of a material **pauses** naming the surface and slot; restock and it resumes.
  `/mallroom preview` now lists each surface with the item and count actually in that slot.

### Changed

- `walls` covers the back wall **and both side walls** — 75 blocks. The side walls were previously
  described as "pillars", which understated them: each is a full 5×5 wall, and an unfilled pair of
  adjacent rooms shows a 2-block gap precisely because those two walls have no material yet.
- `MallCommand.register(dispatcher)` split out of the event handler so the recursively-built tree can
  be unit-tested — it only runs on world join, where a broken tree would look like the command
  silently not existing.

## [0.5.0] — 2026-07-20

### Added

- **Never break a block the held tool cannot harvest.** Hit obsidian with a stone pickaxe and the job
  *pauses* instead of mining — breaking it would destroy the material the mod exists to collect.
  After `toolGraceTicks` (default 40) it aborts and names the block. Blocks that need no tool at all
  never trigger it.

  Uses NeoForge's position-sensitive `hasCorrectToolForDrops(state, level, pos)`, which routes
  through the harvest-check event, so other mods' tool rules are respected.
- **Tolerates a tool swap mid-job.** A hotbar slot change is reconciled *before* the dead-man's
  switch sees it: if the newly selected item can still harvest the target, the job re-latches and
  carries on. That keeps a pickaxe-auto-replace mod from aborting every job, and the harvest pause
  above doubles as its window to act.

  Trade-off: scrolling from one pickaxe to another no longer aborts. Mouse-look and WASD were always
  the strong takeover signals. `allowToolSwap = false` restores the strict behaviour.
- `abortOnWrongTool` (`true`), `toolGraceTicks` (`40`), `allowToolSwap` (`true`) config keys.

### Fixed

- The mods-list description still carried the 0.1.0 blurb — decorated skin, doorways, skinning in
  cobblestone — none of which has been true since 0.2.0.

## [0.4.0] — 2026-07-20

### Added

- **`rough` / `finish` on every job.** `finish` (the default) cuts the finished volume *and* the
  1-block recesses that hold the decorative course; `rough` cuts only the finished volume, leaving
  the floor untouched so you stay at the level you started at.

  Rough is for when you don't have the decorative blocks on you. Because the anchor is deterministic
  and already-carved cells retire on sight, and **rough is a strict subset of finish**, you can rough
  a whole run out now and re-run the same jobs from the same spots later — only the recesses get cut.
- `carveFinishRecesses` config key (default `true`) sets which mode the bare command uses; the
  literals override it either way.

### Changed

- **A spine segment is now 7 tall when finished**, not 5. Its floor and ceiling are replaced too, so
  both get a recess exactly as a room's do, and a finished segment drops you a block just like a
  room. `spine` is now 147 blocks, `spine rough` 105. Still no side recesses and no framing — the
  corridor's sides are where rooms open.
- `RoomGeometry.visibleSkin` renamed to `faceRecesses`; the "skin" wording was left over from when
  the mod placed blocks.

## [0.3.0] — 2026-07-20

### Added

- **`/mallroom spine [length]`** — carves the next segment of corridor: 7 long, 3 wide, 5 tall, 105
  blocks. A plain box with no recesses and no framing. It is the interior height only, so the
  corridor floor stays where you are standing — which is what leaves a finished room flush with it,
  since a room's floor recess is carved one *below* the walking surface.
- `spineLength` config key (default 7).

### Changed

- **Anchoring is now fixed, not scanned.** Stand facing the way you want to build and **the block
  directly in front of you is the first block of the job** — for a room you are laterally centred on
  it, for a spine you are on the centre lane. Nothing is read from the world.

  This makes **partial jobs resumable**: the same standing block always re-derives the same volume,
  so stopping half-way and running the command again finishes the rest. The old forward scan broke on
  exactly that case — it sailed through the opening of a half-carved room and anchored somewhere
  else.
- **`/mallroom build` is now `/mallroom room`**, and `preview` takes the job kind:
  `/mallroom preview room [both]` / `/mallroom preview spine [length]`.
- Removed `finishHallway` and `maxWallScan`, and deleted `HallGeometry` — the corridor is its own
  command now, so a room job carves only the room. A room is **250 carved / 44 framing**; `both` is
  **500 / 88**.

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
