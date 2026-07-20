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

/** Composition: a room off the spine, a mirrored pair, or a segment of spine. */
class MallLayoutTest {

    /** Standing on the spine facing south, the room starting at the very next block. */
    private static final MallAnchor ANCHOR = MallAnchor.of(0, 64, 0, 0.0f);

    private static MallLayout room(boolean bothSides) {
        return new MallLayout(ANCHOR, MallSpec.room(bothSides, 3));
    }

    private static MallLayout spine(int length) {
        return new MallLayout(ANCHOR, MallSpec.spine(length, 3));
    }

    @Nested
    @DisplayName("a single room")
    class SingleRoom {

        @Test
        void is250CarvedAnd44Framing() {
            MallLayout l = room(false);
            assertEquals(250, l.carve().size());
            assertEquals(44, l.framing().size());
            assertEquals(250, l.counts().minedTotal());
            assertEquals(294, l.counts().envelopeTotal());
        }

        @Test
        void itStartsAtTheBlockDirectlyInFrontOfThePlayer() {
            MallLayout l = room(false);
            assertTrue(l.carve().contains(ANCHOR.cell(1, 0, 64)), "the very next block is the room");
            assertFalse(l.carve().contains(ANCHOR.playerFeet()), "the player's own block stays on the spine");
        }

        @Test
        void theRoomIsCentredOnThePlayerLaterally() {
            MallLayout l = room(false);
            assertTrue(l.carve().contains(ANCHOR.cell(1, 2, 64)));
            assertTrue(l.carve().contains(ANCHOR.cell(1, -2, 64)));
            // Side 3 is the pillar -- still carved, it is a face recess.
            assertTrue(l.carve().contains(ANCHOR.cell(1, 3, 64)));
            assertFalse(l.carve().contains(ANCHOR.cell(1, 4, 64)));
        }

        @Test
        void theCarveReachesOneBelowAndFiveAboveTheFeet() {
            MallLayout l = room(false);
            assertTrue(l.carve().stream().allMatch(p -> p.y() >= 63 && p.y() <= 69));
            assertTrue(l.carve().contains(ANCHOR.cell(1, 0, 63)), "floor recess");
            assertTrue(l.carve().contains(ANCHOR.cell(1, 0, 69)), "ceiling recess");
        }
    }

    @Nested
    @DisplayName("both sides of the spine")
    class BothSides {

        @Test
        void isTwoRoomsAndNothingShared() {
            MallLayout l = room(true);
            assertEquals(500, l.carve().size());
            assertEquals(88, l.framing().size());
        }

        @Test
        void theRoomsFaceEachOtherAcrossTheCorridor() {
            MallSpec spec = MallSpec.room(true, 3);
            RoomPlacement faced = ANCHOR.facedRoom();
            RoomPlacement opposite = ANCHOR.oppositeRoom(spec);

            assertEquals(ANCHOR.facing(), faced.depth());
            assertEquals(ANCHOR.facing().opposite(), opposite.depth());
            // Openings 4 apart: 3 lanes of corridor between two wall planes.
            assertEquals(4, ANCHOR.alongOf(faced.openingCentre()) - ANCHOR.alongOf(opposite.openingCentre()));
        }

        @Test
        void theTwoRoomsNeverOverlap() {
            MallSpec spec = MallSpec.room(true, 3);
            Set<GridPos> a = new HashSet<>(RoomGeometry.envelope(ANCHOR.facedRoom()));
            assertTrue(RoomGeometry.envelope(ANCHOR.oppositeRoom(spec)).stream().noneMatch(a::contains));
        }

        @Test
        void aWiderCorridorPushesTheOppositeRoomFurtherBack() {
            for (int depth = 1; depth <= 6; depth++) {
                RoomPlacement opposite = ANCHOR.oppositeRoom(MallSpec.room(true, depth));
                assertEquals(
                        MallAnchor.START_OFFSET - (depth + 1),
                        ANCHOR.alongOf(opposite.openingCentre()),
                        "hallDepth " + depth);
            }
        }
    }

    @Nested
    @DisplayName("a spine segment")
    class Spine {

        @Test
        void isAPlainBoxWithNoFraming() {
            MallLayout l = spine(7);
            assertEquals(105, l.carve().size());
            assertTrue(l.framing().isEmpty(), "a corridor is a corridor -- nothing is left standing");
            assertEquals(105, l.counts().minedTotal());
        }

        @Test
        void itCarvesNoRooms() {
            assertEquals(0, MallSpec.spine(7, 3).roomCount());
        }

        @Test
        void lengthIsHonoured() {
            assertEquals(15, spine(1).carve().size());
            assertEquals(150, spine(10).carve().size());
        }

        @Test
        void itNeverTouchesTheFloorYouAreStandingOn() {
            assertTrue(spine(7).carve().stream().noneMatch(p -> p.y() == ANCHOR.floorPlateY()));
        }
    }

    @Nested
    @DisplayName("invariants")
    class Invariants {

        @Test
        void framingAndCarveAreAlwaysDisjoint() {
            for (MallLayout l : new MallLayout[] {room(false), room(true), spine(7)}) {
                Set<GridPos> overlap = new HashSet<>(l.carve());
                overlap.retainAll(l.framing());
                assertTrue(overlap.isEmpty());
            }
        }

        @Test
        void nothingIsEverPlacedSoTheCountIsJustTheCarve() {
            MallLayout l = room(true);
            assertEquals(l.carve().size(), l.counts().minedTotal());
            assertEquals(l.carve().size() + l.framing().size(), l.counts().envelopeTotal());
        }

        @Test
        void everyFacingProducesTheSameCounts() {
            for (Facing f : Facing.values()) {
                MallAnchor a = new MallAnchor(new GridPos(-77, 12, 300), f);
                assertEquals(
                        500, new MallLayout(a, MallSpec.room(true, 3)).carve().size(), "facing " + f);
                assertEquals(
                        105, new MallLayout(a, MallSpec.spine(7, 3)).carve().size(), "facing " + f);
            }
        }

        @Test
        void theSameStandingSpotAlwaysDescribesTheSameVolume() {
            // This is what makes a partial room resumable: no world state feeds the geometry, so
            // re-running from the same block re-derives exactly the same job.
            assertEquals(room(false).carve(), room(false).carve());
            assertEquals(
                    new MallLayout(MallAnchor.of(0, 64, 0, 0.0f), MallSpec.room(false, 3)).carve(),
                    new MallLayout(MallAnchor.of(0, 64, 0, 12.0f), MallSpec.room(false, 3)).carve(),
                    "any yaw that snaps to the same cardinal gives the same room");
        }
    }
}
