/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Anchor arithmetic: where the room lands relative to where the player was standing and looking. */
class MallAnchorTest {

    private static final MallSpec SPEC = new MallSpec(true, true, 3);

    @Test
    void theFacedRoomOpensWhereTheWallWasFound() {
        MallAnchor a = MallAnchor.of(0, 64, 0, 0.0f, 2); // facing SOUTH (+Z)
        assertEquals(new GridPos(0, 64, 2), a.facedRoom().openingCentre());
        assertEquals(Facing.SOUTH, a.facedRoom().depth());
    }

    @Test
    void theOppositeRoomMirrorsAcrossTheCorridor() {
        MallAnchor a = MallAnchor.of(0, 64, 0, 0.0f, 2);
        RoomPlacement opposite = a.oppositeRoom(SPEC);
        // 3 planes of corridor between two wall planes: openings 4 apart.
        assertEquals(new GridPos(0, 64, -2), opposite.openingCentre());
        assertEquals(Facing.NORTH, opposite.depth());
    }

    @Test
    void oppositeOffsetTracksTheCorridorWidth() {
        for (int depth = 1; depth <= 8; depth++) {
            assertEquals(depth + 1, new MallSpec(true, true, depth).oppositeOpeningOffset(), "hallDepth " + depth);
        }
    }

    @Test
    void whereYouStandAcrossTheCorridorDoesNotShiftTheRoom() {
        // The opening distance comes from scanning for the wall, not from a fixed offset. Three
        // players standing at different spots in the same corridor, all looking at the wall at
        // z=3, must produce exactly the same room.
        GridPos wall = new GridPos(0, 64, 3);
        assertEquals(wall, MallAnchor.of(0, 64, 0, 0.0f, 3).facedRoom().openingCentre());
        assertEquals(wall, MallAnchor.of(0, 64, 1, 0.0f, 2).facedRoom().openingCentre());
        assertEquals(wall, MallAnchor.of(0, 64, 2, 0.0f, 1).facedRoom().openingCentre());
    }

    @Test
    void worksForEveryFacingIncludingNegativeCoordinates() {
        GridPos feet = new GridPos(-100, 12, -200);
        assertEquals(
                new GridPos(-100, 12, -198),
                new MallAnchor(feet, Facing.SOUTH, 2).facedRoom().openingCentre());
        assertEquals(
                new GridPos(-100, 12, -202),
                new MallAnchor(feet, Facing.NORTH, 2).facedRoom().openingCentre());
        assertEquals(
                new GridPos(-98, 12, -200),
                new MallAnchor(feet, Facing.EAST, 2).facedRoom().openingCentre());
        assertEquals(
                new GridPos(-102, 12, -200),
                new MallAnchor(feet, Facing.WEST, 2).facedRoom().openingCentre());
    }

    @Test
    void floorAndCeilingPlatesBracketASevenTallEnvelope() {
        MallAnchor a = MallAnchor.of(0, 64, 0, 0.0f, 2);
        assertEquals(63, a.floorPlateY());
        assertEquals(69, a.ceilingPlateY());
        assertEquals(7, a.ceilingPlateY() - a.floorPlateY() + 1);
    }

    @Test
    void alongAndSideInvertCellForEveryFacing() {
        for (Facing f : Facing.values()) {
            MallAnchor a = new MallAnchor(new GridPos(40, 64, -60), f, 2);
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
        assertEquals(Facing.SOUTH, MallAnchor.of(0, 0, 0, 12.0f, 2).facing());
        assertEquals(Facing.WEST, MallAnchor.of(0, 0, 0, 78.0f, 2).facing());
        assertEquals(Facing.NORTH, MallAnchor.of(0, 0, 0, -179.0f, 2).facing());
        assertEquals(Facing.EAST, MallAnchor.of(0, 0, 0, -88.0f, 2).facing());
    }

    @Test
    void roomCountFollowsTheSideChoice() {
        assertEquals(1, new MallSpec(false, true, 3).roomCount());
        assertEquals(2, new MallSpec(true, true, 3).roomCount());
    }

    @Test
    void specRejectsADegenerateCorridor() {
        assertThrows(IllegalArgumentException.class, () -> new MallSpec(false, true, 0));
    }
}
