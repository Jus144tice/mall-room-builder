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

/** Composition: one or two rooms budding off the spine, plus the corridor in front of them. */
class MallLayoutTest {

    /** Standing in a 3-wide corridor, 2 blocks from the wall, facing south. */
    private static final MallAnchor ANCHOR = MallAnchor.of(0, 64, 0, 0.0f, 2);

    private static MallLayout layout(boolean bothSides, boolean finishHallway) {
        return new MallLayout(ANCHOR, new MallSpec(bothSides, finishHallway, 3));
    }

    @Nested
    @DisplayName("a single room")
    class SingleRoom {

        @Test
        void roomAloneIs250Carved() {
            MallLayout l = layout(false, false);
            assertEquals(250, l.carve().size());
            assertEquals(44, l.framing().size());
        }

        @Test
        void withTheCorridorItIs397() {
            MallLayout l = layout(false, true);
            assertEquals(250 + 147, l.carve().size());
            assertEquals(397, l.counts().minedTotal());
            assertEquals(44, l.framing().size());
        }

        @Test
        void theCorridorAddsNoFraming() {
            assertEquals(
                    layout(false, false).framing().size(),
                    layout(false, true).framing().size());
        }
    }

    @Nested
    @DisplayName("both sides of the spine")
    class BothSides {

        @Test
        void twoRoomsPlusOneSharedCorridorIs647() {
            MallLayout l = layout(true, true);
            assertEquals(2 * 250 + 147, l.carve().size());
            assertEquals(647, l.counts().minedTotal());
            assertEquals(88, l.framing().size());
        }

        @Test
        void theRoomsFaceEachOtherAcrossTheCorridor() {
            MallSpec spec = new MallSpec(true, true, 3);
            RoomPlacement faced = ANCHOR.facedRoom();
            RoomPlacement opposite = ANCHOR.oppositeRoom(spec);

            assertEquals(ANCHOR.facing(), faced.depth());
            assertEquals(ANCHOR.facing().opposite(), opposite.depth());
            // Openings 4 apart: 3 planes of corridor between two wall planes.
            assertEquals(4, ANCHOR.alongOf(faced.openingCentre()) - ANCHOR.alongOf(opposite.openingCentre()));
        }

        @Test
        void theTwoRoomsNeverOverlap() {
            MallSpec spec = new MallSpec(true, true, 3);
            Set<GridPos> a = new HashSet<>(RoomGeometry.envelope(ANCHOR.facedRoom()));
            assertTrue(RoomGeometry.envelope(ANCHOR.oppositeRoom(spec)).stream().noneMatch(a::contains));
        }

        @Test
        void theCorridorSitsExactlyBetweenThem() {
            MallSpec spec = new MallSpec(true, true, 3);
            Set<GridPos> hall = HallGeometry.interior(ANCHOR.facedRoom(), 3);
            Set<GridPos> roomA = new HashSet<>(RoomGeometry.envelope(ANCHOR.facedRoom()));
            Set<GridPos> roomB = new HashSet<>(RoomGeometry.envelope(ANCHOR.oppositeRoom(spec)));

            assertTrue(hall.stream().noneMatch(roomA::contains));
            assertTrue(hall.stream().noneMatch(roomB::contains));
            // No gap either: every along between the two openings is accounted for.
            for (GridPos p : hall) {
                int along = ANCHOR.alongOf(p);
                assertTrue(along >= -1 && along <= 1, "corridor plane at along " + along);
            }
        }
    }

    @Nested
    @DisplayName("invariants")
    class Invariants {

        @Test
        void framingAndCarveAreAlwaysDisjoint() {
            for (boolean both : new boolean[] {false, true}) {
                for (boolean hall : new boolean[] {false, true}) {
                    MallLayout l = layout(both, hall);
                    Set<GridPos> overlap = new HashSet<>(l.carve());
                    overlap.retainAll(l.framing());
                    assertTrue(overlap.isEmpty(), "both=" + both + " hall=" + hall);
                }
            }
        }

        @Test
        void nothingIsEverPlacedSoTheCountIsJustTheCarve() {
            MallLayout l = layout(true, true);
            assertEquals(l.carve().size(), l.counts().minedTotal());
            assertEquals(l.carve().size() + l.framing().size(), l.counts().envelopeTotal());
        }

        @Test
        void everyFacingProducesTheSameCounts() {
            for (Facing f : Facing.values()) {
                MallAnchor a = new MallAnchor(new GridPos(-77, 12, 300), f, 2);
                MallLayout l = new MallLayout(a, new MallSpec(true, true, 3));
                assertEquals(647, l.carve().size(), "facing " + f);
                assertEquals(88, l.framing().size(), "facing " + f);
            }
        }

        @Test
        void aWiderCorridorPushesTheOppositeRoomFurtherBack() {
            for (int depth = 1; depth <= 6; depth++) {
                MallSpec spec = new MallSpec(true, true, depth);
                RoomPlacement opposite = ANCHOR.oppositeRoom(spec);
                assertEquals(2 - (depth + 1), ANCHOR.alongOf(opposite.openingCentre()), "hallDepth " + depth);
            }
        }

        @Test
        void theCarveSpansExactlyTheEnvelopeHeight() {
            MallLayout l = layout(false, true);
            assertTrue(l.carve().stream().allMatch(p -> p.y() >= 63 && p.y() <= 69));
            assertEquals(63, ANCHOR.floorPlateY());
            assertEquals(69, ANCHOR.ceilingPlateY());
        }
    }

    @Nested
    @DisplayName("the player's own position")
    class PlayerPosition {

        @Test
        void thePlayerIsStandingInTheCorridorSegment() {
            MallLayout l = layout(false, true);
            assertTrue(l.carve().contains(ANCHOR.playerFeet()), "feet block is corridor air");
        }

        @Test
        void theFloorUnderTheirFeetIsCarvedNotLeft() {
            // The floor recess goes one below the walking surface, which is why the job ends with a
            // one-block drop.
            assertTrue(layout(false, true).carve().contains(ANCHOR.playerFeet().plus(0, -1, 0)));
        }

        @Test
        void theRoomStartsWhereTheWallIs() {
            MallLayout l = layout(false, false);
            // openingDistance is 2, so the first carved room cell is 2 ahead.
            assertTrue(l.carve().contains(ANCHOR.cell(2, 0, 64)));
            assertFalse(l.carve().contains(ANCHOR.cell(1, 0, 64)), "the corridor is not the room");
        }
    }
}
