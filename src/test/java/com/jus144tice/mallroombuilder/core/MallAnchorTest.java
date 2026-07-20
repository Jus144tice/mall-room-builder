/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Anchor arithmetic: everything follows from where you stand and which way you look. */
class MallAnchorTest {

    @Test
    void aJobAlwaysStartsAtTheVeryNextBlock() {
        assertEquals(1, MallAnchor.START_OFFSET);
        MallAnchor a = MallAnchor.of(0, 64, 0, 0.0f); // facing SOUTH (+Z)
        assertEquals(new GridPos(0, 64, 1), a.facedRoom().openingCentre());
        assertEquals(new GridPos(0, 64, 1), a.spineStart().openingCentre());
    }

    @Test
    void roomAndSpineShareTheSameStartingPoint() {
        MallAnchor a = MallAnchor.of(-12, 70, 33, 90.0f);
        assertEquals(a.facedRoom().openingCentre(), a.spineStart().openingCentre());
        assertEquals(a.facedRoom().depth(), a.spineStart().depth());
    }

    @Test
    void nothingIsReadFromTheWorldSoTheGeometryIsReproducible() {
        // Two anchors built the same way must be identical -- this is what makes a partial job
        // resumable by standing in the same spot and running the command again.
        assertEquals(MallAnchor.of(4, 64, -9, 0.0f), MallAnchor.of(4, 64, -9, 0.0f));
        assertEquals(
                MallAnchor.of(4, 64, -9, 0.0f).facedRoom(),
                MallAnchor.of(4, 64, -9, 44.0f).facedRoom(),
                "any yaw snapping to the same cardinal gives the same room");
    }

    @Test
    void theOppositeRoomMirrorsAcrossTheCorridor() {
        MallAnchor a = MallAnchor.of(0, 64, 0, 0.0f);
        RoomPlacement opposite = a.oppositeRoom(MallSpec.room(true, 3));
        // 3 lanes of corridor between two wall planes: openings 4 apart.
        assertEquals(new GridPos(0, 64, -3), opposite.openingCentre());
        assertEquals(Facing.NORTH, opposite.depth());
    }

    @Test
    void oppositeOffsetTracksTheCorridorWidth() {
        for (int depth = 1; depth <= 8; depth++) {
            assertEquals(depth + 1, MallSpec.room(true, depth).oppositeOpeningOffset(), "hallDepth " + depth);
        }
    }

    @Test
    void worksForEveryFacingIncludingNegativeCoordinates() {
        GridPos feet = new GridPos(-100, 12, -200);
        assertEquals(
                new GridPos(-100, 12, -199),
                new MallAnchor(feet, Facing.SOUTH).facedRoom().openingCentre());
        assertEquals(
                new GridPos(-100, 12, -201),
                new MallAnchor(feet, Facing.NORTH).facedRoom().openingCentre());
        assertEquals(
                new GridPos(-99, 12, -200),
                new MallAnchor(feet, Facing.EAST).facedRoom().openingCentre());
        assertEquals(
                new GridPos(-101, 12, -200),
                new MallAnchor(feet, Facing.WEST).facedRoom().openingCentre());
    }

    @Test
    void floorAndCeilingRecessesBracketASevenTallEnvelope() {
        MallAnchor a = MallAnchor.of(0, 64, 0, 0.0f);
        assertEquals(63, a.floorPlateY());
        assertEquals(69, a.ceilingPlateY());
        assertEquals(7, a.ceilingPlateY() - a.floorPlateY() + 1);
    }

    @Test
    void alongAndSideInvertCellForEveryFacing() {
        for (Facing f : Facing.values()) {
            MallAnchor a = new MallAnchor(new GridPos(40, 64, -60), f);
            for (int along = -6; along <= 12; along++) {
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
    void roomCountFollowsTheJobKind() {
        assertEquals(1, MallSpec.room(false, 3).roomCount());
        assertEquals(2, MallSpec.room(true, 3).roomCount());
        assertEquals(0, MallSpec.spine(7, 3).roomCount());
    }

    @Test
    void specRejectsDegenerateShapes() {
        assertThrows(IllegalArgumentException.class, () -> MallSpec.room(false, 0));
        assertThrows(IllegalArgumentException.class, () -> MallSpec.spine(0, 3));
    }
}
