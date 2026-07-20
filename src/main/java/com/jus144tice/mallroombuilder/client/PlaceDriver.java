/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Block placement through the vanilla path.
 *
 * <p>This is the same technique {@code bedrock-line-placement}'s {@code tryReacharound} uses:
 * synthesize the exact {@link BlockHitResult} the player would produce by aiming at a neighbour's
 * face, and hand it to {@code MultiPlayerGameMode.useItemOn}. No custom packet, no direct
 * {@code setBlock}, and the server validates it exactly as it would a hand-aimed click.</p>
 *
 * <p>The hit vector sits at a face centre, which is 0.5 from the block centre on one axis and 0 on
 * the others — comfortably inside the server's {@code 1.0000001} per-axis tolerance.</p>
 */
public final class PlaceDriver {

    /** A neighbouring block to click against, and the face of it that points at the target. */
    public record Support(BlockPos pos, Direction face) {

        public Vec3 hitVec() {
            Vec3i n = face.getNormal();
            return Vec3.atCenterOf(pos).add(n.getX() * 0.5, n.getY() * 0.5, n.getZ() * 0.5);
        }
    }

    private PlaceDriver() {}

    /**
     * True if the cell can accept a block right now: replaceable, loaded, and not occupied by the
     * player.
     *
     * <p>{@code isUnobstructed} is the whole answer to "don't brick yourself into the floor" — a
     * cell the player is standing in simply is not placeable, so it gets deferred and collected on
     * a later sweep once they have moved on.</p>
     */
    public static boolean isPlaceable(
            ClientLevel level,
            LocalPlayer player,
            BlockPos pos,
            net.minecraft.world.level.block.state.BlockState buildState) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        if (!level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        return level.isUnobstructed(buildState, pos, CollisionContext.of(player));
    }

    /**
     * Finds the best neighbour to click against: solid, loaded, and with its facing face in reach.
     * Prefers whichever face centre is nearest the eye.
     *
     * <p>A target with no solid neighbour yet is not a failure — it becomes placeable once an
     * adjacent skin block lands. Returning null lets the engine defer it and try again.</p>
     */
    public static Support findSupport(LocalPlayer player, ClientLevel level, BlockPos target) {
        Support best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3 eye = player.getEyePosition();

        for (Direction d : Direction.values()) {
            BlockPos neighbour = target.relative(d);
            if (!level.isLoaded(neighbour)) {
                continue;
            }
            if (level.getBlockState(neighbour).canBeReplaced()) {
                continue; // nothing solid to click
            }
            if (!MineDriver.inReach(player, neighbour)) {
                continue;
            }
            Support candidate = new Support(neighbour, d.getOpposite());
            double distance = eye.distanceToSqr(candidate.hitVec());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Attempts the placement.
     *
     * @return true if vanilla consumed the action
     */
    public static boolean place(Minecraft mc, LocalPlayer player, Support support) {
        if (mc.gameMode == null) {
            return false;
        }
        BlockHitResult hit = new BlockHitResult(support.hitVec(), support.face(), support.pos(), false);
        InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        if (result != null && result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        return false;
    }
}
