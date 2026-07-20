# Vanilla hooks — Minecraft 1.21.1 / NeoForge 21.1.233

Every vanilla and NeoForge symbol this mod depends on, written up in our own words, with the fact
that matters for *our* use. Read this before touching `client/`.

Verified by reading the decompiled sources produced by the moddev plugin. **Do not vendor them.**
To grep them yourself, unzip the sources jar in the moddev cache:

```
~/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_*.jar
```

(pick the one whose listing contains `MultiPlayerGameMode.java`). IDE "go to definition" resolves
these after a Gradle sync.

---

## Why this mod has no mixins

Every other mod in this collection mixes into vanilla. This one does not, and that is a deliberate
consequence of three findings below: NeoForge already fires an event at the exact point auto-walk
needs (§3), and mining and placing are **public** methods we can simply call (§1, §2). There is no
`mixins.json`, no refmap, and nothing that can fail at class-transform time. The mapping-sensitive
surface is ordinary method calls that fail at *compile* time if a name changes — which is far
better than failing at *load* time in someone's game.

---

## 1. Mining — `MultiPlayerGameMode`

`net.minecraft.client.multiplayer.MultiPlayerGameMode`, reached via the public field
`Minecraft.gameMode`.

| Member | Signature | Notes |
|---|---|---|
| `startDestroyBlock` | `public boolean (BlockPos, Direction)` | Begins a break. We rarely call it directly — see below. |
| `continueDestroyBlock` | `public boolean (BlockPos, Direction)` | **The workhorse.** |
| `stopDestroyBlock` | `public void ()` | Sends `ABORT_DESTROY_BLOCK` and clears progress. Call on every abort or the breaking overlay sticks. |
| `isDestroying` | `public boolean ()` | True only while actively breaking — **also false during `destroyDelay`**, so it is not a completion signal. |
| `getDestroyStage` | `public int ()` | 0–9 crack stage, for the HUD only. |
| `useItemOn` | `public InteractionResult (LocalPlayer, InteractionHand, BlockHitResult)` | Placement. See §2. |
| `destroyProgress` | **private, no getter** | Do not plan on reading it. Progress must be inferred from the world. |

### `continueDestroyBlock` self-starts and self-completes

This is the single most useful fact in this document. In one call it:

1. calls `ensureHasSentCarriedItem()` (see §5),
2. decrements its own `destroyDelay`, returning early while it is positive,
3. if the target is **not** the current one, tail-calls `startDestroyBlock(pos, face)`,
4. otherwise accumulates `destroyProgress += state.getDestroyProgress(...)`, plays the hit sound
   every 4 ticks,
5. and at `destroyProgress >= 1.0F` calls `destroyBlock(pos)` inside `startPrediction`, then sets
   `destroyDelay = 5`.

**So driving a multi-tick break is one call per tick, forever, with no state machine of our own.**
Start, continue, finish, and retarget are all the same call. This mirrors what vanilla's
`Minecraft.continueAttack` does while you hold left-click — including the `player.swing(MAIN_HAND)`
that we also issue for the arm animation.

`destroyDelay = 5` after each completed block is the natural inter-block pacing and sets a hard
floor on throughput: **~5 ticks minimum per block regardless of hardness**.

### Detecting completion

`destroyBlock` runs client-side inside `startPrediction`, so the local block becomes air on the
same tick the break completes. Therefore:

```java
boolean done = level.getBlockState(pos).isAir();
```

This is more robust than `isDestroying()` (false during `destroyDelay`) or `getDestroyStage()`.
It is also *idempotent and re-derivable*, which is what lets the verify sweep (see the mine-order
section of CLAUDE.md) recover from server rejections, falling gravel, and unloaded chunks with one
mechanism instead of four special cases.

### Never switch hotbar slots mid-break

`sameDestroyTarget` calls `destroyingItem.shouldCauseBlockBreakReset(itemstack)`. A slot change
therefore **resets destroy progress to zero**. `HotbarSelector` may only act at phase boundaries
and when `!isDestroying()`.

