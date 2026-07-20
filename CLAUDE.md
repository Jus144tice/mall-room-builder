# CLAUDE.md — Mall Room Builder

> AI-oriented project guide. Navigate by **symbol anchors** (class / method / field names), never
> line numbers — line numbers go stale. To jump to anything below, `Grep` the quoted symbol.

## What this mod is

A **client-side, NeoForge, Minecraft 1.21.1** carver for mall rooms and the spine hallway joining
them. You stand facing the way you want to build; it mines the volume and leaves.

**One anchoring rule covers both job kinds: the block directly in front of the player is the first
block of the job** (`MallAnchor.START_OFFSET` = 1). Nothing is read from the world, which is what
makes a partial job resumable — same standing block, same volume, every time.

- **It carves. It does not build.** A room is 250 blocks of mining and nothing is placed. Decorating
  the recesses is done by hand afterwards. The one exception is backfilling framing that has gone
  missing (gravel, mobs) — that is the only reason cobblestone appears anywhere in this codebase.
- **Topology:** a spine hallway with rooms budding off it perpendicular, on both sides, shoulder to
  shoulder. A room's whole hallway-facing side is **open**; the pillars framing each opening are the
  corridor wall, present between rooms and absent where a room is.
- **Geometry:** 5x5x5 interior, plus a 1-block recess behind each of its five faces. The corners
  where two recesses would meet are never visible from inside and are left exactly as found — the
  **framing**, 44 cells.
- It **initiates** mining and **steers movement**. That is genuine automation — unlike its siblings
  in this collection it is *not* vanilla-server-safe, and the README says so plainly.

User docs: [README.md](README.md). History: [CHANGELOG.md](CHANGELOG.md). Every vanilla/NeoForge
symbol it leans on: [docs/vanilla-hooks.md](docs/vanilla-hooks.md).

---

## MANDATE — this file is self-updating

**Keep this file in sync with the code in the SAME session the code changes. Do not defer.**

Triggers: adding / removing / renaming anything in the **File & symbol map**; changing the
**geometry rules**, the **mine ordering**, the **state machine**, or the **abort triggers**; adding
/ removing / renaming a **config option** or changing a default; changing build tooling or platform
versions.

Pure-comment or internal-refactor changes with identical public symbols may be left alone. When in
doubt, update. Keep edits surgical.

---

## The geometry, precisely

Everything is expressed in three facing-relative numbers, and `RoomGeometry.extremeCount` is the
whole rule:

```
d  = blocks back from the opening plane   0..5   (0 is the opening, 5 is the back plate)
s  = blocks along the spine               -3..3
dy = height above the walking surface     -1..5

extremes = (d == 5) + (|s| == 3) + (dy == -1 || dy == 5)

  0 -> interior       125  carved
  1 -> face recess    125  carved      } 250 carved per room
  2 -> edge            40
  3 -> corner           4  } 44 framing, never mined
                          envelope 6 x 7 x 7 = 294
```

**The depth axis has only one extreme.** The front is a hole, not a wall. If you ever add `d == 0`
to that predicate the mod will wall the player in and skin jumps to 150 — `RoomGeometryTest`'s
`onlyTheBackPlaneCountsOnTheDepthAxis` exists to catch exactly that.

A **spine segment** is a plain box: `SpineGeometry.DEFAULT_LENGTH` (7) long by 3 wide, and 7 tall
finished or 5 tall roughed. Floor and ceiling get the same recess treatment a room's do — both are
replaced eventually — but there are **no side recesses and no framing**, because the corridor's sides
are where rooms open.

### Rough versus finish

`MallSpec.finishRecesses` is a per-job switch, defaulting from `Config.carveFinishRecesses` and
overridable by the `rough` / `finish` command literals.

- **finish** — the finished volume plus the 1-block recesses. The player ends one block lower.
- **rough** — the finished volume only. The floor is untouched, so the player stays level.

