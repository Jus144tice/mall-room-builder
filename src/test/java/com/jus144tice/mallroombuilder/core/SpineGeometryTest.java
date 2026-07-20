/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** A spine segment is a plain box: 7 long, 3 wide, 5 tall. No recesses, no framing. */
class SpineGeometryTest {

    private static final MallAnchor ANCHOR = MallAnchor.of(0, 64, 0, 0.0f); // facing SOUTH (+Z)

    @Test
    void aDefaultSegmentIs105Blocks() {
        Set<GridPos> cells = SpineGeometry.carve(ANCHOR.spineStart(), SpineGeometry.DEFAULT_LENGTH);
        assertEquals(7 * 3 * 5, cells.size());
        assertEquals(105, cells.size());
        assertEquals(105, SpineGeometry.cellCount(SpineGeometry.DEFAULT_LENGTH));
    }

    @Test
    void itStartsAtTheBlockDirectlyInFrontOfThePlayer() {
        Set<GridPos> cells = SpineGeometry.carve(ANCHOR.spineStart(), 7);
        assertTrue(cells.contains(new GridPos(0, 64, 1)), "the very next block is carved");
        assertFalse(cells.contains(new GridPos(0, 64, 0)), "the block the player is standing in is not");
    }

    @Test
    void itRunsSevenBlocksAheadAndNoFurther() {
        Set<GridPos> cells = SpineGeometry.carve(ANCHOR.spineStart(), 7);
        assertTrue(cells.contains(new GridPos(0, 64, 7)));
        assertFalse(cells.contains(new GridPos(0, 64, 8)));
    }

    @Test
    void itIsThreeWideCentredOnThePlayer() {
        Set<GridPos> cells = SpineGeometry.carve(ANCHOR.spineStart(), 7);
        assertTrue(cells.stream().allMatch(p -> Math.abs(p.x()) <= 1));
        assertTrue(cells.contains(new GridPos(-1, 64, 1)));
        assertTrue(cells.contains(new GridPos(1, 64, 1)));
        assertFalse(cells.contains(new GridPos(2, 64, 1)));
    }

    @Test
    void itIsFiveTallStartingAtTheFeet() {
        Set<GridPos> cells = SpineGeometry.carve(ANCHOR.spineStart(), 7);
        assertTrue(cells.stream().allMatch(p -> p.y() >= 64 && p.y() <= 68));
        assertTrue(cells.contains(new GridPos(0, 68, 1)));
        assertFalse(cells.contains(new GridPos(0, 69, 1)));
    }

    @Test
    void itNeverCarvesTheFloorYouAreStandingOn() {
        // This is what leaves a finished room flush with the corridor: the room's floor recess goes
        // one below the walking surface so a floor can be laid into it by hand, while the corridor's
        // own floor stays put.
        Set<GridPos> cells = SpineGeometry.carve(ANCHOR.spineStart(), 7);
        assertTrue(cells.stream().noneMatch(p -> p.y() == 63));
        assertFalse(cells.contains(new GridPos(0, 63, 1)));
    }

    @Test
    void lengthScalesLinearly() {
        for (int length = 1; length <= 12; length++) {
            assertEquals(
                    length * 15,
                    SpineGeometry.carve(ANCHOR.spineStart(), length).size(),
                    "length " + length);
            assertEquals(length * 15, SpineGeometry.cellCount(length), "length " + length);
        }
    }

    @Test
    void worksForEveryFacing() {
        for (Facing f : Facing.values()) {
            MallAnchor a = new MallAnchor(new GridPos(10, 70, -20), f);
            Set<GridPos> cells = SpineGeometry.carve(a.spineStart(), 7);
            assertEquals(105, cells.size(), "facing " + f);
            assertTrue(cells.contains(a.cell(1, 0, 70)), "facing " + f + " starts one ahead");
            assertFalse(cells.contains(a.playerFeet()), "facing " + f + " never includes the player's own block");
        }
    }

    @Test
    void consecutiveSegmentsTileWithoutGapOrOverlap() {
        // Walk to the far end of one segment and run it again: the next segment picks up exactly
        // where this one stopped.
        Set<GridPos> first = SpineGeometry.carve(ANCHOR.spineStart(), 7);
        MallAnchor next = new MallAnchor(new GridPos(0, 64, 7), Facing.SOUTH);
        Set<GridPos> second = SpineGeometry.carve(next.spineStart(), 7);

        assertTrue(second.stream().noneMatch(first::contains), "no overlap");
        assertTrue(second.contains(new GridPos(0, 64, 8)), "no gap: continues straight on from z=7");
    }
}