---

## 2. Placement — `useItemOn` and what the server checks

`useItemOn(LocalPlayer, InteractionHand, BlockHitResult)` is public and returns an
`InteractionResult`. We synthesize a `BlockHitResult` aimed at the face centre of a solid
neighbour, exactly as `bedrock-line-placement`'s `LineLockManager.tryReacharound()` does:

```java
Vec3 hitVec = Vec3.atCenterOf(supportPos).add(normal.getX() * 0.5, normal.getY() * 0.5, normal.getZ() * 0.5);
InteractionResult r = mc.gameMode.useItemOn(player, hand, new BlockHitResult(hitVec, face, supportPos, false));
```

`ServerGamePacketListenerImpl.handleUseItemOn` validates:

- `player.canInteractWithBlock(pos, 1.0)` — distance only, and
- the hit vector is within `1.0000001` of the block centre **on each axis**.

A face centre is exactly `0.5` off centre on one axis and `0.0` on the others, so it passes with
room to spare. No custom packet, no direct `setBlock`, full server validation.

---

## 3. Auto-walk — `MovementInputUpdateEvent` and `Input`

**The class is `net.minecraft.client.player.Input`.** `ClientInput` is 1.21.4+ and does not exist
here. Public mutable fields:

```java
public float leftImpulse, forwardImpulse;
public boolean up, down, left, right, jumping, shiftKeyDown;
```

`KeyboardInput.tick(boolean, float)` overwrites **all** of them from `Options` every tick, so
writing them at the wrong moment is a no-op. The right moment is the NeoForge event.

### Verified ordering inside `LocalPlayer.aiStep()`

```
this.input.tick(this.isMovingSlowly(), sneakFactor);      // fields populated from key state
ClientHooks.onMovementInputUpdate(this, this.input);      // ← MovementInputUpdateEvent fires HERE
...
super.aiStep() → LocalPlayer.serverAiStep():
    this.xxa = this.input.leftImpulse;
    this.zza = this.input.forwardImpulse;
    this.jumping = this.input.jumping;
```

Our writes land between population and consumption, so **they take effect the same tick**.

`net.neoforged.neoforge.client.event.MovementInputUpdateEvent` — `getInput()`, `getEntity()`;
fired on the **game** bus (`NeoForge.EVENT_BUS`), client only.

Set the `up`/`down`/`left`/`right`/`jumping` booleans as well as the impulses — `LocalPlayer` reads
them after the event for sprint and jump logic.

### World direction → local impulses

`Entity.getInputVector` rotates local input into world space as

```
worldX = left * cos(yaw) - forward * sin(yaw)
worldZ = forward * cos(yaw) + left * sin(yaw)
```

`core/WalkVector` inverts that rotation. Sanity checks (both unit-tested): at yaw 0 (facing +Z)
`forward == worldDz` and `left == worldDx`; at yaw 90 (facing −X) `forward == −worldDx`.

---

## 4. Reach, and what the server actually enforces

| Symbol | Fact |
|---|---|
| `Player.blockInteractionRange()` | `getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)`; `DEFAULT_BLOCK_INTERACTION_RANGE = 4.5F` |
| `Player.canInteractWithBlock(BlockPos, double pad)` | `new AABB(pos).distanceToSqr(getEyePosition()) < (range + pad)²` |
| `ServerPlayerGameMode.handleBlockBreakAction` | gates on `canInteractWithBlock(pos, 1.0)` **and nothing else** |
| `ServerGamePacketListenerImpl.handleUseItemOn` | the same, plus the hit-vector check in §2 |

Two consequences drive the whole design:

**We use `pad = 0.0`.** That is strictly tighter than the server's `1.0`, so the mod never sends an
action the server will reject, and never extends reach:

```java
double r = player.blockInteractionRange();
boolean inReach = new AABB(pos).distanceToSqr(player.getEyePosition()) < r * r;
```

