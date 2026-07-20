/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** The hallway is 3 wide and 5 tall finished, 5 by 7 carved, with the same framing rule. */
class HallGeometryTest {

    private static final MallAnchor ANCHOR = MallAnchor.of(0, 64, 0, 0.0f); // facing SOUTH (+Z)

    private static MallSpec spec(int hallLength) {
        return new MallSpec(2, hallLength, true);
    }

    @Test
    void gapSpansExactlyHallLengthPlanes() {
        for (int n = 1; n <= 8; n++) {
            MallSpec s = spec(n);
            assertEquals(n, HallGeometry.gapEnd(s, 0) - HallGeometry.gapStart(s, 0) + 1, "hallLength " + n);
        }
    }

    @Test
    void openingSpansTwoMoreThanTheGap_thePiercedRoomWallPlanes() {
        MallSpec s = spec(5);
        assertEquals(7, HallGeometry.openingEnd(s, 0) - HallGeometry.openingStart(s, 0) + 1);
        assertEquals(HallGeometry.gapStart(s, 0) - 1, HallGeometry.openingStart(s, 0));
        assertEquals(HallGeometry.gapEnd(s, 0) + 1, HallGeometry.openingEnd(s, 0));
    }

    @Test
    void theGapSitsExactlyBetweenTheTwoRoomEnvelopes() {
        MallSpec s = spec(5);
        // Room 0's envelope ends at along 3; room 1's begins at pitch - 3.
        assertEquals(4, HallGeometry.gapStart(s, 0));
        assertEquals(s.pitch() - 4, HallGeometry.gapEnd(s, 0));
    }

    @Test
    void interiorIsThreeWideAndFiveTallAcrossTheOpening() {
        MallSpec s = spec(5);
        Set<GridPos> interior = HallGeometry.interior(ANCHOR, s, 0);
        assertEquals(3 * 5 * 7, interior.size()); // 3 wide x 5 tall x 7 opening planes = 105

        assertTrue(interior.stream().allMatch(p -> p.y() >= 64 && p.y() <= 68), "5 tall, floor at feet Y");
        assertTrue(interior.stream().allMatch(p -> Math.abs(ANCHOR.sideOf(p)) <= 1), "3 wide");
    }

    @Test
    void floorAndCeilingPlanesLineUpWithTheRoom() {
        MallSpec s = spec(5);
        Set<GridPos> skin = HallGeometry.visibleSkin(ANCHOR, s, 0);
        // Same plates as the room: feetY-1 and feetY+5.
        assertEquals(ANCHOR.floorPlateY(), 63);
        assertEquals(ANCHOR.ceilingPlateY(), 69);
        assertTrue(skin.stream().anyMatch(p -> p.y() == 63));
        assertTrue(skin.stream().anyMatch(p -> p.y() == 69));
        assertTrue(skin.stream().allMatch(p -> p.y() >= 63 && p.y() <= 69));
    }

    @Test
    void oneSliceIs35Envelope_15Interior_16Visible_4Framing() {
        // hallLength 1 makes the gap exactly one plane, so the gap skin is one cross-section.
        MallSpec s = new MallSpec(2, 1, false); // thresholds off: gap skin only
        Set<GridPos> skin = HallGeometry.visibleSkin(ANCHOR, s, 0);
        assertEquals(16, skin.size(), "visible skin per slice");

        int envelope = 5 * 7;
        int interior = 3 * 5;
        assertEquals(35, envelope);
        assertEquals(15, interior);
        assertEquals(20, envelope - interior, "shell per slice");
        assertEquals(4, (envelope - interior) - skin.size(), "framing per slice: the four long corners");
    }

    @Test
    void sideWallsSpanTheGapAndPlatesSpanTheOpening() {
        MallSpec s = spec(5);
        Set<GridPos> skin = HallGeometry.visibleSkin(ANCHOR, s, 0);

        long sideWalls =
                skin.stream().filter(p -> Math.abs(ANCHOR.sideOf(p)) == 2).count();
        assertEquals(2 * 5 * 5, sideWalls, "two walls x 5 tall x 5 gap planes");

        long plates = skin.stream().filter(p -> p.y() == 63 || p.y() == 69).count();
        // Plates are 3 wide, not 5: a plate cell at side +/-2 sits at two extremes at once, which
        // makes it a corner edge of the tube -- framing, left as stone. So 3 wide across all 7
        // opening planes, top and bottom.
        assertEquals(3 * 7 * 2, plates);
        assertEquals(42, plates);
        // Side walls plus plates account for the whole hallway skin.
        assertEquals(skin.size(), sideWalls + plates);
    }

    @Test
    void coveringDoorThresholdsAddsTwelveBlocksPerHallway() {
        MallSpec on = new MallSpec(2, 5, true);
        MallSpec off = new MallSpec(2, 5, false);
        int with = HallGeometry.visibleSkin(ANCHOR, on, 0).size();
        int without = HallGeometry.visibleSkin(ANCHOR, off, 0).size();
        assertEquals(12, with - without, "3 floor + 3 ceiling, at each of the two doorways");
        assertEquals(92, with);
        assertEquals(80, without);
    }

    @Test
    void interiorAndSkinNeverOverlap() {
        MallSpec s = spec(5);
        Set<GridPos> interior = HallGeometry.interior(ANCHOR, s, 0);
        assertTrue(HallGeometry.visibleSkin(ANCHOR, s, 0).stream().noneMatch(interior::contains));
    }

    @Test
    void worksForEveryFacing() {
        MallSpec s = spec(5);
        for (Facing f : Facing.values()) {
            MallAnchor a = new MallAnchor(new GridPos(10, 70, -20), f);
            assertEquals(105, HallGeometry.interior(a, s, 0).size(), "facing " + f);
            assertEquals(92, HallGeometry.visibleSkin(a, s, 0).size(), "facing " + f);
        }
    }
}
