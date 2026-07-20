# Mall Room Builder

Client-side NeoForge mod for **Minecraft Java 1.21.1** that carves out mall rooms. Stand in the
spine hallway, face the wall you want opened, run one command.

> ⚠️ **This mod automates mining and movement.** It sends only vanilla packets and never extends
> reach, but it initiates block breaking and steers your character without input. Anti-cheat
> plugins will flag it. Use it in singleplayer or on a private server. Any key press or mouse
> movement stops it instantly.

## What it does

**It carves. It does not build.** A room is 250 blocks of mining and nothing is placed — decorating
the recesses afterwards is your job. The only block the mod ever puts down is a cobblestone backfill
into framing that has gone missing.

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

| Job | Carved | Framing left |
|---|---|---|
| `room` | 250 | 44 |
| `room both` — two facing rooms | 500 | 88 |
| `spine` — a 7 × 3 × 5 corridor segment | 105 | 0 |

A **spine segment is a plain box** — no recesses, no framing. It's also the interior height only, so
its floor stays where you're standing. That's deliberate: a room's floor recess is carved one *below*
the walking surface so you can lay a finished floor into it by hand and end up level with the
corridor. Carving the corridor floor too would drop the hallway a block and break the flush join.

## Usage

```
/mallroom room  [both]          carve a room off the spine
/mallroom spine [length]        carve the next segment of corridor (default 7 long)

/mallroom preview room  [both]  counts only — no side effects
/mallroom preview spine [length]

/mallroom status                current progress
/mallroom stop                  abort cleanly
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

The mod never mines framing — but gravel falls and mobs happen. Every second it re-checks that the
framing is still standing, and replaces anything missing with cobblestone. This runs **between
blocks, never mid-break**: switching hotbar slots during a break resets destroy progress to zero. If
you keep cobblestone in your **off hand** it never touches your hotbar at all.

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
| `autoWalkEnabled` | `true` | off = only mine what's already in reach, then stop |
| `abortOnPlayerInput` | `true` | the dead-man's switch. Turning it off is unsupported |
| `lookAbortDegrees` | `1.0` | mouse-look sensitivity of the switch |
| `autoBackfillFraming` | `true` | repair framing that goes missing |
| `backfillBlock` | `minecraft:cobblestone` | what to repair it with |
| `autoSelectTool` | `false` | off by design — you chose which pickaxe to hold |

## Verification status

The geometry is covered by **120 unit tests** (`./gradlew test`) — every count above is asserted,
along with the ordering invariants that keep a job from deadlocking on an unreachable ceiling or
digging the floor out from under you. The mod is confirmed to load cleanly in a dev client.

**The in-game behaviour has not been played through yet.** If you're the first to run it:

- [ ] `/mallroom preview room` and `preview spine` print sane counts and the right direction
- [ ] `spine`: 105 mined, box lands exactly where you expected, next segment tiles onto it with no gap
- [ ] `room`: 250 mined, all drops collected, durability consumed, break animation visible
- [ ] The finished shape matches a hand-built room — recesses right, corner framing intact
- [ ] **Resume works**: stop a room half-way, stand in the same block, run it again, it finishes
- [ ] Dead-man's switch: tap W → "forward key"; nudge the mouse → "mouse look"; open inventory → "a screen was opened"; left-click → "attack button"
- [ ] Auto-walk carries you into the room as the near slices open
- [ ] Floors: you end one block lower inside the room, never fall further, never suffocate
- [ ] Gravel above the ceiling → the framing watcher backfills anything it knocks out
- [ ] `room both` produces two rooms facing each other, correctly spaced
- [ ] `/mallroom stop` mid-carve leaves no stuck cracking overlay
- [ ] Hotbar restored afterwards; no mid-break slot switch resetting progress

## Licence

Apache-2.0.
