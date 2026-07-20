/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

/**
 * A horizontal cardinal direction — the axis the mall extends along.
 *
 * <p><strong>Declaration order is significant.</strong> The constants are declared in Minecraft's
 * 2D data-value order (SOUTH 0, WEST 1, NORTH 2, EAST 3) so {@link #fromYaw} can be an ordinal
 * lookup that exactly reproduces {@code Direction.fromYRot}. The names also match
 * {@code net.minecraft.core.Direction}, so the client bridge converts by name
 * ({@code Direction.valueOf(facing.name())}) rather than maintaining a mapping table — the same
 * trick {@code bedrock-line-placement}'s {@code LineDirection} uses.</p>
 */
public enum Facing {
    SOUTH(0, 1),
    WEST(-1, 0),
    NORTH(0, -1),
    EAST(1, 0);

    private final int stepX;
    private final int stepZ;

    Facing(int stepX, int stepZ) {
        this.stepX = stepX;
        this.stepZ = stepZ;
    }

    /**
     * Snaps a player yaw (degrees) to the nearest cardinal.
     *
     * <p>Mirrors {@code Direction.fromYRot}, which is
     * {@code from2DDataValue(Mth.floor(yaw / 90 + 0.5) & 3)}. {@code LocalPlayer.getYRot()} is not
     * normalised in general (it accumulates past ±180 as the player spins), hence
     * {@link Math#floorMod} rather than a bare mask — same result, clearer intent, and it is what
     * the tests assert.</p>
     */
    public static Facing fromYaw(float yaw) {
        return values()[Math.floorMod((int) Math.floor(yaw / 90.0f + 0.5f), 4)];
    }

    public int stepX() {
        return stepX;
    }

    public int stepZ() {
        return stepZ;
    }

    /** The direction 90 degrees counter-clockwise, used as the mall's lateral axis. */
    public Facing left() {
        return switch (this) {
            case SOUTH -> EAST;
            case EAST -> NORTH;
            case NORTH -> WEST;
            case WEST -> SOUTH;
        };
    }

    public Facing opposite() {
        return switch (this) {
            case SOUTH -> NORTH;
            case NORTH -> SOUTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }
}
