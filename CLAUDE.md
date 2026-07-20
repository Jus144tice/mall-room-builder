# CLAUDE.md — Mall Room Builder

> AI-oriented project guide. Navigate by **symbol anchors** (class / method / field names), never
> line numbers — line numbers go stale. To jump to anything below, `Grep` the quoted symbol.

## What this mod is

A **client-side, NeoForge, Minecraft 1.21.1** builder for grid-aligned "mall" rooms. Given where the
player stands and faces, it computes a chain of rooms and hallways, mines the volume through the
real vanilla mining path, and skins the visible faces in cobblestone.

- A room is a **5×5×5 interior** in a **7×7×7 carved envelope**. Of the 218 shell blocks only the
  **150** forming the six flat 5×5 faces are visible from inside; the **68** on edges and corners
  (the **framing**) are never mined.
- Hallways are **3 wide × 5 tall** finished, **5 × 7** carved. Room pitch = `7 + hallLength`.
- It **initiates** mining and **steers movement**. That makes it genuine automation — unlike its
  siblings in this collection it is *not* vanilla-server-safe, and the README says so plainly.
- It sends only vanilla packets, never extends reach, and never rotates the camera.

User-facing docs live in [README.md](README.md); version history in [CHANGELOG.md](CHANGELOG.md);
every vanilla/NeoForge symbol it leans on is written up in [docs/vanilla-hooks.md](docs/vanilla-hooks.md).

---

## MANDATE — this file is self-updating

**Keep this file in sync with the code in the SAME session the code changes. Do not defer.**

Triggers (non-exhaustive):

- Adding / removing / renaming any class, method, field, or resource in the **File & symbol map**.
- Changing the **geometry rules**, the **mine or build ordering**, the **state machine**, or the
  **abort triggers**.
- Adding / removing / renaming a **config option** or changing a default.
- Changing build tooling or platform versions.

Pure-comment or internal-refactor changes with identical public symbols may be left alone. When in
doubt, update. Keep edits surgical.

---

## Architecture

**No mixins.** This is the only mod in the collection without any, and it is deliberate: NeoForge's
`MovementInputUpdateEvent` fires exactly where auto-walk needs it, and mining and placing are public
methods. Nothing can fail at class-transform time. Do not add a mixin without a hard reason.

Same split as `bedrock-line-placement`: **a pure `core/` with zero Minecraft imports**, plus a thin
`client/` bridge that owns every world read and write.

```
/mallroom build
  → MallCommand                       (RegisterClientCommandsEvent, game bus)
    → JobEngine.start()               snapshot anchor, build MallLayout, size + chunk checks
      → state = ARMING

every client tick
  → ClientEvents.onClientTickPost     → JobEngine.tick()
      ARMING    : wait for InputWatch.allReleased(), then InputWatch.arm() latches look baseline
      CARVING   : InputWatch.tripped()? → abort
                  target air yet? → cursor.complete()
                  cursor.select(canCarve) → MineDriver.drive()   (continueDestroyBlock + swing)
                  nothing in reach → retireAlreadyFinished(), then AutoWalk.steerTo(cursor.peek())
                  queue empty → verifyCarve() → beginBuildPhase()
      BUILDING  : HotbarSelector.ensureBuildBlock() → PAUSED_NO_MATERIAL if none
                  cursor.select(placeable + has support) → PlaceDriver.place()  (useItemOn)
                  queue empty → verifyBuild() → finish()

every movement update
  → AutoWalk.onMovementInput          writes Input.forwardImpulse / leftImpulse / up / jumping
```

### Invariants — do not break without updating the tests and this file

- **The `core` package never imports a Minecraft class.** All conversion happens in `client/`.
- **The anchor is a snapshot.** Taken once in `JobEngine.anchorFor`, never re-read. The player is
  about to be walked around and may drop a block off the last floor plate; live tracking would drift
  the geometry mid-job.
- **Progress is re-derived from the world, never assumed.** Carved iff `isAir()`, skinned iff the
  block matches. This one choice is what lets a single verify sweep recover from server rejections,
  falling gravel, other players' blocks, and unloaded chunks.
- **Reach uses `canInteractWithBlock(pos, 0.0)`** — the server admits `1.0`, so ours is strictly
  tighter and can never send a rejected action. Do **not** copy `bedrock-line-placement`'s
  face-centre distance; it wrongly rejects reachable corner blocks here.
