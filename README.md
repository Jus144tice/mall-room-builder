# Mall Room Builder

Client-side NeoForge mod for **Minecraft Java 1.21.1** that carves out mall rooms. Stand in the
spine hallway, face the wall you want opened, run one command.

> ⚠️ **This mod automates mining and movement.** It sends only vanilla packets and never extends
> reach, but it initiates block breaking and steers your character without input. Anti-cheat
> plugins will flag it. Use it in singleplayer or on a private server. Any key press or mouse
> movement stops it instantly.

## What it does

**It carves, and it fills what you tell it to.** A room is 250 blocks of mining. By default nothing
is placed into the recesses; name a surface and a hotbar slot and it lays that material in after
cutting it. Framing gaps get backfilled with cobblestone regardless — see below.

The mall is a spine hallway with rooms budding off it, shoulder to shoulder, openings facing the
corridor:

```
TOP VIEW

  [room][ ][room][ ][room]      each room 5 deep, 5 wide
  ════════════════════════      pillars between them
  ········ spine ·········      corridor, 3 wide
  ════════════════════════
  [room][ ][room][ ][room]
```

A room's entire hallway-facing side is **open** — the pillars framing each opening are the corridor
wall, present between rooms and absent where a room is. So a room has five faces, not six.

Each of those five faces gets a **1-block recess** carved behind it, which is what you decorate. The
corners where two recesses would meet are never visible from inside, so they are left exactly as
found — that's the **framing**:

```
CROSS-SECTION                              CARVE, in facing-relative terms

 dy    s: -3  -2  -1   0  +1  +2  +3       d  = blocks back from the opening (0..5)
 +5       ██  ▓▓  ▓▓  ▓▓  ▓▓  ▓▓  ██       s  = blocks along the spine (-3..+3)
 +4       ▓▓  ··  ··  ··  ··  ··  ▓▓       dy = height above the floor (-1..+5)
 +3       ▓▓  ··  ··  ··  ··  ··  ▓▓
 +2       ▓▓  ··  ··  ··  ··  ··  ▓▓       count the extremes:
 +1       ▓▓  ··  ··  ··  ··  ··  ▓▓         d == 5        (back only; the front is open)
  0       ▓▓  ··  ··  ··  ··  ··  ▓▓         |s| == 3      (the pillars)
 -1       ██  ▓▓  ▓▓  ▓▓  ▓▓  ▓▓  ██         dy == -1 or 5 (floor and ceiling)

 ·· interior   carved      0 extremes → interior      125
 ▓▓ recess     carved      1 extreme  → face recess   125   } 250 carved
 ██ framing    untouched   2+         → framing        44
                                       envelope 6×7×7 = 294
```

Note the depth axis has only **one** extreme. The front isn't a wall, it's a hole — that's what makes
a room five faces rather than six, and getting it wrong would wall you in.

A **spine segment** gets the same treatment on floor and ceiling — both are replaced eventually, so
both get a recess — but no side recesses and **no framing**, since its sides are where rooms open. So
it's a plain box, 3 wide and 7 tall finished (or 5 tall roughed).

| Job | Carved | Framing left |
|---|---|---|
| `room` | 250 | 44 |
| `room rough` | 125 | 44 |
| `room both` | 500 | 88 |
| `room both rough` | 250 | 88 |
| `spine` (7 long) | 147 | 0 |
| `spine rough` | 105 | 0 |

### Rough vs finish

Every job takes an optional `rough` or `finish`:

- **`finish`** (the default) cuts the finished volume *and* the 1-block recesses that hold the
  decorative course. You end **one block lower**, standing in the floor recess.
- **`rough`** cuts only the finished volume. The floor and ceiling stay put, so you **stay at the
  level you started at**.

Rough is for when you don't have the decorative blocks on you. Because the anchor is deterministic
and already-carved cells retire on sight, you can **rough a whole run out now, then walk back later
and re-run the same jobs from the same spots** — and only the recesses get cut.

### Filling from your hotbar

Name a surface and a hotbar slot and the job fills that recess after carving it:

```
/mallroom spine ceiling 4 floor 3
/mallroom room finish floor 3 walls 5 ceiling 4 beam 6
/mallroom fill room beam 6                  fill only, over something already carved
```

Slots are hotbar positions **1–9** as you see them. Surfaces are:

| Surface | Room | Spine |
|---|---|---|
| `floor` | 25 | 3 × length |
| `walls` | 75 — back wall plus **both side walls** | — |
| `ceiling` | 20 | 3 × length |
| `beam` | 5 — the lintel row across the entrance | — |

