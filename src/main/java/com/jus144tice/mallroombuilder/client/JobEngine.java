/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.client;

import com.jus144tice.mallroombuilder.Config;
import com.jus144tice.mallroombuilder.MallRoomBuilder;
import com.jus144tice.mallroombuilder.core.FillPlan;
import com.jus144tice.mallroombuilder.core.GridPos;
import com.jus144tice.mallroombuilder.core.MallAnchor;
import com.jus144tice.mallroombuilder.core.MallLayout;
import com.jus144tice.mallroombuilder.core.MallSpec;
import com.jus144tice.mallroombuilder.core.QueueCursor;
import com.jus144tice.mallroombuilder.core.Surface;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The job state machine. Owns every world read and write.
 *
 * <pre>
 *   IDLE -&gt; ARMING -&gt; CARVING -&gt; FILLING -&gt; IDLE
 *                        |           ^
 *                        |           v
 *                        |    PAUSED_NO_MATERIAL
 *                        +--&gt; IDLE   (nothing to fill)
 *   any state -&gt; IDLE on abort
 * </pre>
 *
 * <p><strong>Carving is the default; filling is opt-in.</strong> A room is 250 blocks of mining and
 * a job places nothing unless the command named a surface and a hotbar slot. When it does, the fill
 * phase runs after the carve, taking each surface's material from its assigned slot. A fill-only job
 * skips carving entirely, for finishing something already dug.</p>
 *
 * <p>The one placement that happens regardless is a backfill into framing that has gone missing.</p>
 *
 * <p><strong>Why ARMING exists.</strong> The player pressed Enter to send the command, so the chat
 * screen is closing and keys are frequently still down. Starting immediately would trip the
 * dead-man's switch on tick one, every time.</p>
 *
 * <p><strong>Progress is re-derived, never assumed.</strong> A cell is carved iff the world says it
 * is air. That single choice is what lets one verify sweep recover from server rejections, falling
 * gravel, blocks placed by other players, and chunks that were not loaded — instead of four separate
 * special cases.</p>
 */
public final class JobEngine {

    public static final JobEngine INSTANCE = new JobEngine();

    public enum State {
        IDLE,
        ARMING,
        CARVING,
        FILLING,
        PAUSED_NO_MATERIAL
    }

    private State state = State.IDLE;
    private MallLayout layout;
    private QueueCursor cursor;
    private GridPos currentTarget;

    private final Set<GridPos> backfill = new LinkedHashSet<>();
    private final List<MallLayout.FillCell> fillQueue = new ArrayList<>();
    private int filled;
    private int fillStallTicks;
    private Surface pausedSurface;
    private int blockTicks;
    private int armTicks;
    private int stepOffTicks;
    private int verifySweeps;
    private int framingScanTicks;
    private int placeCooldown;
    private int wrongToolTicks;
    private int carved;
    private int backfilled;
    private boolean warnedNoBackfillMaterial;

    private JobEngine() {}

    public State state() {
        return state;
    }

    public boolean isRunning() {
        return state != State.IDLE;
    }

    // --- Starting and stopping ---------------------------------------------

    /**
     * Validates and begins a job.
     *
     * @return an error message, or null on success
     */
    public String start(Minecraft mc, MallSpec spec) {
        if (!Config.enabled()) {
            return "Mall Room Builder is disabled in the config.";
        }
        if (isRunning()) {
            return "A job is already running. Use /mallroom stop first.";
        }
        if (!spec.doesSomething()) {
            return "Nothing to do — name at least one surface and slot to fill.";
        }
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null) {
            return "No player in world.";
        }

        MallAnchor anchor = anchorFor(player);
        MallLayout candidate = new MallLayout(anchor, spec);

        int total = candidate.counts().minedTotal();
        if (total > Config.maxQueuedBlocks()) {
            return "That job is " + total + " blocks, over the maxQueuedBlocks limit of " + Config.maxQueuedBlocks()
                    + ".";
        }
        String unloaded = checkLoaded(level, candidate);
        if (unloaded != null) {
            return unloaded;
        }

