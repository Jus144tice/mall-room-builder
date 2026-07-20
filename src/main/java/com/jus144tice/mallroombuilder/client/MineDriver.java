/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.client;

import com.jus144tice.mallroombuilder.core.GridPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * Real vanilla mining, one call per tick.
 *
 * <p>{@code MultiPlayerGameMode.continueDestroyBlock} does everything: on a new target it tail-calls
 * {@code startDestroyBlock}, it owns its own inter-block {@code destroyDelay}, and it completes the
 * break inside {@code startPrediction}. So there is no per-block state machine here — just call it
 * again every tick until the block turns to air.</p>
 *
 * <p>Because this is the genuine mining path, blocks break progressively at the speed of whatever
 * tool is held, that tool takes the durability, and the drops fall normally.</p>
 */
public final class MineDriver {

    private MineDriver() {}

    public static BlockPos toBlockPos(GridPos p) {
        return new BlockPos(p.x(), p.y(), p.z());
    }

    /**
     * Reach test, deliberately mirroring the server's own with <em>no</em> padding.
     *
     * <p>The server admits {@code canInteractWithBlock(pos, 1.0)}; using 0.0 here is strictly
     * tighter, so the mod can never send an action the server would reject, and never extends reach
     * by a millimetre.</p>
     */
    public static boolean inReach(LocalPlayer player, BlockPos pos) {
        return player.canInteractWithBlock(pos, 0.0);
    }

    /** Already carved? Decided from the world, never assumed, which is what makes it re-derivable. */
    public static boolean isCarved(ClientLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir();
    }

    /**
     * Would breaking this block actually yield its drop with what the player is holding?
     *
     * <p>The whole point of this mod is that you keep the material, so mining obsidian with a stone
     * pickaxe is a failure, not a slow success. Blocks that do not require a tool at all (dirt,
     * gravel) always pass.</p>
     *
     * <p>Uses NeoForge's <em>position-sensitive</em> overload, which routes through
     * {@code EventHooks.doPlayerHarvestCheck} — the plain {@code hasCorrectToolForDrops(BlockState)}
     * is deprecated precisely because it bypasses that, and bypassing it would ignore any other mod
     * that adjusts harvest rules.</p>
     */
    public static boolean canHarvest(LocalPlayer player, ClientLevel level, BlockPos pos) {
        return player.hasCorrectToolForDrops(level.getBlockState(pos), level, pos);
    }

    /** A readable name for the block at {@code pos}, for abort messages. */
    public static String blockName(ClientLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().getName().getString();
    }

    /** The face to report breaking from. Cosmetic — the server ignores it. */
    public static Direction faceFromEye(LocalPlayer player, BlockPos pos) {
        Vec3 toEye = player.getEyePosition().subtract(Vec3.atCenterOf(pos));
        return Direction.getNearest(toEye.x, toEye.y, toEye.z);
    }

    /** Advances the break on {@code pos} by one tick. */
    public static void drive(Minecraft mc, LocalPlayer player, BlockPos pos) {
        if (mc.gameMode == null) {
            return;
        }
        mc.gameMode.continueDestroyBlock(pos, faceFromEye(player, pos));
        player.swing(InteractionHand.MAIN_HAND);
    }

    /** Cancels any in-progress break, clearing the client's cracking overlay. */
    public static void cancel(Minecraft mc) {
        if (mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
    }
}
