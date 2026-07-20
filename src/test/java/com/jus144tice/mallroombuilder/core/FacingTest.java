/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Yaw snapping must reproduce {@code Direction.fromYRot} exactly, including for the unnormalised
 * yaws {@code LocalPlayer.getYRot()} produces once the player has spun a few times.
 */
class FacingTest {

    @Test
    void declarationOrderMatchesMinecraftTwoDimensionalDataValues() {
        assertEquals(0, Facing.SOUTH.ordinal());
        assertEquals(1, Facing.WEST.ordinal());
        assertEquals(2, Facing.NORTH.ordinal());
        assertEquals(3, Facing.EAST.ordinal());
    }

    @Test
    void stepVectorsAreUnitAndPointTheRightWay() {
        assertEquals(0, Facing.SOUTH.stepX());
        assertEquals(1, Facing.SOUTH.stepZ());
        assertEquals(-1, Facing.WEST.stepX());
        assertEquals(0, Facing.WEST.stepZ());
        assertEquals(0, Facing.NORTH.stepX());
        assertEquals(-1, Facing.NORTH.stepZ());
        assertEquals(1, Facing.EAST.stepX());
        assertEquals(0, Facing.EAST.stepZ());
    }

    @ParameterizedTest(name = "yaw {0} -> {1}")
    @CsvSource({
        "0, SOUTH",
        "44.9, SOUTH",
        "45, WEST",
        "89.9, WEST",
        "90, WEST",
        "135, NORTH",
        "180, NORTH",
        "225, EAST",
        "270, EAST",
        "359.9, SOUTH",
        "360, SOUTH",
        "-45, SOUTH",
        "-90, EAST",
        "-135, EAST",
        "-180, NORTH",
        "-270, WEST",
        "720, SOUTH",
        "-721, SOUTH"
    })
    void fromYawSnapsToTheNearestCardinal(float yaw, String expected) {
        assertEquals(Facing.valueOf(expected), Facing.fromYaw(yaw));
    }

    @Test
    void fromYawMatchesTheVanillaFormulaAcrossAWideRange() {
        for (int degrees = -1080; degrees <= 1080; degrees++) {
            float yaw = degrees;
            // Direction.fromYRot: from2DDataValue(Mth.floor(yaw / 90 + 0.5) & 3)
            int expected = (int) Math.floor(yaw / 90.0f + 0.5f) & 3;
            assertEquals(Facing.values()[expected], Facing.fromYaw(yaw), "yaw " + degrees);
        }
    }

    @Test
    void leftRotatesCounterClockwiseAndReturnsAfterFour() {
        assertEquals(Facing.EAST, Facing.SOUTH.left());
        assertEquals(Facing.NORTH, Facing.EAST.left());
        assertEquals(Facing.WEST, Facing.NORTH.left());
        assertEquals(Facing.SOUTH, Facing.WEST.left());

        for (Facing f : Facing.values()) {
            assertEquals(f, f.left().left().left().left());
            assertEquals(f.opposite(), f.left().left());
        }
    }
}
