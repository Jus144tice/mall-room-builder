/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The room spec, made executable. If any count here changes, the mod is building something other
 * than what was designed.
 */
class RoomGeometryTest {

    private static final GridPos REF = new GridPos(100, 64, -250);

    @Nested
    @DisplayName("the headline counts")
    class Counts {

        @Test
        void interiorIs5Cubed() {
            assertEquals(125, RoomGeometry.interior(REF).size());
        }

        @Test
        void envelopeIs7Cubed() {
            assertEquals(343, RoomGeometry.envelope(REF).size());
        }

        @Test
        void shellIs218() {
            Set<GridPos> shell = new HashSet<>(RoomGeometry.envelope(REF));
            shell.removeAll(RoomGeometry.interior(REF));
            assertEquals(218, shell.size());
        }

        @Test
        void visibleSkinIs150() {
            assertEquals(150, RoomGeometry.visibleSkin(REF).size());
        }

        @Test
        void framingIs68() {
            // 12 edges of 5 cells each, plus 8 corners.
            assertEquals(68, RoomGeometry.framing(REF).size());
            assertEquals(12 * 5 + 8, RoomGeometry.framing(REF).size());
        }

        @Test
        void skinPlusFramingIsTheWholeShell() {
            assertEquals(
                    218,
                    RoomGeometry.visibleSkin(REF).size()
                            + RoomGeometry.framing(REF).size());
        }

        @Test
        void minedPerIsolatedRoomIs275() {
            int mined = RoomGeometry.interior(REF).size()
                    + RoomGeometry.visibleSkin(REF).size();
            assertEquals(275, mined);
        }
    }

    @Nested
    @DisplayName("the partition is exact")
    class Partition {

        @Test
        void interiorSkinAndFramingTileTheEnvelopeExactly() {
            Set<GridPos> union = new HashSet<>();
            union.addAll(RoomGeometry.interior(REF));
            union.addAll(RoomGeometry.visibleSkin(REF));
            union.addAll(RoomGeometry.framing(REF));

            assertEquals(RoomGeometry.envelope(REF), union);
            // No double counting: the sizes must add up as well as the sets matching.
            assertEquals(
                    343,
                    RoomGeometry.interior(REF).size()
                            + RoomGeometry.visibleSkin(REF).size()
                            + RoomGeometry.framing(REF).size());
        }

        @Test
        void interiorAndSkinAreDisjoint() {
            Set<GridPos> both = new HashSet<>(RoomGeometry.interior(REF));
            both.retainAll(RoomGeometry.visibleSkin(REF));
            assertTrue(both.isEmpty());
        }

        @Test
        void everySkinCellSitsAtExactlyOneEnvelopeExtreme() {
            for (GridPos p : RoomGeometry.visibleSkin(REF)) {
                GridPos d = p.minus(REF);
                assertEquals(
                        1,
                        RoomGeometry.extremeCount(d.x(), d.y(), d.z(), RoomGeometry.ENVELOPE_RADIUS),
                        "visible skin cell " + d + " should be on exactly one face plane");
            }
        }

        @Test
        void everyFramingCellSitsAtTwoOrThreeExtremes() {
            for (GridPos p : RoomGeometry.framing(REF)) {
                GridPos d = p.minus(REF);
                int extremes = RoomGeometry.extremeCount(d.x(), d.y(), d.z(), RoomGeometry.ENVELOPE_RADIUS);
                assertTrue(extremes >= 2, "framing cell " + d + " should be an edge or corner, got " + extremes);
            }
        }
    }

    @Nested
    @DisplayName("vertical placement relative to the player")
    class Vertical {

        @Test
        void interiorStartsAtTheReferenceYAndIsFiveTall() {
            Set<GridPos> interior = RoomGeometry.interior(REF);
            assertTrue(interior.stream().allMatch(p -> p.y() >= REF.y() && p.y() <= REF.y() + 4));
            assertTrue(interior.contains(REF), "the reference cell is the player's feet, inside the room");
        }

        @Test
        void envelopeSpansFloorPlateToCeilingPlate() {
            Set<GridPos> envelope = RoomGeometry.envelope(REF);
            assertTrue(envelope.stream().allMatch(p -> p.y() >= REF.y() - 1 && p.y() <= REF.y() + 5));
        }

        @Test
        void theBlockUnderTheReferenceIsFloorSkin() {
            assertTrue(RoomGeometry.visibleSkin(REF).contains(REF.plus(0, -1, 0)));
        }
    }

    @Test
    void geometryIsTranslationInvariantIncludingNegativeCoordinates() {
        GridPos other = new GridPos(-4000, -59, 7);
        assertEquals(150, RoomGeometry.visibleSkin(other).size());
        assertEquals(68, RoomGeometry.framing(other).size());
        assertEquals(125, RoomGeometry.interior(other).size());
    }
}