Note this is an **AABB** distance, not a face-centre distance. `bedrock-line-placement` uses face
centres, which is fine for a single block ahead of you but would wrongly reject reachable corner
blocks here. Do not copy that part.

**The server never checks look direction.** Neither breaking nor placing validates where the player
is looking. So this mod *never rotates the camera* — which in turn means any camera rotation is
unambiguously the human, making mouse-look a false-positive-free dead-man's switch (§6).

---

## 5. Hotbar

`Inventory.selected` is a `public int`. `ensureHasSentCarriedItem()` is private but is called at
the head of `continueDestroyBlock`, `useItemOn`, and `useItem`, and syncs the selected slot to the
server. **So switching hotbar slots is a single field write** — no packet to send by hand.

Subject to the mid-break restriction in §1.

---

## 6. Reading player input — `Options`

All `public final KeyMapping`: `keyUp`, `keyDown`, `keyLeft`, `keyRight`, `keyJump`, `keyShift`,
`keySprint`, `keyAttack`, `keyUse`, `keyPickItem`, `keyDrop`, `keyInventory`.

`keyShift` and `keySprint` are `ToggleKeyMapping`s, so `isDown()` correctly respects toggle-crouch
and toggle-sprint settings.

Because this mod writes `Input` fields directly and **never** touches `KeyMapping` state,
`mc.options.key*.isDown()` stays an uncontaminated read of what the human is doing. That is what
makes the dead-man's switch trustworthy — we are not reading back our own writes.

**Unverified:** `Options.keyHotbarSlots` — field name and type not confirmed. If it turns out to
differ, drop hotbar keys from `InputWatch`; the `inventory.selected != expectedSlot` check already
covers the same intent.

---

## 7. Client commands — `RegisterClientCommandsEvent`

`net.neoforged.neoforge.client.event.RegisterClientCommandsEvent` — `getDispatcher()` returns a
`CommandDispatcher<CommandSourceStack>`, plus `getBuildContext()`.

Fired on the **game** bus (`NeoForge.EVENT_BUS`, *not* the mod bus) from
`ClientCommandHandler.mergeServerCommands` during `ClientPlayerNetworkEvent.LoggingIn` — so it
re-fires on every world join.

### Gotcha: never gate on permissions

`ClientCommandHandler.getSource()` builds its `ClientCommandSourceStack` with
`player.getPermissionLevel()`, which is **0 on any normal server**. Writing
`.requires(src -> src.hasPermission(2))` therefore makes the command silently not exist. Use
`.requires(src -> true)` or omit `requires` entirely.

Feedback: `ClientCommandSourceStack` overrides `sendSuccess` to
`Minecraft.getInstance().player.sendSystemMessage(...)`, so it is purely local — nothing reaches
the server.

---

## 8. Misc

| Symbol | Fact |
|---|---|
| `Direction.fromYRot(double)` | `from2DDataValue(Mth.floor(y / 90 + 0.5) & 3)`; 2D order is **SOUTH 0, WEST 1, NORTH 2, EAST 3**. `core/Facing` declares its constants in that order so the mapping is an ordinal lookup, and converts by *name* at the bridge. |
| `Direction.getNearest(Vec3)` | Used to pick a natural-looking break face. The server ignores it. |
| `Level.isLoaded(BlockPos)` | Gate every world read; a long mall can cross into ungenerated chunks. |
| `BlockState.canBeReplaced()` | Placement precondition. |
| `CollisionGetter.isUnobstructed(BlockState, BlockPos, CollisionContext)` | Default method on `Level`. This alone handles "don't place a block inside the player". |
| `ClientTickEvent.Pre` / `.Post` | Static nested classes on the NeoForge event; we use `.Post`. |
| `Player.getEyePosition()` | Eye height 1.62 standing, **1.27 sneaking** — see the ceiling-reach margin note in CLAUDE.md. |