**Rough is always a strict subset of finish** (`RoomGeometryTest.roughIsASubsetOfFinish`,
`SpineGeometryTest.roughIsAlwaysASubsetOfFinish`). Combined with the world-independent anchor and
`retireAlreadyCarved`, that is what makes "rough a run out now, re-run the same jobs later" cut
exactly the recesses and nothing else. Do not break either half of that.

Job sizes: `room` 250/44, `room rough` 125/44, `room both` 500/88, `spine` 147/0, `spine rough` 105/0.

---

## Architecture

**No mixins.** The only mod in the collection without any, deliberately: NeoForge's
`MovementInputUpdateEvent` fires exactly where auto-walk needs it, and mining and placing are public
methods. Nothing can fail at class-transform time. Do not add a mixin without a hard reason.

**Pure `core/` with zero Minecraft imports**, plus a thin `client/` bridge that owns every world read
and write.

```
/mallroom room [both]  |  /mallroom spine [length]
  → MallCommand                       (RegisterClientCommandsEvent, game bus)
    → JobEngine.start()               anchorFor() = position + facing only, builds MallLayout,
                                      size + chunk checks → state = ARMING

every client tick
  → ClientEvents.onClientTickPost     → JobEngine.tick()
      ARMING  : wait for InputWatch.allReleased(), then arm() latches look baseline,
                selectBestTool(), latchMiningSlot()
      CARVING : InputWatch.tripped()? → abort
                watchFraming()        → queue any framing gone to air
                target air yet?       → cursor.complete()
                tryBackfill()         → only between blocks, never mid-break
                back on the mining slot? → else swap back and skip the tick
                cursor.select(canCarve) → MineDriver.drive()  (continueDestroyBlock + swing)
                nothing in reach      → retireAlreadyCarved(), then AutoWalk.steerTo(cursor.peek())
                queue empty           → verifyCarve() → finish()

every movement update
  → AutoWalk.onMovementInput          writes Input.forwardImpulse / leftImpulse / up / jumping
```

### Invariants — do not break without updating the tests and this file

- **The `core` package never imports a Minecraft class.** All conversion happens in `client/`.
- **The anchor is a snapshot.** The player is about to be walked around and will drop a block into
  the floor recess; live tracking would drift the geometry mid-job.
- **The anchor reads nothing from the world.** `JobEngine.anchorFor` is position + facing, and the
  job always starts at `START_OFFSET` = 1. An earlier version scanned forward for the first solid
  block; that broke the moment a room was half-carved, because the scan sailed through the opening
  and anchored the room somewhere else. Determinism is what makes resuming work — do not reintroduce
  world-dependent anchoring.
- **Progress is re-derived from the world, never assumed.** Carved iff `isAir()`. One verify sweep
  then recovers from server rejections, falling gravel, other players' blocks, and unloaded chunks.
- **Reach uses `canInteractWithBlock(pos, 0.0)`** — the server admits `1.0`, so ours is strictly
  tighter and can never send a rejected action.
- **Never mine a block the held tool cannot harvest.** `tickCarving` gates on
  `MineDriver.canHarvest` and *pauses* rather than mining — breaking it would destroy the material
  the mod exists to collect. The pause is also the grace window for a tool-replacement mod. Use the
  **position-sensitive** `player.hasCorrectToolForDrops(state, level, pos)`; the single-argument
  overload is deprecated because it bypasses NeoForge's harvest-check event and would ignore other
  mods' tool rules.
- **A hotbar slot change is not automatically a takeover.** `reconcileToolSwap` runs *before*
  `InputWatch.tripped` and accepts the change when the new item can still harvest the target, so a
  mod that auto-replaces a broken pickaxe does not abort the job. Order matters: reconcile first,
  then check.
- **Never change hotbar slots mid-break.** `sameDestroyTarget` calls `shouldCauseBlockBreakReset`, so
  a slot change zeroes destroy progress. Backfill only runs when `currentTarget == null`, and
  `tickCarving` swaps back to `HotbarSelector.miningSlot()` before driving another break.
