/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.client;

import com.jus144tice.mallroombuilder.Config;
import com.jus144tice.mallroombuilder.MallRoomBuilder;
import com.jus144tice.mallroombuilder.core.GridPos;
import com.jus144tice.mallroombuilder.core.MallAnchor;
import com.jus144tice.mallroombuilder.core.MallLayout;
import com.jus144tice.mallroombuilder.core.MallSpec;
import com.jus144tice.mallroombuilder.core.QueueCursor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The job state machine. Owns every world read and write; everything it decides is computed from
 * the pure {@code core} layer.
 *
 * <pre>
 *   IDLE -&gt; ARMING -&gt; CARVING -&gt; BUILDING -&gt; IDLE
 *                        ^           |
 *                        |           v
 *                        +--- PAUSED_NO_MATERIAL
 *   any state -&gt; IDLE on abort
 * </pre>
 *
 * <p><strong>Why ARMING exists.</strong> The player pressed Enter to send the command, so the chat
 * screen is closing and keys are frequently still down. Starting immediately would trip the
 * dead-man's switch on tick one, every time. The engine waits for one clean tick with everything
 * released, then latches the look baseline.</p>
 *
 * <p><strong>Progress is re-derived, never assumed.</strong> A cell is carved iff the world says it
 * is air, and skinned iff the world says it is the build block. That single choice is what lets one
 * verify sweep recover from server rejections, falling gravel, blocks placed by other players, and
 * chunks that were not loaded — instead of four separate special cases.</p>
 */
public final class JobEngine {

    public static final JobEngine INSTANCE = new JobEngine();

    public enum State {
        IDLE,
        ARMING,
        CARVING,
        BUILDING,
        PAUSED_NO_MATERIAL
    }

    private State state = State.IDLE;
    private MallLayout layout;
    private QueueCursor cursor;
    private GridPos currentTarget;

    private int blockTicks;
    private int placeCooldown;
    private int armTicks;
    private int stepOffTicks;
    private int verifySweeps;
    private int carved;
    private int placed;
    private boolean warnedNoMaterial;

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
        this.cursor = new QueueCursor(candidate.mineOrder(), Config.maxVerifySweeps());
        this.state = State.ARMING;
        this.currentTarget = null;
        this.blockTicks = 0;
        this.placeCooldown = 0;
        this.armTicks = 0;
        this.stepOffTicks = 0;
        this.verifySweeps = 0;
        this.carved = 0;
        this.placed = 0;
        this.warnedNoMaterial = false;

