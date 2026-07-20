/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.client;

import com.jus144tice.mallroombuilder.Config;
import com.jus144tice.mallroombuilder.core.GridPos;
import com.jus144tice.mallroombuilder.core.WalkVector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * Movement steering, for when the next block is out of reach.
 *
 * <p>No mixin required. NeoForge fires {@link MovementInputUpdateEvent} from
 * {@code LocalPlayer.aiStep()} in the gap between {@code input.tick(...)} populating the impulse
 * fields from key state and {@code serverAiStep()} consuming them — so writing them here takes
 * effect on the same tick.</p>
 *
 * <p>The boolean flags are set alongside the impulses because {@code LocalPlayer} reads
 * {@code jumping} and {@code hasForwardImpulse()} after the event for sprint and jump handling.</p>
 *
 * <p>Steering is always the fallback, never the preference: the engine stops it the instant
 * anything is in reach, so the player stands still and mines rather than shuffling.</p>
 */
public final class AutoWalk {

    private static GridPos target;
    private static int stuckTicks;
    private static double lastX;
    private static double lastZ;

    private AutoWalk() {}

    /** Requests movement toward {@code cell}. Safe to call every tick with the same target. */
    public static void steerTo(GridPos cell) {
        if (!Config.autoWalkEnabled()) {
            target = null;
            return;
        }
        target = cell;
    }

    public static void stop() {
        target = null;
        stuckTicks = 0;
    }

    public static boolean isSteering() {
        return target != null;
    }

    /** Per-tick bookkeeping: notices when steering is making no progress. */
    public static void tick(LocalPlayer player) {
        if (target == null) {
            stuckTicks = 0;
            lastX = player.getX();
            lastZ = player.getZ();
            return;
        }
        double moved = Math.hypot(player.getX() - lastX, player.getZ() - lastZ);
        lastX = player.getX();
        lastZ = player.getZ();
        if (moved < 0.01) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
    }

    /** The impulses to apply this tick, in the player's local frame. */
    public static WalkVector desiredWalk(LocalPlayer player) {
        GridPos t = target;
        if (t == null) {
            return WalkVector.STILL;
        }
        double dx = (t.x() + 0.5) - player.getX();
        double dz = (t.z() + 0.5) - player.getZ();
        return WalkVector.toward(dx, dz, player.getYRot(), Config.autoWalkSpeed(), wantsJump(player, t));
    }

    /**
     * Jump when there is a lip to climb, or when we have stopped making progress.
     *
     * <p>The stuck case matters more than it looks: a half-carved room leaves odd 1-block steps, and
     * without this the driver can press forward into a wall corner indefinitely.</p>
     */
    private static boolean wantsJump(LocalPlayer player, GridPos t) {
        if (!Config.autoWalkJump()) {
            return false;
        }
        if (stuckTicks >= Config.stuckTicks()) {
            return true;
        }
        if (!player.onGround()) {
            return false;
        }
        // A solid block at foot height directly ahead is a step-up.
        double dx = (t.x() + 0.5) - player.getX();
        double dz = (t.z() + 0.5) - player.getZ();
        double length = Math.hypot(dx, dz);
        if (length < 0.1) {
            return false;
        }
        BlockPos ahead = BlockPos.containing(player.getX() + dx / length, player.getY(), player.getZ() + dz / length);
        return player.level().isLoaded(ahead)
                && !player.level().getBlockState(ahead).canBeReplaced();
    }

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || event.getEntity() != player) {
            return;
        }
        if (!JobEngine.INSTANCE.isRunning() || target == null) {
            return;
        }

        WalkVector v = desiredWalk(player);
        Input in = event.getInput();
        in.forwardImpulse = (float) v.forward();
        in.leftImpulse = (float) v.left();
        in.up = v.forward() > 0.1;
        in.down = v.forward() < -0.1;
        in.left = v.left() > 0.1;
        in.right = v.left() < -0.1;
        in.jumping = v.jump();
    }
}
