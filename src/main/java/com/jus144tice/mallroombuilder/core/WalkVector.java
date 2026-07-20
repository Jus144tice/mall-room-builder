/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

/**
 * Movement input in the player's local frame, ready to be written onto {@code Input}.
 *
 * @param forward {@code forwardImpulse}: positive walks the way the player is looking
 * @param left    {@code leftImpulse}: positive strafes left
 * @param jump    whether to request a jump this tick
 */
public record WalkVector(double forward, double left, boolean jump) {

    public static final WalkVector STILL = new WalkVector(0.0, 0.0, false);

    /** Below this, a direction is treated as degenerate and produces {@link #STILL}. */
    private static final double EPSILON = 1.0e-4;

    /**
     * Converts a world-space direction into local impulses for a player at {@code yawDegrees}.
     *
     * <p>Minecraft rotates local input into world space in {@code Entity.getInputVector} as</p>
     *
     * <pre>
     *   worldX = left * cos(yaw) - forward * sin(yaw)
     *   worldZ = forward * cos(yaw) + left * sin(yaw)
     * </pre>
     *
     * <p>which is a plain 2D rotation, so inverting it is the transpose. Sanity: at yaw 0 the
     * player faces +Z, giving {@code forward = worldDz} and {@code left = worldDx}; at yaw 90 they
     * face -X, giving {@code forward = -worldDx}. Both are unit-tested.</p>
     *
     * @param speed scale applied to the normalised direction, 0..1
     */
    public static WalkVector toward(double worldDx, double worldDz, float yawDegrees, double speed, boolean jump) {
        double length = Math.hypot(worldDx, worldDz);
        if (length < EPSILON) {
            return jump ? new WalkVector(0.0, 0.0, true) : STILL;
        }
        double nx = worldDx / length;
        double nz = worldDz / length;
        double radians = Math.toRadians(yawDegrees);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double forward = nz * cos - nx * sin;
        double left = nx * cos + nz * sin;
        return new WalkVector(forward * speed, left * speed, jump);
    }

    public boolean isMoving() {
        return Math.abs(forward) > EPSILON || Math.abs(left) > EPSILON;
    }
}