Every surface is optional and they can be given **in any order**, so `floor 3` alone works, and
`beam 6 ceiling 4` is the same as `ceiling 4 beam 6`. Naming none means carve-only.

A room's `walls` covers the back wall *and* both side walls. Two adjacent rooms each have their own
side wall, which is why an unfilled pair shows a 2-block gap between their openings — that's two
walls back to back waiting for material.

Filling runs **floor → walls → ceiling → beam**. Floor first is deliberate: it puts your walking
level back where the carve dropped it from, so the rest is placed from your normal standing height.
The floor itself is laid far-to-near so you back out of the room rather than stranding yourself on
the last cell. Run out of a material and the job **pauses** naming the surface and slot — restock
and it resumes.

`/mallroom fill` skips carving entirely, for finishing something already dug. Same anchor rule, so
stand where you'd stand to carve it.

## Usage

```
/mallroom room  [both]   [rough|finish] [<surface> <slot>]...
/mallroom spine [length] [rough|finish] [<surface> <slot>]...

/mallroom fill room  [both]   <surface> <slot>...     fill without carving
/mallroom fill spine [length] <surface> <slot>...

/mallroom preview ...                                 same shapes, counts only
/mallroom status                                      current progress
/mallroom stop                                        abort cleanly
```

**One rule covers both jobs: stand facing the way you want to build, and the block directly in front
of you is the first block of the job.**

- `room` — stand on the spine at the **lateral centre** of the room you want, facing it. One step
  forward would put you off the spine and inside the room. Add `both` to also carve the room
  directly opposite.
- `spine` — stand on the **centre lane** of the spine you're extending, facing along it. The segment
  runs 7 forward, 3 wide, 5 tall.

Nothing is read from the world; the geometry comes purely from your position and facing. That's what
makes a **partial job resumable** — stand in the same block and run it again, and it re-derives
exactly the same volume and picks up whatever's left.

Everything is snapshotted when you run it. You're about to be walked around, so tracking you live
would drift the geometry mid-job.

### How it mines

Through the real client mining path (`continueDestroyBlock`), so blocks break progressively at the
speed of whatever you're holding, **your pickaxe takes the durability**, and **you keep every drop**.
Reach is checked against the server's own rule with zero padding — strictly tighter than what the
server would accept, so the mod never reaches further than you can.

When nothing is in reach it walks you to the next block. It always prefers standing still and mining.

**It will not break a block it can't harvest.** Hit obsidian with a stone pickaxe and the job
*pauses* rather than mining — breaking it would destroy it for nothing, and keeping the material is
the whole point. After `toolGraceTicks` (default 40, two seconds) it aborts and names the block.

That pause doubles as the window for a **tool-replacement mod**: if your pickaxe breaks mid-job and
something swaps a fresh one in, the job picks straight back up. If the replacement lands in a
*different* hotbar slot, that's tolerated too — a slot change is treated as a tool swap rather than
as you taking over, as long as the new item can still harvest the block being worked. Switching to
something that can't still aborts. (Trade-off: scrolling from one pickaxe to another no longer
aborts. Mouse-look and WASD were always the strong signals; the slot check was the weak one. Set
`allowToolSwap = false` for the old strict behaviour.)

### Order of operations

**Slice by slice, near to far.** A room is an alcove cut into rock from outside: the far end is
neither reachable nor walkable until the near end is open. Doing the whole ceiling first — which is
right for a room you stand in the middle of — would deadlock here, since the far ceiling is about
5.25 blocks away and you can't walk in to close the gap. Within each slice the ceiling still leads,
so anything unstable overhead drops while there's solid ground beneath it.

**Floor recesses last, across the whole job.** Carving the floor drops you a block, and a lower eye
costs reach on every ceiling cell after it. Leaving all the floors to a final pass means the job is
worked from your original standing height throughout, and the one-block drop happens at the very end.
Expect to finish standing one block lower than you started, with framing ledges along the wall bases.

### Framing backfill

The mod never mines framing — but a carve often **breaks into a cave**, leaving the corners that
would normally be solid stone as open air. After carving, a repair phase walks to every such gap and
fills it with cobblestone, so the room keeps its full frame. It's the one and only block the mod
places into the shell.

Gaps that go out of reach are walked to, not abandoned. If you have no cobblestone it says so and
skips the repair rather than hanging. Keep cobblestone in your **off hand** and it never touches
your hotbar; otherwise it borrows a hotbar slot and puts it back at the end.

### Gravel and sand

Carving the ceiling out from under a gravel or sand deposit makes it pour into the room. The mod
**waits for it to land, then re-mines it**, and only calls the carve done once the volume has stayed
clear for a moment (`gravelSettleTicks`, default 10). A tall column just means a few more passes —
it drains the whole thing rather than leaving a pile behind.