- **`retireAlreadyCarved` must run before steering.** A cell already air is never a valid target, so
  without it the queue never drains and auto-walk steers at it forever.
- **`ARMING` is not optional.** The player pressed Enter to send the command; without the wait the
  dead-man's switch fires on tick one, every time.

---

## File & symbol map

### Pure geometry — `com.jus144tice.mallroombuilder.core` (no Minecraft imports)

| File | Symbols | Purpose |
|---|---|---|
| [GridPos.java](src/main/java/com/jus144tice/mallroombuilder/core/GridPos.java) | record `(x,y,z)`; `plus`, `minus`, `offset(Facing,int)`, `lateral`, `withY`, `at(Facing,along,side,y)` | Minecraft-free block position. `at` is the facing-relative constructor everything else describes cells with. |
| [Facing.java](src/main/java/com/jus144tice/mallroombuilder/core/Facing.java) | `SOUTH,WEST,NORTH,EAST` (**declaration order is MC's 2D data values**), `fromYaw`, `stepX/stepZ`, `left()`, `opposite()` | `fromYaw` reproduces `Direction.fromYRot` via `Math.floorMod`, so unnormalised player yaws work. Names match `net.minecraft.core.Direction` for name-based conversion. |
| [RoomPlacement.java](src/main/java/com/jus144tice/mallroombuilder/core/RoomPlacement.java) | record `(openingCentre, depth)`; `cell(d,s,y)`, `depthOf`, `sideOf`, `floorY`, `floorPlateY`, `ceilingPlateY` | One room's position and orientation. A room is *directed* — open at the front, walled at the back — so everything is d/s/dy relative to the opening. |
| [MallSpec.java](src/main/java/com/jus144tice/mallroombuilder/core/MallSpec.java) | record `(kind, bothSides, hallDepth, spineLength, **finishRecesses**)`; `Kind` (ROOM/SPINE); factories `room(...)`, `spine(...)`; `roomCount()`, `oppositeOpeningOffset()`, `modeName()` | What one job carves. `oppositeOpeningOffset` is `hallDepth + 1`: the corridor plus the two wall planes the openings occupy. |
| [MallAnchor.java](src/main/java/com/jus144tice/mallroombuilder/core/MallAnchor.java) | **`START_OFFSET`** (1); record `(playerFeet, facing)`; `of`, `facedRoom()`, `oppositeRoom(spec)`, **`spineStart()`**, `floorPlateY`, `ceilingPlateY`, `cell`, `alongOf`, `sideOf` | Where the job goes, from position and facing alone. `facedRoom()` and `spineStart()` return the same frame — both begin at the next block ahead. |
| [SpineGeometry.java](src/main/java/com/jus144tice/mallroombuilder/core/SpineGeometry.java) | `DEFAULT_LENGTH` (7), `RADIUS` (1), `WIDTH` (3), `INTERIOR_HEIGHT` (5), `ENVELOPE_HEIGHT` (7); `interior`, `recesses`, **`carve(start, length, includeRecesses)`**, `cellCount(length, includeRecesses)` | A plain corridor box: floor and ceiling recesses like a room's, but no side recesses and no framing. |
| [RoomGeometry.java](src/main/java/com/jus144tice/mallroombuilder/core/RoomGeometry.java) | `INTERIOR_SIZE` (5), `INTERIOR_RADIUS` (2), `ENVELOPE_RADIUS` (3), `BACK_PLATE_DEPTH` (5); **`extremeCount(d,s,dy)`**; `interior`, `faceRecesses`, `framing`, `envelope`, **`carve(room, includeRecesses)`** | **The whole framing rule is `extremeCount`.** `carve` is the rough/finish switch: interior alone, or interior plus the five face recesses. |
| [MallLayout.java](src/main/java/com/jus144tice/mallroombuilder/core/MallLayout.java) | ctor (branches on `spec.kind()`); `carve()`, `framing()`, `counts()`, **`mineOrder()`**; private `addRoom`, `addSpine`, `keyFor`, record `SortKey` | **The composer and single source of truth.** Two disjoint sets: cells to mine, and cells to protect and backfill. `keyFor` encodes the ordering; units are faced room 0, opposite room 1 (a spine job is a single unit 0). |
| [MallCounts.java](src/main/java/com/jus144tice/mallroombuilder/core/MallCounts.java) | record `(carvedCount, framingCount)`; `minedTotal()`, `envelopeTotal()` | `minedTotal == carvedCount` because nothing is ever placed. |
| [QueueCursor.java](src/main/java/com/jus144tice/mallroombuilder/core/QueueCursor.java) | `select`, `peek`, `complete`, `defer`, `requeue`, `sweep`, `outstanding`, `done/remaining/total`, `sweepsUsed`, `deferredCount` | Ordered work list with a deferral tail and a bounded sweep counter. Completion is always the caller's call against the world. |
| [WalkVector.java](src/main/java/com/jus144tice/mallroombuilder/core/WalkVector.java) | record `(forward,left,jump)`, `STILL`, `toward(...)`, `isMoving()` | Inverts `Entity.getInputVector`'s rotation. Round-tripped against a reimplementation in the tests. |

### Client bridge — `com.jus144tice.mallroombuilder.client`

| File | Symbols | Purpose |
|---|---|---|
| [JobEngine.java](src/main/java/com/jus144tice/mallroombuilder/client/JobEngine.java) | `INSTANCE`; `State` (IDLE/ARMING/CARVING); `start`, `abort`, `finish`, `clear`, `tick`, `tickArming`, `tickCarving`, `selectCarveTarget`, `canCarve`, `verifyCarve`, **`watchFraming`**, **`tryBackfill`**, `steerOrStall`, **`retireAlreadyCarved`**, **`reconcileToolSwap`**, `safetyGate`, `touchesLiquid`, `checkLoaded`, **`anchorFor`**, `statusLine`, `progressLine`; field `wrongToolTicks` | **The state machine.** `selectCarveTarget` holds the pedestal rule (skip the block underfoot until `stepOffTimeoutTicks`). `anchorFor` is position + facing only, and static so `MallCommand.preview` shares it. |
| [MineDriver.java](src/main/java/com/jus144tice/mallroombuilder/client/MineDriver.java) | `toBlockPos`, `inReach`, `isCarved`, **`canHarvest`**, `blockName`, `faceFromEye`, `drive`, `cancel` | `drive` is one `continueDestroyBlock` + `swing` per tick — that call self-starts, self-paces and self-completes, so there is no per-block state machine. `canHarvest` uses the position-sensitive NeoForge overload. |
| [PlaceDriver.java](src/main/java/com/jus144tice/mallroombuilder/client/PlaceDriver.java) | record `Support(pos, face)` + `hitVec()`; `isPlaceable`, `findSupport`, `place(mc, player, support, hand)` | Used **only** for framing backfill. Synthesizes a `BlockHitResult` on a neighbour's face and calls `useItemOn` — the `LineLockManager.tryReacharound` technique. |
| [AutoWalk.java](src/main/java/com/jus144tice/mallroombuilder/client/AutoWalk.java) | `steerTo`, `stop`, `isSteering`, `tick`, `desiredWalk`, `wantsJump`, `onMovementInput` | Writes `Input`'s impulse and boolean fields from `MovementInputUpdateEvent`. Set the booleans too — `LocalPlayer` reads them after the event for sprint/jump. |
| [InputWatch.java](src/main/java/com/jus144tice/mallroombuilder/client/InputWatch.java) | `watched`, `allReleased`, `arm`, `setExpectedSlot`, `expectedSlot`, `tripped`, `trippedKey`, `angleDelta` | The dead-man's switch. Trustworthy because the engine writes `Input` fields directly and **never** touches `KeyMapping` state, so `key*.isDown()` is never reading back our own writes. |
| [HotbarSelector.java](src/main/java/com/jus144tice/mallroombuilder/client/HotbarSelector.java) | `backfillItem`, `backfillBlock`, `remember`, `miningSlot`, `latchMiningSlot`, `onMiningSlot`, `selectMiningSlot`, **`backfillHand`**, `selectBackfillSlot`, `selectBestTool`, `restore`, `forget` | `backfillHand` checks the off hand first-class: a player keeping cobblestone there never triggers a hotbar swap, so backfill can never disturb a break. |
| [MallCommand.java](src/main/java/com/jus144tice/mallroombuilder/client/MallCommand.java) | `onRegisterClientCommands`, `roomSpec`, `spineSpec`, `run`, `preview`, `status`, `stop`, `feedback` | `/mallroom room [both]`, `/mallroom spine [length]`, `/mallroom preview room\|spine ...`, `status`, `stop`. **Never add `.requires(hasPermission(n))`** — client sources report permission 0 on servers, which makes the command silently vanish. |
| [ClientEvents.java](src/main/java/com/jus144tice/mallroombuilder/client/ClientEvents.java) | `onClientTickPost`, `lastDimension` | Ticks the engine; aborts on leaving the world or changing dimension (the anchor is world coordinates). |
| [HudOverlay.java](src/main/java/com/jus144tice/mallroombuilder/client/HudOverlay.java) | `onRenderGui` | Progress readout. Not decoration — a job runs for minutes at a floor of ~5 ticks per block. |

### Entry point, config, resources

| File | Symbols | Purpose |
|---|---|---|
| [MallRoomBuilder.java](src/main/java/com/jus144tice/mallroombuilder/MallRoomBuilder.java) | `MODID`, `LOGGER`, `debug(String)`, ctor | `@Mod(dist = Dist.CLIENT)`. Registers the CLIENT config and four game-bus listeners. |
| [Config.java](src/main/java/com/jus144tice/mallroombuilder/Config.java) | `SPEC` + values and matching null-safe getters; `safeGet` overloads | `config/mallroombuilder-client.toml`. Getters fall back to defaults if queried before load. |
| [neoforge.mods.toml](src/main/templates/META-INF/neoforge.mods.toml) | — | **Templated** (`src/main/templates`, expanded by `generateModMetadata`), so `mod_version` lives only in `gradle.properties`. `side = "CLIENT"`, **no `[[mixins]]`**. |
| [mallroombuilder.png](src/main/resources/mallroombuilder.png) | — | Mod-list icon: the 7x7 cross-section — emerald face recesses, dark interior, neutral corner framing. Regenerate with `tools/make_icon.py` (Python + Pillow). |

---

## Tests

JUnit 5 on the moddev `unitTest` harness. `.\gradlew.bat test`. 137 tests; the geometry is where the
value is.

| File | Covers |
|---|---|
| [RoomGeometryTest.java](src/test/java/com/jus144tice/mallroombuilder/core/RoomGeometryTest.java) | **125 interior / 125 recess / 44 framing / 294 envelope / 250 carved**; the partition is exact; **`onlyTheBackPlaneCountsOnTheDepthAxis`** and `theOpeningPlaneHasNoBackWall` guard the open front; each slice carves 45 of 49 leaving 4 corners |
| [SpineGeometryTest.java](src/test/java/com/jus144tice/mallroombuilder/core/SpineGeometryTest.java) | **147 finished / 105 rough**; starts at the block in front, never includes the player's own; `aFinishedSegmentSpansTheSameHeightAsARoom`; **`roughIsAlwaysASubsetOfFinish`**; **`consecutiveSegmentsTileWithoutGapOrOverlap`** |
| [MallLayoutTest.java](src/test/java/com/jus144tice/mallroombuilder/core/MallLayoutTest.java) | room **250 / 44**, rough **125 / 44**; `both` **500 / 88**; spine **147 / 0**, rough **105 / 0**; the two rooms face each other 4 apart and never overlap; carve ∩ framing = ∅; **`theSameStandingSpotAlwaysDescribesTheSameVolume`** (the resume guarantee) |
| [MineOrderTest.java](src/test/java/com/jus144tice/mallroombuilder/core/MineOrderTest.java) | permutation of the carve set; never queues framing; **every floor cell follows every non-floor cell**; **`aRoomIsCarvedFrontToBack`** and `aSpineSegmentIsCarvedFrontToBackToo` (the anti-deadlock assertions); ceiling leads each slice, body top-down; one room at a time |
| [MallAnchorTest.java](src/test/java/com/jus144tice/mallroombuilder/core/MallAnchorTest.java) | **`aJobAlwaysStartsAtTheVeryNextBlock`**; room and spine share a start; **`nothingIsReadFromTheWorldSoTheGeometryIsReproducible`**; opposite room mirrors 4 back; `alongOf`/`sideOf` invert `cell` for every facing |
| [FacingTest.java](src/test/java/com/jus144tice/mallroombuilder/core/FacingTest.java) | `fromYaw` against the vanilla formula for every degree in ±1080 |
| [WalkVectorTest.java](src/test/java/com/jus144tice/mallroombuilder/core/WalkVectorTest.java) | round-trip through a reimplementation of `getInputVector` across yaws × directions |
| [GridPosTest.java](src/test/java/com/jus144tice/mallroombuilder/core/GridPosTest.java), [QueueCursorTest.java](src/test/java/com/jus144tice/mallroombuilder/core/QueueCursorTest.java), [MallCountsTest.java](src/test/java/com/jus144tice/mallroombuilder/core/MallCountsTest.java) | position algebra; cursor defer/sweep/requeue semantics and the sweep bound |
| [ConfigTest.java](src/test/java/com/jus144tice/mallroombuilder/ConfigTest.java) | spec paths and defaults; getters survive the un-loaded state; the safety defaults are the safe ones |

**Nothing in `client/` is unit-tested** — it needs a live client. See the manual checklist in the
README, and keep it honest.

## Build

- **NeoForge 21.1.233**, **ModDevGradle 2.0.141**, **JDK 21**, Parchment `2024.11.17`, Spotless
  6.25.0 (palantir-java-format). Versions live in [gradle.properties](gradle.properties).
- `.\gradlew.bat build` = format + compile + test + jar → `build/libs/mallroombuilder-<version>.jar`.
  Set `JAVA_HOME` to a JDK 21 if your default is newer.
- `.\gradlew.bat runClient` for a dev client.
- Spotless is folded in: `compileJava` `dependsOn 'spotlessApply'`, and `spotlessCheck` gates `check`.
- CI: [.github/workflows/build.yml](.github/workflows/build.yml) on push/PR to `main`.

## Gotchas

- **Changing the geometry means editing `core/` and its tests only.** The client layer does not know
  the rules and must not learn them.
- **The front face is not an extreme.** This is the single easiest thing to get wrong, and it walls
  the player in. `RoomGeometryTest` asserts it from both directions.
- **Ordering is anti-deadlock, not just anti-gravel.** Whole-ceiling-first is correct for a room you
  stand inside and *wrong* for an alcove carved from outside. If you touch `MallLayout.keyFor`,
  `MineOrderTest.aRoomIsCarvedFrontToBack` is the assertion that matters.
- **Backfill must never run mid-break.** A hotbar swap resets destroy progress to zero. The
  `currentTarget == null` guard in `tickCarving` is what enforces it.
- **Throughput is floored at ~5 ticks per block** by `destroyDelay`. A 397-block job takes minutes.
  That is why the HUD exists.
- **The player finishes one block lower than they started** on any `finish` job, standing in the
  floor recess. That is correct and matches the hand-built rooms — not a bug. A `rough` job leaves
  them level, because it never cuts the floor.
- **A partial job is finished by re-running it from the same block.** That only holds because the
  anchor is world-independent; `MallLayoutTest.theSameStandingSpotAlwaysDescribesTheSameVolume` is
  the assertion protecting it.
- **In-game behaviour is unverified.** The build is green and the mod loads, but nobody has played a
  job through end to end. Update the README checklist as items are confirmed.
