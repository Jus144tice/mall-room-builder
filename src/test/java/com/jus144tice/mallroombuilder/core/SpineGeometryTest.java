/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** A spine segment: 3 wide, 5 tall roughed out or 7 tall finished. No framing either way. */
class SpineGeometryTest {

    private static final MallAnchor ANCHOR = MallAnchor.of(0, 64, 0, 0.0f); // facing SOUTH (+Z)

    private static Set<GridPos> carve(int length, boolean finish) {
        return SpineGeometry.carve(ANCHOR.spineStart(), length, finish);
    }

    @Nested
    @DisplayName("counts")
    class Counts {

        @Test
        void aFinishedSegmentIs147Blocks() {
            assertEquals(7 * 3 * 7, carve(7, true).size());
            assertEquals(147, carve(7, true).size());
            assertEquals(147, SpineGeometry.cellCount(SpineGeometry.DEFAULT_LENGTH, true));
        }

        @Test
        void aRoughSegmentIs105Blocks() {
            assertEquals(7 * 3 * 5, carve(7, false).size());
            assertEquals(105, carve(7, false).size());
            assertEquals(105, SpineGeometry.cellCount(SpineGeometry.DEFAULT_LENGTH, false));
        }

        @Test
        void theDifferenceIsExactlyTheTwoRecessPlanes() {
            assertEquals(42, carve(7, true).size() - carve(7, false).size());
            assertEquals(42, SpineGeometry.recesses(ANCHOR.spineStart(), 7).size());
        }

        @Test
        void lengthScalesLinearly() {
            for (int length = 1; length <= 12; length++) {
                assertEquals(length * 21, carve(length, true).size(), "finish, length " + length);
                assertEquals(length * 15, carve(length, false).size(), "rough, length " + length);
            }
        }
    }

    @Nested
    @DisplayName("placement")
    class Placement {

        @Test
        void itStartsAtTheBlockDirectlyInFrontOfThePlayer() {
            Set<GridPos> cells = carve(7, true);
            assertTrue(cells.contains(new GridPos(0, 64, 1)), "the very next block is carved");
            assertFalse(cells.contains(new GridPos(0, 64, 0)), "the block the player is standing in is not");
        }

        @Test
        void itRunsTheRequestedLengthAndNoFurther() {
            Set<GridPos> cells = carve(7, true);
            assertTrue(cells.contains(new GridPos(0, 64, 7)));
            assertFalse(cells.contains(new GridPos(0, 64, 8)));
        }

        @Test
        void itIsThreeWideCentredOnThePlayer() {
            Set<GridPos> cells = carve(7, true);
            assertTrue(cells.stream().allMatch(p -> Math.abs(p.x()) <= 1));
            assertTrue(cells.contains(new GridPos(-1, 64, 1)));
            assertTrue(cells.contains(new GridPos(1, 64, 1)));
            assertFalse(cells.contains(new GridPos(2, 64, 1)));
        }

        @Test
        void aRoughSegmentLeavesTheFloorAndCeilingAlone() {
            Set<GridPos> cells = carve(7, false);
            assertTrue(cells.stream().allMatch(p -> p.y() >= 64 && p.y() <= 68), "5 tall, feet level");
            assertFalse(cells.contains(new GridPos(0, 63, 1)), "floor untouched");
            assertFalse(cells.contains(new GridPos(0, 69, 1)), "ceiling untouched");
        }

        @Test
        void aFinishedSegmentCutsBothRecessesAndDropsThePlayerOneBlock() {
            Set<GridPos> cells = carve(7, true);
            assertTrue(cells.stream().allMatch(p -> p.y() >= 63 && p.y() <= 69), "7 tall");
            assertTrue(cells.contains(new GridPos(0, 63, 1)), "floor recess");
            assertTrue(cells.contains(new GridPos(0, 69, 1)), "ceiling recess");
            assertEquals(ANCHOR.floorPlateY(), 63);
            assertEquals(ANCHOR.ceilingPlateY(), 69);
        }

        @Test
        void aFinishedSegmentSpansTheSameHeightAsARoom() {
            // This is what keeps a finished room flush with the corridor beside it.
            Set<GridPos> spine = carve(7, true);
            Set<GridPos> room = RoomGeometry.carve(ANCHOR.facedRoom(), true);
            assertEquals(
                    spine.stream().mapToInt(GridPos::y).min().orElseThrow(),
                    room.stream().mapToInt(GridPos::y).min().orElseThrow());
            assertEquals(
                    spine.stream().mapToInt(GridPos::y).max().orElseThrow(),
                    room.stream().mapToInt(GridPos::y).max().orElseThrow());
        }
    }

    @Test
    void roughIsAlwaysASubsetOfFinish() {
        // Which is what lets you rough a run out now and re-run it later for just the recesses.
        for (int length = 1; length <= 8; length++) {
            assertTrue(carve(length, true).containsAll(carve(length, false)), "length " + length);
        }
    }

    @Test
    void worksForEveryFacing() {
        for (Facing f : Facing.values()) {
            MallAnchor a = new MallAnchor(new GridPos(10, 70, -20), f);
            Set<GridPos> cells = SpineGeometry.carve(a.spineStart(), 7, true);
            assertEquals(147, cells.size(), "facing " + f);
            assertTrue(cells.contains(a.cell(1, 0, 70)), "facing " + f + " starts one ahead");
            assertFalse(cells.contains(a.playerFeet()), "facing " + f + " never includes the player's own block");
        }
    }

    @Test
    void consecutiveSegmentsTileWithoutGapOrOverlap() {
        // Walk to the far end of one segment and run it again: the next picks up exactly where this
        // one stopped.
        Set<GridPos> first = carve(7, true);
        MallAnchor next = new MallAnchor(new GridPos(0, 64, 7), Facing.SOUTH);
        Set<GridPos> second = SpineGeometry.carve(next.spineStart(), 7, true);

        assertTrue(new HashSet<>(second).stream().noneMatch(first::contains), "no overlap");
        assertTrue(second.contains(new GridPos(0, 64, 8)), "no gap: continues straight on from z=7");
    }
}