## Stopping it

Any of these aborts immediately: **W/A/S/D, jump, sneak, sprint, left/right/middle click, drop,
inventory, opening any screen, or moving the mouse more than 1°.** Mouse-look is a reliable signal
precisely because the mod never rotates your camera (the server doesn't validate look direction for
breaking or placing), so any rotation at all is unambiguously you.

It also stops on low health, entering lava, changing dimension, and leaving the world.

## Install

Needs NeoForge 21.1.x for Minecraft 1.21.1. Drop the jar in `mods/`. Client only; the server neither
needs nor sees it.

Build from source with `./gradlew build` (JDK 21; set `JAVA_HOME` if your default is newer). Output
lands at `build/libs/mallroombuilder-<version>.jar`.

## Config

`config/mallroombuilder-client.toml`. The ones you're most likely to touch:

| Key | Default | |
|---|---|---|
| `hallDepth` | `3` | corridor width; sets how far apart two facing rooms sit |
| `spineLength` | `7` | default length of `/mallroom spine` |
| `carveFinishRecesses` | `true` | whether jobs cut the recesses by default; `rough`/`finish` override it |
| `autoWalkEnabled` | `true` | off = only mine what's already in reach, then stop |
| `abortOnPlayerInput` | `true` | the dead-man's switch. Turning it off is unsupported |
| `lookAbortDegrees` | `1.0` | mouse-look sensitivity of the switch |
| `autoBackfillFraming` | `true` | repair framing gaps (e.g. where a carve breaks into a cave) |
| `backfillBlock` | `minecraft:cobblestone` | what to repair them with |
| `gravelSettleTicks` | `10` | how long the volume must stay clear before carving is done |
| `abortOnWrongTool` | `true` | stop rather than break a block that wouldn't drop |
| `toolGraceTicks` | `40` | how long to wait for a replacement tool before giving up |
| `allowToolSwap` | `true` | treat a hotbar change to another valid tool as a swap, not a takeover |
| `autoSelectTool` | `false` | off by design — you chose which pickaxe to hold |

## Verification status

The geometry is covered by **170 unit tests** (`./gradlew test`) — every count above is asserted,
along with the ordering invariants that keep a job from deadlocking on an unreachable ceiling or
digging the floor out from under you. The mod is confirmed to load cleanly in a dev client.

**The in-game behaviour has not been played through yet.** If you're the first to run it:

- [ ] `/mallroom preview room` and `preview spine` print sane counts and the right direction
- [ ] `spine rough`: 105 mined, box lands where expected, you stay at the same level
- [ ] `spine` (finish): 147 mined, 7 tall, you end one block lower; next segment tiles on with no gap
- [ ] **Rough then finish**: `spine rough`, walk back, `spine finish` from the same block → only the recesses get cut
- [ ] `room`: 250 mined, all drops collected, durability consumed, break animation visible
- [ ] The finished shape matches a hand-built room — recesses right, corner framing intact
- [ ] **Resume works**: stop a room half-way, stand in the same block, run it again, it finishes
- [ ] Dead-man's switch: tap W → "forward key"; nudge the mouse → "mouse look"; open inventory → "a screen was opened"; left-click → "attack button"
- [ ] Auto-walk carries you into the room as the near slices open
- [ ] Floors: you end one block lower inside the room, never fall further, never suffocate
- [ ] Carve a room that breaks into a cave → the missing corners get filled with cobblestone (have some on you)
- [ ] No cobblestone on you → it reports the gap count and skips repair instead of hanging
- [ ] Carve the ceiling out under a gravel/sand deposit → it waits, re-mines what falls, and ends clean
- [ ] Put obsidian in the path with a stone pickaxe → job pauses, then aborts naming the block, obsidian intact
- [ ] Let a pickaxe break mid-job → your replacement mod swaps in and the job carries on
- [ ] `/mallroom preview spine ceiling 4 floor 3` lists both surfaces with the item and count in each slot
- [ ] `/mallroom spine ceiling 4 floor 3` carves then fills; you end level, not one block lower
- [ ] Empty a slot mid-fill → pauses naming the surface and slot; restock → resumes
- [ ] `/mallroom room floor 3 walls 5 ceiling 4 beam 6` puts the beam row across the entrance only
- [ ] `/mallroom fill room beam 6` over an already-carved room fills just the beam
- [ ] `room both` produces two rooms facing each other, correctly spaced
- [ ] `/mallroom stop` mid-carve leaves no stuck cracking overlay
- [ ] Hotbar restored afterwards; no mid-break slot switch resetting progress

## Licence

Apache-2.0.
