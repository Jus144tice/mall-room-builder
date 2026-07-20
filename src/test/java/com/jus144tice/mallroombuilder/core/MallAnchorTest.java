/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Anchor arithmetic: where the mall lands relative to where the player was standing. */
class MallAnchorTest {

    private static final MallSpec SPEC = new MallSpec(4, 5, true);

    @Test
    void pitchIsEnvelopePlusHallLength() {
        assertEquals(12, SPEC.pitch());
        assertEquals(8, new MallSpec(2, 1, true).pitch());
        assertEquals(20, new MallSpec(2, 13, true).pitch());
    }

    @Test
    void roomZeroIsWhereThePlayerStands() {
        MallAnchor a = MallAnchor.of(17, 64, -3, 0.0f);
        assertEquals(a.playerFeet(), a.roomReference(SPEC, 0));
    }

    @Test
    void eachRoomIsOnePitchFurtherAlongTheFacing() {
        MallAnchor a = MallAnchor.of(0, 64, 0, 0.0f); // SOUTH, +Z
        assertEquals(new GridPos(0, 64, 12), a.roomReference(SPEC, 1));
        assertEquals(new GridPos(0, 64, 24), a.roomReference(SPEC, 2));
        assertEquals(new GridPos(0, 64, 36), a.roomReference(SPEC, 3));
    }

    @Test
    void worksForEveryFacingIncludingNegativeCoordinates() {
        GridPos feet = new GridPos(-100, 12, -200);
        assertEquals(new GridPos(-100, 12, -188), new MallAnchor(feet, Facing.SOUTH).roomReference(SPEC, 1));
        assertEquals(new GridPos(-100, 12, -212), new MallAnchor(feet, Facing.NORTH).roomReference(SPEC, 1));
        assertEquals(new GridPos(-88, 12, -200), new MallAnchor(feet, Facing.EAST).roomReference(SPEC, 1));
        assertEquals(new GridPos(-112, 12, -200), new MallAnchor(feet, Facing.WEST).roomReference(SPEC, 1));
    }

    @Test
    void floorPlateIsTheBlockBeingStoodOnAndCeilingIsFiveAbove() {
        MallAnchor a = MallAnchor.of(0, 64, 0, 0.0f);
        assertEquals(63, a.floorPlateY());
        assertEquals(69, a.ceilingPlateY());
        assertEquals(7, a.ceilingPlateY() - a.floorPlateY() + 1, "envelope is 7 tall");
    }

    @Test
    void alongAndSideInvertCellForEveryFacing() {
        for (Facing f : Facing.values()) {
            MallAnchor a = new MallAnchor(new GridPos(40, 64, -60), f);
            for (int along = -5; along <= 20; along++) {
                for (int side = -3; side <= 3; side++) {
                    GridPos p = a.cell(along, side, 70);
                    assertEquals(along, a.alongOf(p), "facing " + f);
                    assertEquals(side, a.sideOf(p), "facing " + f);
                    assertEquals(70, p.y());
                }
            }
        }
    }

    @Test
    void ofSnapsYawToACardinal() {
        assertEquals(Facing.SOUTH, MallAnchor.of(0, 0, 0, 12.0f).facing());
        assertEquals(Facing.WEST, MallAnchor.of(0, 0, 0, 78.0f).facing());
        assertEquals(Facing.NORTH, MallAnchor.of(0, 0, 0, -179.0f).facing());
        assertEquals(Facing.EAST, MallAnchor.of(0, 0, 0, -88.0f).facing());
    }

    @Test
    void specRejectsDegenerateShapes() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new MallSpec(0, 5, true));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new MallSpec(2, 0, true));
    }

    @Test
    void hallCountIsOneFewerThanRoomCount() {
        assertEquals(0, new MallSpec(1, 5, true).hallCount());
        assertEquals(3, new MallSpec(4, 5, true).hallCount());
    }
}