- **Never change hotbar slots mid-break.** `sameDestroyTarget` calls `shouldCauseBlockBreakReset`, so
  a slot change zeroes destroy progress. `HotbarSelector` is only called at phase boundaries.
- **`retireAlreadyFinished` must run before steering.** A cell already in its final state is never a
  valid target, so without it the queue never drains and auto-walk steers at it forever.
- **`ARMING` is not optional.** The player pressed Enter to send the command; without the wait the
  dead-man's switch fires on tick one, every time.

---

## File & symbol map

### Pure geometry — `com.jus144tice.mallroombuilder.core` (no Minecraft imports)

| File | Symbols | Purpose |
|---|---|---|
| [GridPos.java](src/main/java/com/jus144tice/mallroombuilder/core/GridPos.java) | record `(x,y,z)`; `plus`, `minus`, `offset(Facing,int)`, `lateral`, `withY`, `at(Facing,along,side,y)` | Minecraft-free block position. `at` is the facing-relative constructor the geometry classes describe every cell with. |
| [Facing.java](src/main/java/com/jus144tice/mallroombuilder/core/Facing.java) | `SOUTH,WEST,NORTH,EAST` (**declaration order is MC's 2D data values**), `fromYaw`, `stepX/stepZ`, `left()`, `opposite()` | Cardinal direction. `fromYaw` reproduces `Direction.fromYRot` via `Math.floorMod`, so unnormalised player yaws work. Names match `net.minecraft.core.Direction` for name-based conversion. |
| [MallSpec.java](src/main/java/com/jus144tice/mallroombuilder/core/MallSpec.java) | record `(roomCount, hallLength, coverDoorThreshold)`; `pitch()`, `hallCount()` | What to build. Validates in the compact constructor. |
| [MallAnchor.java](src/main/java/com/jus144tice/mallroombuilder/core/MallAnchor.java) | record `(playerFeet, facing)`; `of`, `roomReference`, `floorPlateY`, `ceilingPlateY`, `cell(along,side,y)`, `alongOf`, `sideOf` | Where the mall goes. Floor Y = feet Y; interior ±2 laterally; room *i* at `feet + facing × i·pitch`. `alongOf`/`sideOf` invert `cell` by dot product. |
| [RoomGeometry.java](src/main/java/com/jus144tice/mallroombuilder/core/RoomGeometry.java) | `INTERIOR_SIZE` (5), `ENVELOPE_SIZE` (7), `INTERIOR_RADIUS` (2), `ENVELOPE_RADIUS` (3); **`extremeCount`**; `interior`, `envelope`, `visibleSkin`, `framing` | **The framing rule lives in `extremeCount`**: count coordinates at an envelope extreme — 0 interior, 1 visible face plate, 2+ framing. Yields 125/343/150/68/218 exactly. |
| [HallGeometry.java](src/main/java/com/jus144tice/mallroombuilder/core/HallGeometry.java) | `INTERIOR_RADIUS` (1), `ENVELOPE_RADIUS` (2); `gapStart/gapEnd`, `openingStart/openingEnd`, `interior`, `visibleSkin` | Reuses `extremeCount` with radius 2. **Opening span = gap + 2 planes**, which cuts the doorways through the room wall plates for free. `visibleSkin` adds the threshold strips when `coverDoorThreshold`. |
| [MallLayout.java](src/main/java/com/jus144tice/mallroombuilder/core/MallLayout.java) | ctor; `air()`, `skin()`, `counts()`, **`mineOrder()`**, **`buildOrder()`**; private `sorted`, `key`, `unitOrdinal`, record `Keyed` | **The composer and single source of truth.** `skin.removeAll(air)` is the line that cuts doorways. `unitOrdinal` derives the unit from `along` **alone**, which is what guarantees a floor cell and the interior above it share a unit. |
| [MallCounts.java](src/main/java/com/jus144tice/mallroombuilder/core/MallCounts.java) | record `(airCount, skinCount)`; `minedTotal()`, `stacksNeeded()` | Preview arithmetic. |
| [QueueCursor.java](src/main/java/com/jus144tice/mallroombuilder/core/QueueCursor.java) | `select`, `peek`, `complete`, `defer`, `requeue`, `sweep`, `outstanding`, `done/remaining/total`, `sweepsUsed`, `deferredCount` | Ordered work list with a deferral tail and a bounded sweep counter. Completion is always the caller's call against the world. |
| [WalkVector.java](src/main/java/com/jus144tice/mallroombuilder/core/WalkVector.java) | record `(forward,left,jump)`, `STILL`, `toward(...)`, `isMoving()` | Inverts `Entity.getInputVector`'s rotation. Round-tripped against a reimplementation in the tests. |

### Client bridge — `com.jus144tice.mallroombuilder.client`

| File | Symbols | Purpose |
|---|---|---|
| [JobEngine.java](src/main/java/com/jus144tice/mallroombuilder/client/JobEngine.java) | `INSTANCE`; `State` (IDLE/ARMING/CARVING/BUILDING/PAUSED_NO_MATERIAL); `start`, `abort`, `finish`, `tick`, `tickArming`, `tickCarving`, `tickBuilding`, `selectCarveTarget`, `canCarve`, `verifyCarve`, `beginBuildPhase`, `verifyBuild`, `steerOrStall`, **`retireAlreadyFinished`**, `safetyGate`, `touchesLiquid`, `checkLoaded`, `anchorFor`, `statusLine`, `progressLine` | **The state machine.** Owns all world I/O. `selectCarveTarget` holds the pedestal rule (skip the block underfoot until `stepOffTimeoutTicks`). |
| [MineDriver.java](src/main/java/com/jus144tice/mallroombuilder/client/MineDriver.java) | `toBlockPos`, `inReach`, `isCarved`, `faceFromEye`, `drive`, `cancel` | `drive` is one `continueDestroyBlock` + `swing` per tick — that call self-starts, self-paces and self-completes, so there is no per-block state machine. |
| [PlaceDriver.java](src/main/java/com/jus144tice/mallroombuilder/client/PlaceDriver.java) | record `Support(pos, face)` + `hitVec()`; `isPlaceable`, `findSupport`, `place` | Synthesizes a `BlockHitResult` on a neighbour's face and calls `useItemOn` — the `LineLockManager.tryReacharound` technique. `isPlaceable`'s `isUnobstructed` is the entire answer to "don't place a block inside the player". |
| [AutoWalk.java](src/main/java/com/jus144tice/mallroombuilder/client/AutoWalk.java) | `steerTo`, `stop`, `isSteering`, `tick`, `desiredWalk`, `wantsJump`, `onMovementInput` | Writes `Input`'s impulse and boolean fields from `MovementInputUpdateEvent`. Set the booleans too — `LocalPlayer` reads them after the event for sprint/jump. |
| [InputWatch.java](src/main/java/com/jus144tice/mallroombuilder/client/InputWatch.java) | `watched`, `allReleased`, `arm`, `setExpectedSlot`, `expectedSlot`, `tripped`, `trippedKey`, `angleDelta` | The dead-man's switch. Trustworthy because the engine writes `Input` fields directly and **never** touches `KeyMapping` state, so `key*.isDown()` is never reading back our own writes. |
| [HotbarSelector.java](src/main/java/com/jus144tice/mallroombuilder/client/HotbarSelector.java) | `buildItem`, `buildBlock`, `remember`, `ensureBuildBlock`, `selectBestTool`, `select`, `restore`, `forget` | Slot switching is a bare `Inventory.selected` write; `ensureHasSentCarriedItem()` syncs it. `select` also calls `InputWatch.setExpectedSlot` so our own write is not read as the player. |
| [MallCommand.java](src/main/java/com/jus144tice/mallroombuilder/client/MallCommand.java) | `onRegisterClientCommands`, `specOf`, `preview`, `build`, `status`, `stop`, `feedback` | The `/mallroom` tree. **Never add `.requires(hasPermission(n))`** — client sources report permission 0 on servers, which makes the command silently vanish. |
| [ClientEvents.java](src/main/java/com/jus144tice/mallroombuilder/client/ClientEvents.java) | `onClientTickPost`, `lastDimension` | Ticks the engine; aborts on leaving the world or changing dimension (the anchor is world coordinates). |
| [HudOverlay.java](src/main/java/com/jus144tice/mallroombuilder/client/HudOverlay.java) | `onRenderGui` | Progress readout. Not decoration — a job runs for minutes at a floor of ~5 ticks per block. |

### Entry point, config, resources

| File | Symbols | Purpose |
|---|---|---|
| [MallRoomBuilder.java](src/main/java/com/jus144tice/mallroombuilder/MallRoomBuilder.java) | `MODID`, `LOGGER`, `debug(String)`, ctor | `@Mod(dist = Dist.CLIENT)`. Registers the CLIENT config and four game-bus listeners. |
| [Config.java](src/main/java/com/jus144tice/mallroombuilder/Config.java) | `SPEC` + 26 values and matching null-safe getters; `safeGet` overloads | `config/mallroombuilder-client.toml`. Getters fall back to defaults if queried before load. |
| [neoforge.mods.toml](src/main/templates/META-INF/neoforge.mods.toml) | — | **Templated** (`src/main/templates`, expanded by `generateModMetadata`), so `mod_version` lives only in `gradle.properties`. `side = "CLIENT"`, **no `[[mixins]]`**. |
| [mallroombuilder.png](src/main/resources/mallroombuilder.png) | — | Mod-list icon: a 7×7 cross-section, emerald face plates against neutral corner framing. Regenerate with `tools/make_icon.py` (Python + Pillow). |

---

## Tests

JUnit 5 on the moddev `unitTest` harness. `.\gradlew.bat test`. The geometry is where the value is —
if a count changes, the mod is building something other than what was designed.

| File | Covers |
|---|---|
| [RoomGeometryTest.java](src/test/java/com/jus144tice/mallroombuilder/core/RoomGeometryTest.java) | **125 / 343 / 218 / 150 / 68 / 275**; interior ∪ skin ∪ framing tiles the envelope exactly; every skin cell has exactly one extreme, every framing cell two or more |
| [HallGeometryTest.java](src/test/java/com/jus144tice/mallroombuilder/core/HallGeometryTest.java) | per-slice **35 / 15 / 20 / 16 / 4**; opening = gap + 2; plates are 3 wide (a plate cell at side ±2 is a corner, hence framing); thresholds add 12 per hallway |
| [MallLayoutTest.java](src/test/java/com/jus144tice/mallroombuilder/core/MallLayoutTest.java) | 1 room = 125/150/275; **2 rooms = 355 / 362 / 717**; doorways remove exactly 30; 10-block jamb; air ∩ skin = ∅ for every shape; framing untouched |
| [MineOrderTest.java](src/test/java/com/jus144tice/mallroombuilder/core/MineOrderTest.java) | permutation of air ∪ skin; **no floor cell precedes the interior above it**; ceiling first and floor last within a unit; units run forward without interleaving; body top-down |
| [BuildOrderTest.java](src/test/java/com/jus144tice/mallroombuilder/core/BuildOrderTest.java) | exactly `reverse(mineOrder ∩ skin)`; floor before ceiling; starts at the far end |
| [FacingTest.java](src/test/java/com/jus144tice/mallroombuilder/core/FacingTest.java) | `fromYaw` against the vanilla formula for every degree in ±1080, including negatives and quadrant boundaries |
| [WalkVectorTest.java](src/test/java/com/jus144tice/mallroombuilder/core/WalkVectorTest.java) | round-trip through a reimplementation of `getInputVector` across yaws × directions |
| [MallAnchorTest.java](src/test/java/com/jus144tice/mallroombuilder/core/MallAnchorTest.java), [GridPosTest.java](src/test/java/com/jus144tice/mallroombuilder/core/GridPosTest.java), [QueueCursorTest.java](src/test/java/com/jus144tice/mallroombuilder/core/QueueCursorTest.java), [MallCountsTest.java](src/test/java/com/jus144tice/mallroombuilder/core/MallCountsTest.java) | anchor arithmetic per facing incl. negatives; `alongOf`/`sideOf` invert `cell`; cursor defer/sweep/requeue semantics and the sweep bound |
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
- **`unitOrdinal` depends on `along` alone.** That is load-bearing for the "floor after body"
  invariant. If you make it depend on Y, `MineOrderTest` will tell you — listen to it.
- **`skin` is not 150 per room once rooms are joined.** Doorways subtract, hallways add. Ask
  `MallLayout.counts()`; never assume a per-room figure.
- **The ceiling-reach margin is thin.** The far ceiling corner is ~4.40 from a standing eye against a
  4.5 default reach. Sneaking drops the eye to 1.27 and puts corners out of range — auto-walk
  compensates, but this is the likeliest cause of a "it keeps shuffling around" report.
- **Throughput is floored at ~5 ticks per block** by `destroyDelay`. A 717-block mall takes minutes.
  That is why the HUD exists.
- **Open-air jobs stall by design** — a skin cell with no solid neighbour is never placeable. The
  sweep budget turns that into a clean stop rather than a spin.
- **In-game behaviour is unverified.** The build is green and the mod loads, but nobody has played a
  job through end to end. Update the README checklist as items are confirmed.