        HotbarSelector.remember(player);
        say(
                mc,
                ChatFormatting.GRAY,
                "Planning " + spec.roomCount() + " room(s) to the "
                        + anchor.facing().name().toLowerCase() + ": "
                        + candidate.counts().minedTotal() + " to mine, "
                        + candidate.counts().skinCount() + " to place. Release all keys to begin.");
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
        state = State.IDLE;
        currentTarget = null;
        layout = null;
        cursor = null;
    }

    private void finish(Minecraft mc) {
        LocalPlayer player = mc.player;
        AutoWalk.stop();
        MineDriver.cancel(mc);
        if (player != null) {
            HotbarSelector.restore(player);
        }
        int leftover = cursor == null ? 0 : cursor.remaining();
        if (leftover > 0) {
            say(mc, ChatFormatting.YELLOW, "Finished with " + leftover + " block(s) unreachable. " + progressLine());
        } else {
            say(mc, ChatFormatting.GREEN, "Mall complete. " + progressLine());
        }
        state = State.IDLE;
        currentTarget = null;
        layout = null;
        cursor = null;
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
            case BUILDING, PAUSED_NO_MATERIAL -> tickBuilding(mc, player, level);
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
                HotbarSelector.selectBestTool(player, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
            state = State.CARVING;
            say(mc, ChatFormatting.GRAY, "Carving. Touch any key or move the mouse to stop.");
            return;
        }
        if (++armTicks > Config.armGraceTicks()) {
            abort(mc, "you were still holding something");
        }
    }

    // --- Carving -----------------------------------------------------------

    private void tickCarving(Minecraft mc, LocalPlayer player, ClientLevel level) {
        // Completion is a world read, not a bookkeeping assumption.
        if (currentTarget != null && MineDriver.isCarved(level, MineDriver.toBlockPos(currentTarget))) {
            cursor.complete(currentTarget);
            carved++;
            currentTarget = null;
            blockTicks = 0;
        }

        if (currentTarget == null) {
            currentTarget = selectCarveTarget(player, level);
            blockTicks = 0;
        }

        if (currentTarget == null) {
            if (!cursor.hasPending() && !cursor.hasDeferred()) {
                if (verifyCarve(level)) {
                    beginBuildPhase(mc, player);
                }
                return;
            }
            steerOrStall(mc);
            return;
        }

        AutoWalk.stop();
        BlockPos pos = MineDriver.toBlockPos(currentTarget);
        MineDriver.drive(mc, player, pos);

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
     * <p>The cell the player is standing on is skipped — that is the "pedestal" rule that keeps the
     * player from digging the floor out from under themselves. It is deferred and collected on a
     * later sweep, by which time they have walked forward. The one exception is the very last floor
     * block of the mall, where there is nowhere left to walk: after {@code stepOffTimeoutTicks} the
     * engine mines it anyway and the player drops exactly one block onto untouched stone.</p>
     */
    private GridPos selectCarveTarget(LocalPlayer player, ClientLevel level) {
        BlockPos standing = player.blockPosition().below();
        boolean allowStepOff = stepOffTicks > Config.stepOffTimeoutTicks();

        GridPos chosen = cursor.select(p -> canCarve(player, level, p, standing, allowStepOff));
        if (chosen != null) {
            stepOffTicks = 0;
            return chosen;
        }
        // Nothing workable. If the only thing left in reach is the block underfoot, start the clock.
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
            return false; // already air; the completion pass will retire it
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
        for (GridPos p : layout.air()) {
            if (!MineDriver.isCarved(level, MineDriver.toBlockPos(p))) {
                unfinished.add(p);
            }
        }
        for (GridPos p : layout.skin()) {
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

    // --- Building ----------------------------------------------------------

    private void beginBuildPhase(Minecraft mc, LocalPlayer player) {
        cursor = new QueueCursor(layout.buildOrder(), Config.maxVerifySweeps());
        verifySweeps = 0;
        placeCooldown = 0;
        currentTarget = null;
        state = State.BUILDING;
        say(
                mc,
                ChatFormatting.GRAY,
                "Carved " + carved + " blocks. Placing " + layout.counts().skinCount() + ".");
    }

    private void tickBuilding(Minecraft mc, LocalPlayer player, ClientLevel level) {
        if (!HotbarSelector.ensureBuildBlock(player)) {
            if (!Config.pauseWhenOutOfMaterial()) {
                abort(mc, "out of " + Config.buildBlock());
                return;
            }
            if (!warnedNoMaterial) {
                warnedNoMaterial = true;
                say(mc, ChatFormatting.YELLOW, "Out of " + Config.buildBlock() + " — restock to resume.");
            }
            state = State.PAUSED_NO_MATERIAL;
            AutoWalk.stop();
            return;
        }
        if (state == State.PAUSED_NO_MATERIAL) {
            state = State.BUILDING;
            warnedNoMaterial = false;
            say(mc, ChatFormatting.GRAY, "Resuming.");
        }

        if (placeCooldown > 0) {
            placeCooldown--;
            return;
        }

        BlockState buildState = HotbarSelector.buildBlock().defaultBlockState();
        GridPos target = cursor.select(p -> {
            BlockPos pos = MineDriver.toBlockPos(p);
            return PlaceDriver.isPlaceable(level, player, pos, buildState)
                    && PlaceDriver.findSupport(player, level, pos) != null;
        });

        if (target == null) {
            if (!cursor.hasPending() && !cursor.hasDeferred()) {
                if (verifyBuild(level)) {
                    finish(mc);
                }
                return;
            }
            steerOrStall(mc);
            return;
        }

        AutoWalk.stop();
        BlockPos pos = MineDriver.toBlockPos(target);
        PlaceDriver.Support support = PlaceDriver.findSupport(player, level, pos);
        if (support == null) {
            cursor.defer(target);
            return;
        }
        if (PlaceDriver.place(mc, player, support)) {
            cursor.complete(target);
            placed++;
            placeCooldown = Config.placeCooldownTicks();
        } else {
            cursor.defer(target);
        }
    }

    /** Re-scans the skin. @return true when every skin cell holds the build block. */
    private boolean verifyBuild(ClientLevel level) {
        List<GridPos> unfinished = new ArrayList<>();
        for (GridPos p : layout.skin()) {
            BlockState actual = level.getBlockState(MineDriver.toBlockPos(p));
            if (!actual.is(HotbarSelector.buildBlock())) {
                unfinished.add(p);
            }
        }
        if (unfinished.isEmpty()) {
            return true;
        }
        if (verifySweeps >= Config.maxVerifySweeps()) {
            MallRoomBuilder.debug("build verify gave up with " + unfinished.size() + " left");
            return true;
        }
        verifySweeps++;
        cursor.requeue(unfinished);
        return false;
    }

    // --- Shared helpers ----------------------------------------------------

    /**
     * Nothing is workable from here: either walk toward the next cell, or admit defeat.
     *
     * <p>Refusing to steer is the honest failure mode for a job aimed at open sky, where blocks have
     * no support and never will.</p>
     */
    private void steerOrStall(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != null) {
            retireAlreadyFinished(level);
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

        // Nothing pending, so everything outstanding is deferred. Put it back and try once more.
        if (cursor.sweep()) {
            return;
        }

        // Sweep budget spent with work still outstanding: stop rather than spin. This is the honest
        // outcome for cells that can never be worked -- placements with no support, blocks behind
        // bedrock, a job aimed at open sky.
        MallRoomBuilder.debug("sweep budget exhausted with " + cursor.remaining() + " outstanding");
        AutoWalk.stop();
        LocalPlayer player = mc.player;
        if (state == State.CARVING && player != null) {
            beginBuildPhase(mc, player);
        } else {
            finish(mc);
        }
    }

    /**
     * Retires outstanding cells that are already in their final state.
     *
     * <p>Without this the queue can never drain. A cell that is already air is never a valid carve
     * target, and one that already holds the build block is never a valid place target — so neither
     * is ever selected, yet both stay outstanding, and the steering target would sit on one of them
     * forever. Pre-existing air (a cave clipping the room), a block another player placed, or simply
     * re-running a job over finished work all land here.
     *
     * <p>Only called when nothing workable is in reach, so the full scan is free — the engine has
     * nothing else to do this tick.</p>
     */
    private void retireAlreadyFinished(ClientLevel level) {
        boolean carving = state == State.CARVING;
        for (GridPos p : cursor.outstanding()) {
            BlockPos pos = MineDriver.toBlockPos(p);
            if (!level.isLoaded(pos)) {
                continue;
            }
            boolean done = carving
                    ? MineDriver.isCarved(level, pos)
                    : level.getBlockState(pos).is(HotbarSelector.buildBlock());
            if (done) {
                cursor.complete(p);
            }
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
        for (GridPos p : candidate.air()) {
            if (!level.isLoaded(MineDriver.toBlockPos(p))) {
                return "Part of that mall is in an unloaded chunk. Move closer or build fewer rooms.";
            }
        }
        for (GridPos p : candidate.skin()) {
            if (!level.isLoaded(MineDriver.toBlockPos(p))) {
                return "Part of that mall is in an unloaded chunk. Move closer or build fewer rooms.";
            }
        }
        return null;
    }

    private static MallAnchor anchorFor(LocalPlayer player) {
        return MallAnchor.of(
                Mth.floor(player.getX()), Mth.floor(player.getY()), Mth.floor(player.getZ()), player.getYRot());
    }

    // --- Status ------------------------------------------------------------

    public String progressLine() {
        if (cursor == null) {
            return "carved " + carved + ", placed " + placed + ".";
        }
        return "carved " + carved + ", placed " + placed + "; " + cursor.done() + "/" + cursor.total() + " this phase.";
    }

    /** One-line status for the command and the HUD. */
    public String statusLine() {
        return switch (state) {
            case IDLE -> "idle";
            case ARMING -> "waiting for you to release all keys";
            case CARVING -> "carving " + queueFraction();
            case BUILDING -> "building " + queueFraction();
            case PAUSED_NO_MATERIAL -> "paused: out of " + Config.buildBlock();
        };
    }

    private String queueFraction() {
        if (cursor == null) {
            return "";
        }
        String deferredNote = cursor.deferredCount() > 0 ? " (" + cursor.deferredCount() + " deferred)" : "";
        return cursor.done() + "/" + cursor.total() + deferredNote;
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
