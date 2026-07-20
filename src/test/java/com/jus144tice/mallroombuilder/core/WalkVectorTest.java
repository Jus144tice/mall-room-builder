/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The world-to-local impulse conversion, checked against a reimplementation of Minecraft's own
 * {@code Entity.getInputVector} rotation. If these round-trips fail the player walks sideways.
 */
class WalkVectorTest {

    private static final double TOLERANCE = 1.0e-9;

    /** Mirrors {@code Entity.getInputVector}: local (left, forward) rotated into world space. */
    private static double[] toWorld(double left, double forward, float yaw) {
        double r = Math.toRadians(yaw);
        double sin = Math.sin(r);
        double cos = Math.cos(r);
        return new double[] {left * cos - forward * sin, forward * cos + left * sin};
    }

    @Test
    void atYawZeroForwardIsPlusZAndLeftIsPlusX() {
        WalkVector v = WalkVector.toward(0.0, 1.0, 0.0f, 1.0, false);
        assertEquals(1.0, v.forward(), TOLERANCE);
        assertEquals(0.0, v.left(), TOLERANCE);

        WalkVector strafe = WalkVector.toward(1.0, 0.0, 0.0f, 1.0, false);
        assertEquals(0.0, strafe.forward(), TOLERANCE);
        assertEquals(1.0, strafe.left(), TOLERANCE);
    }

    @Test
    void atYawNinetyForwardIsMinusX() {
        WalkVector v = WalkVector.toward(-1.0, 0.0, 90.0f, 1.0, false);
        assertEquals(1.0, v.forward(), TOLERANCE);
        assertEquals(0.0, v.left(), TOLERANCE);
    }

    @Test
    void atYawOneEightyForwardIsMinusZ() {
        WalkVector v = WalkVector.toward(0.0, -1.0, 180.0f, 1.0, false);
        assertEquals(1.0, v.forward(), TOLERANCE);
        assertEquals(0.0, v.left(), TOLERANCE);
    }

    @Test
    void roundTripsThroughMinecraftsOwnRotationForManyYawsAndDirections() {
        for (int yawDegrees = -360; yawDegrees <= 360; yawDegrees += 7) {
            for (int angle = 0; angle < 360; angle += 11) {
                double dx = Math.cos(Math.toRadians(angle));
                double dz = Math.sin(Math.toRadians(angle));

                WalkVector local = WalkVector.toward(dx, dz, yawDegrees, 1.0, false);
                double[] world = toWorld(local.left(), local.forward(), yawDegrees);

                assertEquals(dx, world[0], 1.0e-9, "yaw " + yawDegrees + " angle " + angle);
                assertEquals(dz, world[1], 1.0e-9, "yaw " + yawDegrees + " angle " + angle);
            }
        }
    }

    @Test
    void magnitudeIsAlwaysTheRequestedSpeed() {
        for (int angle = 0; angle < 360; angle += 13) {
            double dx = Math.cos(Math.toRadians(angle));
            double dz = Math.sin(Math.toRadians(angle));
            WalkVector v = WalkVector.toward(dx, dz, 37.0f, 0.4, false);
            assertEquals(0.4, Math.hypot(v.forward(), v.left()), 1.0e-9);
        }
    }

    @Test
    void speedScalesLinearly() {
        WalkVector half = WalkVector.toward(0.0, 1.0, 0.0f, 0.5, false);
        assertEquals(0.5, half.forward(), TOLERANCE);
    }

    @Test
    void degenerateDirectionProducesNoMovementButCanStillJump() {
        assertEquals(WalkVector.STILL, WalkVector.toward(0.0, 0.0, 45.0f, 1.0, false));
        assertFalse(WalkVector.STILL.isMoving());

        WalkVector jumpOnly = WalkVector.toward(0.0, 0.0, 45.0f, 1.0, true);
        assertTrue(jumpOnly.jump());
        assertFalse(jumpOnly.isMoving());
    }

    @Test
    void jumpFlagIsCarriedThrough() {
        assertTrue(WalkVector.toward(1.0, 1.0, 0.0f, 1.0, true).jump());
        assertFalse(WalkVector.toward(1.0, 1.0, 0.0f, 1.0, false).jump());
    }
}
