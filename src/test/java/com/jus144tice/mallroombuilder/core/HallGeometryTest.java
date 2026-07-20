/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The stretch of spine corridor fronting one room slot. */
class HallGeometryTest {

    private static final RoomPlacement ROOM = new RoomPlacement(new GridPos(0, 64, 5), Facing.SOUTH);
    private static final int DEPTH = 3;

    @Test
    void interiorIsThreePlanesDeepSevenWideAndFiveTall() {
        Set<GridPos> interior = HallGeometry.interior(ROOM, DEPTH);
        assertEquals(3 * 7 * 5, interior.size());
        assertEquals(105, interior.size());
    }

    @Test
    void platesCoverTheSameFootprintTopAndBottom() {
        Set<GridPos> plates = HallGeometry.visibleSkin(ROOM, DEPTH);
        assertEquals(3 * 7 * 2, plates.size());
        assertEquals(42, plates.size());
        assertTrue(plates.stream().allMatch(p -> p.y() == ROOM.floorPlateY() || p.y() == ROOM.ceilingPlateY()));
    }

    @Test
    void itSitsEntirelyInFrontOfTheOpening() {
        for (GridPos p : HallGeometry.interior(ROOM, DEPTH)) {
            int d = ROOM.depthOf(p);
            assertTrue(d >= -DEPTH && d <= -1, "hall cell at depth " + d + " should be in front of the opening");
        }
    }

    @Test
    void itNeverOverlapsTheRoom() {
        Set<GridPos> room = new HashSet<>(RoomGeometry.envelope(ROOM));
        assertTrue(HallGeometry.interior(ROOM, DEPTH).stream().noneMatch(room::contains));
        assertTrue(HallGeometry.visibleSkin(ROOM, DEPTH).stream().noneMatch(room::contains));
    }

    @Test
    void itIsOneWiderEachWayThanTheRoomSoSlotsTileAtThePillars() {
        // A 5-wide segment would leave an unfinished strip of floor under every pillar.
        assertEquals(RoomGeometry.ENVELOPE_RADIUS, HallGeometry.SEGMENT_RADIUS);
        Set<GridPos> plates = HallGeometry.visibleSkin(ROOM, DEPTH);
        assertTrue(plates.stream().anyMatch(p -> Math.abs(ROOM.sideOf(p)) == 3));
    }

    @Test
    void interiorAndPlatesAreDisjoint() {
        Set<GridPos> interior = new HashSet<>(HallGeometry.interior(ROOM, DEPTH));
        assertTrue(HallGeometry.visibleSkin(ROOM, DEPTH).stream().noneMatch(interior::contains));
    }

    @Test
    void depthScalesLinearly() {
        for (int depth = 1; depth <= 6; depth++) {
            assertEquals(depth * 7 * 5, HallGeometry.interior(ROOM, depth).size(), "depth " + depth);
            assertEquals(depth * 7 * 2, HallGeometry.visibleSkin(ROOM, depth).size(), "depth " + depth);
        }
    }

    @Test
    void worksForEveryFacing() {
        for (Facing f : Facing.values()) {
            RoomPlacement room = new RoomPlacement(new GridPos(10, 70, -20), f);
            assertEquals(105, HallGeometry.interior(room, DEPTH).size(), "facing " + f);
            assertEquals(42, HallGeometry.visibleSkin(room, DEPTH).size(), "facing " + f);
        }
    }
}