        this.layout = candidate;
        this.cursor = new QueueCursor(spec.carve() ? candidate.mineOrder() : List.of(), Config.maxVerifySweeps());
        this.fillQueue.clear();
        this.fillQueue.addAll(candidate.fillOrder());
        this.filled = 0;
        this.fillStallTicks = 0;
        this.pausedSurface = null;
        this.state = State.ARMING;
        this.currentTarget = null;
        this.backfill.clear();
        this.blockTicks = 0;
        this.armTicks = 0;
        this.stepOffTicks = 0;
        this.verifySweeps = 0;
        this.framingScanTicks = 0;
        this.placeCooldown = 0;
        this.wrongToolTicks = 0;
        this.carved = 0;
        this.backfilled = 0;
        this.warnedNoBackfillMaterial = false;

        HotbarSelector.remember(player);
        String what = spec.kind() == MallSpec.Kind.SPINE
                ? "spine segment " + spec.spineLength() + " long (" + spec.modeName() + ")"
                : spec.roomCount() + " room(s) (" + spec.modeName() + ")";
        StringBuilder work = new StringBuilder();
        if (spec.carve()) {
            work.append(total).append(" to mine");
        }
        if (spec.fills()) {
            if (work.length() > 0) {
                work.append(", ");
            }
            work.append(candidate.counts().placedTotal())
                    .append(" to place (")
                    .append(spec.fill())
                    .append(')');
        }
        if (candidate.counts().framingCount() > 0 && spec.carve()) {
            work.append(", ").append(candidate.counts().framingCount()).append(" framing left standing");
        }
        say(
                mc,
                ChatFormatting.GRAY,
                "Planning " + what + " " + anchor.facing().name().toLowerCase() + ": " + work
                        + ". Release all keys to begin.");
        return null;
    }

    /** Stops cleanly, leaving no half-broken block and no stuck cracking overlay. */
    public void abort(Minecraft mc, String reason) {
        if (state == State.IDLE) {
            return;
        }
        MineDriver.cancel(mc);
        AutoWalk.stop();
        LocalPlayer player = mc.player;
        if (player != null) {
            HotbarSelector.restore(player);
        } else {
            HotbarSelector.forget();
        }
        say(mc, ChatFormatting.YELLOW, "Stopped (" + reason + "). " + progressLine());
        clear();
    }

    private void finish(Minecraft mc) {
        AutoWalk.stop();
        MineDriver.cancel(mc);
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player != null) {
            HotbarSelector.restore(player);
        }
        int leftoverCarve = cursor == null ? 0 : cursor.remaining();
        int leftoverFill = level == null ? 0 : pendingFillCount(level);
        int leftover = leftoverCarve + leftoverFill;
        if (leftover > 0) {
            say(
                    mc,
                    ChatFormatting.YELLOW,
                    "Finished with " + leftover + " block(s) unreachable. " + progressLine()
                            + " Stand in the same spot and run it again to pick up the rest.");
        } else {
            say(mc, ChatFormatting.GREEN, "Done. " + progressLine());
        }
        clear();
    }

    private void clear() {
        state = State.IDLE;
        currentTarget = null;
        layout = null;
        cursor = null;
        backfill.clear();
        fillQueue.clear();
        pausedSurface = null;
    }

    // --- The tick ----------------------------------------------------------

    public void tick(Minecraft mc) {
        if (state == State.IDLE) {
            return;
        }
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null) {
            abort(mc, "left the world");
            return;
        }

        if (state == State.ARMING) {
            tickArming(mc);
            return;
        }

        // Reconcile a tool swap before the dead-man's switch sees it, or a mod that auto-replaces a
        // broken pickaxe would read as the player grabbing the controls.
        reconcileToolSwap(player, level);

        String tripped = InputWatch.tripped(mc);
        if (tripped != null) {
            abort(mc, tripped);
            return;
        }
        String unsafe = safetyGate(player);
        if (unsafe != null) {
            abort(mc, unsafe);
            return;
        }

        AutoWalk.tick(player);
        switch (state) {
            case CARVING -> tickCarving(mc, player, level);
            case FILLING, PAUSED_NO_MATERIAL -> tickFilling(mc, player, level);
            default -> {}
        }
    }

    private void tickArming(Minecraft mc) {
        if (InputWatch.allReleased(mc)) {
            InputWatch.arm(mc);
            LocalPlayer player = mc.player;
            if (player != null) {
                // Phase boundary: the only safe moment to touch the hotbar, since a slot change
                // mid-break resets destroy progress to zero.
                HotbarSelector.selectBestTool(player, Blocks.STONE.defaultBlockState());
                HotbarSelector.latchMiningSlot(player);
            }
            state = layout.spec().carve() ? State.CARVING : State.FILLING;
            say(
                    mc,
                    ChatFormatting.GRAY,
                    (state == State.CARVING ? "Carving." : "Filling.") + " Touch any key or move the mouse to stop.");
            return;
        }
        if (++armTicks > Config.armGraceTicks()) {
            abort(mc, "you were still holding something");
        }
    }

    // --- Carving -----------------------------------------------------------

    private void tickCarving(Minecraft mc, LocalPlayer player, ClientLevel level) {
        if (placeCooldown > 0) {
            placeCooldown--;
        }
        watchFraming(level);

        // Completion is a world read, not a bookkeeping assumption.
        if (currentTarget != null && MineDriver.isCarved(level, MineDriver.toBlockPos(currentTarget))) {
            cursor.complete(currentTarget);
            carved++;
            currentTarget = null;
            blockTicks = 0;
        }

        // Backfill only ever happens between blocks. Swapping hotbar slots mid-break would reset
        // destroy progress, so this must never run while a break is in flight.
        if (currentTarget == null && tryBackfill(mc, player, level)) {
            return;
        }

        if (currentTarget == null) {
            // A backfill may have left us holding cobblestone; get the pickaxe back before mining.
            if (!HotbarSelector.onMiningSlot(player)) {
                HotbarSelector.selectMiningSlot(player);
                return;
            }
            currentTarget = selectCarveTarget(player, level);
            blockTicks = 0;
        }

        if (currentTarget == null) {
            if (!cursor.hasPending() && !cursor.hasDeferred()) {
                if (verifyCarve(level)) {
                    beginFillOrFinish(mc);
                }
                return;
            }
            steerOrStall(mc);
            return;
        }

        BlockPos targetPos = MineDriver.toBlockPos(currentTarget);

        // Hold fire if the held tool would destroy this block without dropping it. The whole point
        // of the mod is that you keep the material, so this pauses rather than mining -- which also
        // gives a tool-replacement mod its window to swap a fresh pickaxe in after one breaks.
        if (Config.abortOnWrongTool() && !MineDriver.canHarvest(player, level, targetPos)) {
            AutoWalk.stop();
            MineDriver.cancel(mc);
            if (++wrongToolTicks > Config.toolGraceTicks()) {
                abort(
                        mc,
                        "no tool for " + MineDriver.blockName(level, targetPos) + " — it would break without dropping");
            }
            return;
        }
        wrongToolTicks = 0;

        AutoWalk.stop();
        MineDriver.drive(mc, player, targetPos);

        if (++blockTicks > Config.blockTimeoutTicks()) {
            MallRoomBuilder.debug("giving up on " + currentTarget + " after " + blockTicks + " ticks");
            MineDriver.cancel(mc);
            cursor.defer(currentTarget);
            currentTarget = null;
            blockTicks = 0;
        }
    }

    /**
     * Picks the nearest workable cell in reach.
     *
     * <p>The cell the player is standing on is skipped — that is the "pedestal" rule that keeps them
     * from digging the floor out from under themselves. It is deferred and collected on a later
     * sweep, by which time they have walked forward. The exception is the last floor cells, where
     * there is nowhere left to walk: after {@code stepOffTimeoutTicks} the engine mines it anyway and
     * the player drops exactly one block onto untouched stone.</p>
     */
    private GridPos selectCarveTarget(LocalPlayer player, ClientLevel level) {
        BlockPos standing = player.blockPosition().below();
        boolean allowStepOff = stepOffTicks > Config.stepOffTimeoutTicks();

        GridPos chosen = cursor.select(p -> canCarve(player, level, p, standing, allowStepOff));
        if (chosen != null) {
            stepOffTicks = 0;
            return chosen;
        }
        GridPos underfoot =
                cursor.select(p -> p.x() == standing.getX() && p.y() == standing.getY() && p.z() == standing.getZ());
        if (underfoot != null) {
            stepOffTicks++;
        }
        return null;
    }

    private boolean canCarve(
            LocalPlayer player, ClientLevel level, GridPos cell, BlockPos standing, boolean allowStepOff) {
        BlockPos pos = MineDriver.toBlockPos(cell);
        if (!level.isLoaded(pos)) {
            return false;
        }
        if (MineDriver.isCarved(level, pos)) {
            return false;
        }
        if (!allowStepOff && pos.equals(standing)) {
            return false;
        }
        if (!MineDriver.inReach(player, pos)) {
            return false;
        }
        return !touchesLiquid(level, pos);
    }

    /** Re-scans the whole volume. @return true when nothing is left to carve. */
    private boolean verifyCarve(ClientLevel level) {
        List<GridPos> unfinished = new ArrayList<>();
        for (GridPos p : layout.carve()) {
            if (!MineDriver.isCarved(level, MineDriver.toBlockPos(p))) {
                unfinished.add(p);
            }
        }
        if (unfinished.isEmpty()) {
            return true;
        }
        if (verifySweeps >= Config.maxVerifySweeps()) {
            MallRoomBuilder.debug("carve verify gave up with " + unfinished.size() + " left");
            return true;
        }
        verifySweeps++;
        cursor.requeue(unfinished);
        MallRoomBuilder.debug("carve sweep " + verifySweeps + ": " + unfinished.size() + " cells re-queued");
        return false;
    }

    // --- Framing backfill --------------------------------------------------

    /**
     * Periodically checks that the framing is still standing.
     *
     * <p>The mod never mines framing, but gravel falls, mobs happen, and a stray break happens. This
     * is the one thing that ever puts a block back, and it is exactly the case the player described:
     * cobblestone is only for repairing framing that went missing.</p>
     */
    private void watchFraming(ClientLevel level) {
        if (!Config.autoBackfillFraming() || layout == null) {
            return;
        }
        if (++framingScanTicks < Config.framingScanInterval()) {
            return;
        }
        framingScanTicks = 0;
        for (GridPos p : layout.framing()) {
            BlockPos pos = MineDriver.toBlockPos(p);
            if (level.isLoaded(pos) && level.getBlockState(pos).isAir()) {
                if (backfill.add(p)) {
                    MallRoomBuilder.debug("framing breach at " + p);
                }
            }
        }
    }

    /**
     * Places one backfill block if anything is missing and reachable.
     *
     * @return true if this tick was consumed by backfill work
     */
    private boolean tryBackfill(Minecraft mc, LocalPlayer player, ClientLevel level) {
        if (backfill.isEmpty() || placeCooldown > 0) {
            return false;
        }

        var buildState = HotbarSelector.backfillBlock().defaultBlockState();
        GridPos target = null;
        PlaceDriver.Support support = null;
        for (GridPos p : backfill) {
            BlockPos pos = MineDriver.toBlockPos(p);
            if (!level.getBlockState(pos).isAir()) {
                target = p; // already fixed itself
                support = null;
                break;
            }
            if (!PlaceDriver.isPlaceable(level, player, pos, buildState)) {
                continue;
            }
            PlaceDriver.Support candidate = PlaceDriver.findSupport(player, level, pos);
            if (candidate != null) {
                target = p;
                support = candidate;
                break;
            }
        }
        if (target == null) {
            return false;
        }
        if (support == null) {
            backfill.remove(target);
            return false;
        }

        InteractionHand hand = HotbarSelector.backfillHand(player);
        if (hand == null) {
            if (!HotbarSelector.selectBackfillSlot(player)) {
                if (!warnedNoBackfillMaterial) {
                    warnedNoBackfillMaterial = true;
                    say(
                            mc,
                            ChatFormatting.YELLOW,
                            "Framing is missing but you have no " + Config.backfillBlock() + " to repair it with.");
                }
                backfill.clear();
                return false;
            }
            return true; // slot switched this tick; place on the next one
        }

        if (PlaceDriver.place(mc, player, support, hand)) {
            backfill.remove(target);
            backfilled++;
            placeCooldown = Config.placeCooldownTicks();
            MallRoomBuilder.debug("backfilled framing at " + target);
        }
        return true;
    }

    // --- Shared helpers ----------------------------------------------------

    private void steerOrStall(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != null) {
            retireAlreadyCarved(level);
        }

        GridPos next = cursor.peek();
        if (next != null) {
            if (Config.autoWalkEnabled()) {
                AutoWalk.steerTo(next);
            } else {
                abort(mc, "nothing in reach and auto-walk is disabled");
            }
            return;
        }

        if (cursor.sweep()) {
            return;
        }

        MallRoomBuilder.debug("sweep budget exhausted with " + cursor.remaining() + " outstanding");
        AutoWalk.stop();
        beginFillOrFinish(mc);
    }

    // --- Filling -----------------------------------------------------------

    private void beginFillOrFinish(Minecraft mc) {
        if (state == State.CARVING && layout != null && layout.spec().fills()) {
            MineDriver.cancel(mc);
            AutoWalk.stop();
            state = State.FILLING;
            fillStallTicks = 0;
            say(
                    mc,
                    ChatFormatting.GRAY,
                    "Carved " + carved + ". Filling " + layout.counts().placedTotal() + ".");
            return;
        }
        finish(mc);
    }

    /**
     * Places one block per cadence tick.
     *
     * <p>A cell counts as needing fill iff it is currently <em>replaceable</em>. That single rule
     * covers three cases without special-casing any of them: an uncarved recess is solid, so it is
     * skipped rather than half-filled; an already-filled cell is solid, so re-running a job is a
     * no-op; and only genuinely open recesses get material.</p>
     *
     * <p>Material comes from the hotbar slot the plan assigns to that cell's surface. Slots are only
     * ever switched between placements, never mid-break — by this point there is no break in flight
     * anyway, since carving is done.</p>
     */
    private void tickFilling(Minecraft mc, LocalPlayer player, ClientLevel level) {
        watchFraming(level);
        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        MallLayout.FillCell target = null;
        MallLayout.FillCell anyPending = null;
        PlaceDriver.Support support = null;

        for (MallLayout.FillCell cell : fillQueue) {
            BlockPos pos = MineDriver.toBlockPos(cell.pos());
            if (!level.isLoaded(pos) || !level.getBlockState(pos).canBeReplaced()) {
                continue; // already filled, or never carved
            }
            if (anyPending == null) {
                anyPending = cell;
            }
            Block block = blockFor(player, cell.surface());
            if (block == null) {
                continue; // that surface's slot is empty; handled once it is the only thing left
            }
            if (!PlaceDriver.isPlaceable(level, player, pos, block.defaultBlockState())) {
                continue;
            }
            PlaceDriver.Support candidate = PlaceDriver.findSupport(player, level, pos);
            if (candidate != null) {
                target = cell;
                support = candidate;
                break;
            }
        }

        if (anyPending == null) {
            finish(mc);
            return;
        }

        if (target == null) {
            if (blockFor(player, anyPending.surface()) == null) {
                pauseForMaterial(mc, anyPending.surface());
                return;
            }
            resume(mc);
            AutoWalk.steerTo(anyPending.pos());
            if (++fillStallTicks > Config.blockTimeoutTicks()) {
                MallRoomBuilder.debug("fill stalled with " + pendingFillCount(level) + " cells left");
                AutoWalk.stop();
                finish(mc);
            }
            return;
        }

        resume(mc);
        fillStallTicks = 0;
        AutoWalk.stop();

        int slot = layout.spec().fill().inventoryIndex(target.surface());
        if (!HotbarSelector.onSlot(player, slot)) {
            HotbarSelector.selectSlot(player, slot);
            return; // let the swap settle for a tick before placing
        }

        if (PlaceDriver.place(mc, player, support, InteractionHand.MAIN_HAND)) {
            filled++;
            placeCooldown = Config.placeCooldownTicks();
        } else {
            // Server refused, or the stack ran out between the check and the click.
            if (++fillStallTicks > Config.blockTimeoutTicks()) {
                AutoWalk.stop();
                finish(mc);
            }
        }
    }

    /** The block a surface's assigned hotbar slot currently supplies, or null if it has none. */
    private Block blockFor(LocalPlayer player, Surface surface) {
        FillPlan plan = layout.spec().fill();
        if (!plan.covers(surface)) {
            return null;
        }
        ItemStack stack = player.getInventory().getItem(plan.inventoryIndex(surface));
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return null;
        }
        return blockItem.getBlock();
    }

    private void pauseForMaterial(Minecraft mc, Surface surface) {
        AutoWalk.stop();
        if (state != State.PAUSED_NO_MATERIAL || pausedSurface != surface) {
            FillPlan plan = layout.spec().fill();
            say(
                    mc,
                    ChatFormatting.YELLOW,
                    "Out of material for the " + surface.key() + " (hotbar slot " + plan.slot(surface)
                            + "). Restock it to resume, or /mallroom stop.");
        }
        state = State.PAUSED_NO_MATERIAL;
        pausedSurface = surface;
    }

    private void resume(Minecraft mc) {
        if (state == State.PAUSED_NO_MATERIAL) {
            state = State.FILLING;
            pausedSurface = null;
            say(mc, ChatFormatting.GRAY, "Resuming.");
        }
    }

    private int pendingFillCount(ClientLevel level) {
        int n = 0;
        for (MallLayout.FillCell cell : fillQueue) {
            BlockPos pos = MineDriver.toBlockPos(cell.pos());
            if (level.isLoaded(pos) && level.getBlockState(pos).canBeReplaced()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Retires outstanding cells that are already air.
     *
     * <p>Without this the queue can never drain: a cell that is already air is never a valid target,
     * yet stays outstanding, and the steering target would sit on one of them forever. Pre-existing
     * air — a cave clipping the room, or re-running a job over finished work — lands here.</p>
     *
     * <p>Only called when nothing workable is in reach, so the full scan is free.</p>
     */
    private void retireAlreadyCarved(ClientLevel level) {
        for (GridPos p : cursor.outstanding()) {
            BlockPos pos = MineDriver.toBlockPos(p);
            if (level.isLoaded(pos) && MineDriver.isCarved(level, pos)) {
                cursor.complete(p);
            }
        }
    }

    /**
     * Accepts a hotbar change that is a tool swap rather than the player taking over.
     *
     * <p>A mod that replaces a broken pickaxe usually restocks the same slot, which is invisible
     * here — but some switch to another slot instead, and that would trip the dead-man's switch mid
     * job. So: if the newly selected item can still harvest what we are working on, re-latch and
     * carry on. If it cannot, the switch stands and {@link InputWatch} aborts as usual.</p>
     *
     * <p>The trade is that scrolling from one pickaxe to another no longer aborts. Mouse-look and
     * WASD are the strong takeover signals; the slot check was always the weak one.</p>
     */
    private void reconcileToolSwap(LocalPlayer player, ClientLevel level) {
        if (!Config.allowToolSwap()) {
            return;
        }
        int selected = player.getInventory().selected;
        if (selected == InputWatch.expectedSlot()) {
            return;
        }
        BlockPos reference = currentTarget == null ? null : MineDriver.toBlockPos(currentTarget);
        boolean stillUsable = reference != null
                ? MineDriver.canHarvest(player, level, reference)
                : player.getInventory().getSelected().getDestroySpeed(Blocks.STONE.defaultBlockState()) > 1.0f;
        if (stillUsable) {
            MallRoomBuilder.debug("accepted tool swap to slot " + selected);
            InputWatch.setExpectedSlot(selected);
            HotbarSelector.latchMiningSlot(player);
        }
    }

    private String safetyGate(LocalPlayer player) {
        if (Config.abortOnLowHealth() && player.getHealth() < Config.minHealth()) {
            return "health too low";
        }
        if (player.isInLava()) {
            return "you are in lava";
        }
        return null;
    }

    private static boolean touchesLiquid(ClientLevel level, BlockPos pos) {
        if (!Config.abortOnLiquid()) {
            return false;
        }
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (level.isLoaded(n) && !level.getFluidState(n).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String checkLoaded(ClientLevel level, MallLayout candidate) {
        for (GridPos p : candidate.carve()) {
            if (!level.isLoaded(MineDriver.toBlockPos(p))) {
                return "Part of that room is in an unloaded chunk. Move closer.";
            }
        }
        return null;
    }

    /**
     * Snapshots the anchor from the player's position and facing alone.
     *
     * <p>Nothing is read from the world. The job always starts at the very next block ahead, so the
     * same standing position always describes the same volume — which is what lets a half-finished
     * room be completed by simply standing in the same spot and running the command again. An
     * earlier version scanned forward for the first solid block, and that broke on exactly that
     * case: the scan sailed through the opening and anchored the room somewhere else.</p>
     */
    public static MallAnchor anchorFor(LocalPlayer player) {
        return MallAnchor.of(
                Mth.floor(player.getX()), Mth.floor(player.getY()), Mth.floor(player.getZ()), player.getYRot());
    }

    // --- Status ------------------------------------------------------------

    public String progressLine() {
        StringBuilder sb = new StringBuilder("carved ").append(carved);
        if (filled > 0) {
            sb.append(", placed ").append(filled);
        }
        if (backfilled > 0) {
            sb.append(", backfilled ").append(backfilled).append(" framing");
        }
        if (state == State.CARVING && cursor != null) {
            sb.append("; ").append(cursor.done()).append('/').append(cursor.total());
        }
        return sb.append('.').toString();
    }

    /** One-line status for the command and the HUD. */
    public String statusLine() {
        return switch (state) {
            case IDLE -> "idle";
            case ARMING -> "waiting for you to release all keys";
            case CARVING -> wrongToolTicks > 0
                    ? "waiting for a tool that can harvest this block"
                    : "carving " + queueFraction();
            case FILLING -> "filling " + filled + "/"
                    + (layout == null ? 0 : layout.counts().placedTotal());
            case PAUSED_NO_MATERIAL -> "paused: out of material for the "
                    + (pausedSurface == null ? "fill" : pausedSurface.key());
        };
    }

    private String queueFraction() {
        if (cursor == null) {
            return "";
        }
        String deferredNote = cursor.deferredCount() > 0 ? " (" + cursor.deferredCount() + " deferred)" : "";
        String repairNote = backfill.isEmpty() ? "" : " [" + backfill.size() + " to backfill]";
        return cursor.done() + "/" + cursor.total() + deferredNote + repairNote;
    }

    public MallLayout layout() {
        return layout;
    }

    private static void say(Minecraft mc, ChatFormatting colour, String message) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("[Mall] " + message).withStyle(colour));
        }
    }
}
