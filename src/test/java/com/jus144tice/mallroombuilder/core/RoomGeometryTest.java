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

/**
 * The room spec, made executable. If a count here changes, the mod is carving something other than
 * what was designed.
 */
class RoomGeometryTest {

    private static final RoomPlacement ROOM = new RoomPlacement(new GridPos(100, 64, -250), Facing.SOUTH);

    @Nested
    @DisplayName("the headline counts")
    class Counts {

        @Test
        void interiorIs5Cubed() {
            assertEquals(125, RoomGeometry.interior(ROOM).size());
        }

        @Test
        void theFiveFaceRecessesAre125() {
            // back 25 + two pillars 50 + ceiling 25 + floor 25
            assertEquals(125, RoomGeometry.faceRecesses(ROOM).size());
        }

        @Test
        void framingIs44() {
            assertEquals(44, RoomGeometry.framing(ROOM).size());
            // 4 corner runs of 5 slices, plus the whole back-plane rim.
            assertEquals(4 * 5 + (7 * 7 - 25), RoomGeometry.framing(ROOM).size());
        }

        @Test
        void envelopeIs6By7By7() {
            assertEquals(294, RoomGeometry.envelope(ROOM).size());
            assertEquals(6 * 7 * 7, RoomGeometry.envelope(ROOM).size());
        }

        @Test
        void carvedTotalIs250() {
            int carved = RoomGeometry.interior(ROOM).size()
                    + RoomGeometry.faceRecesses(ROOM).size();
            assertEquals(250, carved);
            assertEquals(294, carved + RoomGeometry.framing(ROOM).size());
        }
    }

    @Nested
    @DisplayName("rough versus finish")
    class RoughVersusFinish {

        @Test
        void aFinishJobCarves250AndARoughJobCarves125() {
            assertEquals(250, RoomGeometry.carve(ROOM, true).size());
            assertEquals(125, RoomGeometry.carve(ROOM, false).size());
        }

        @Test
        void roughIsExactlyTheInterior() {
            assertEquals(RoomGeometry.interior(ROOM), RoomGeometry.carve(ROOM, false));
        }

        @Test
        void theDifferenceIsExactlyTheFaceRecesses() {
            Set<GridPos> extra = new HashSet<>(RoomGeometry.carve(ROOM, true));
            extra.removeAll(RoomGeometry.carve(ROOM, false));
            assertEquals(RoomGeometry.faceRecesses(ROOM), extra);
        }

        @Test
        void roughIsASubsetOfFinish() {
            // Which is what lets a room be roughed out now and finished by re-running it later.
            assertTrue(RoomGeometry.carve(ROOM, true).containsAll(RoomGeometry.carve(ROOM, false)));
        }

        @Test
        void aRoughJobNeverTouchesTheFloorYouStandOn() {
            assertTrue(RoomGeometry.carve(ROOM, false).stream().noneMatch(p -> p.y() == ROOM.floorPlateY()));
            assertTrue(RoomGeometry.carve(ROOM, true).stream().anyMatch(p -> p.y() == ROOM.floorPlateY()));
        }

        @Test
        void neitherModeEverTouchesFraming() {
            for (boolean finish : new boolean[] {false, true}) {
                assertTrue(RoomGeometry.carve(ROOM, finish).stream().noneMatch(RoomGeometry.framing(ROOM)::contains));
            }
        }
    }

    @Nested
    @DisplayName("the open front")
    class OpenFront {

        @Test
        void theOpeningPlaneHasNoBackWall() {
            // Every interior cell at depth 0 is carved -- there is no plate across the front.
            for (int s = -2; s <= 2; s++) {
                for (int dy = 0; dy <= 4; dy++) {
                    assertEquals(0, RoomGeometry.extremeCount(0, s, dy), "d=0 s=" + s + " dy=" + dy);
                }
            }
        }

        @Test
        void onlyTheBackPlaneCountsOnTheDepthAxis() {
            // If the front were an extreme too, the room would be walled in and skin would be 150.
            for (int d = 0; d < RoomGeometry.BACK_PLATE_DEPTH; d++) {
                assertEquals(0, RoomGeometry.extremeCount(d, 0, 0), "depth " + d + " must not be an extreme");
            }
            assertEquals(1, RoomGeometry.extremeCount(RoomGeometry.BACK_PLATE_DEPTH, 0, 0));
        }

        @Test
        void thePillarsReachTheOpeningPlane() {
            // The visible pillar framing each opening starts at depth 0.
            assertTrue(RoomGeometry.faceRecesses(ROOM).contains(ROOM.cell(0, 3, ROOM.floorY())));
            assertTrue(RoomGeometry.faceRecesses(ROOM).contains(ROOM.cell(0, -3, ROOM.floorY())));
        }
    }

    @Nested
    @DisplayName("the partition is exact")
    class Partition {

        @Test
        void interiorRecessesAndFramingTileTheEnvelope() {
            Set<GridPos> union = new HashSet<>();
            union.addAll(RoomGeometry.interior(ROOM));
            union.addAll(RoomGeometry.faceRecesses(ROOM));
            union.addAll(RoomGeometry.framing(ROOM));

            assertEquals(RoomGeometry.envelope(ROOM), union);
            assertEquals(
                    294,
                    RoomGeometry.interior(ROOM).size()
                            + RoomGeometry.faceRecesses(ROOM).size()
                            + RoomGeometry.framing(ROOM).size());
        }

        @Test
        void interiorAndRecessesAreDisjoint() {
            Set<GridPos> both = new HashSet<>(RoomGeometry.interior(ROOM));
            both.retainAll(RoomGeometry.faceRecesses(ROOM));
            assertTrue(both.isEmpty());
        }

        @Test
        void framingIsNeverAlsoCarved() {
            Set<GridPos> carved = new HashSet<>(RoomGeometry.interior(ROOM));
            carved.addAll(RoomGeometry.faceRecesses(ROOM));
            assertTrue(RoomGeometry.framing(ROOM).stream().noneMatch(carved::contains));
        }
    }

    @Nested
    @DisplayName("the cross-section is a 7x7 with its corners left standing")
    class CrossSection {

        @Test
        void eachInteriorSliceCarves45Of49() {
            for (int d = 0; d < RoomGeometry.BACK_PLATE_DEPTH; d++) {
                int carved = 0;
                int framing = 0;
                for (int s = -3; s <= 3; s++) {
                    for (int dy = -1; dy <= 5; dy++) {
                        if (RoomGeometry.extremeCount(d, s, dy) <= 1) {
                            carved++;
                        } else {
                            framing++;
                        }
                    }
                }
                assertEquals(45, carved, "slice " + d);
                assertEquals(4, framing, "slice " + d + ": only the four corners stay");
            }
        }

        @Test
        void theFourCornersOfASliceAreTheFraming() {
            for (int s : new int[] {-3, 3}) {
                for (int dy : new int[] {-1, 5}) {
                    assertEquals(2, RoomGeometry.extremeCount(2, s, dy), "corner s=" + s + " dy=" + dy);
                }
            }
        }
    }

    @Nested
    @DisplayName("placement in the world")
    class Placement {

        @Test
        void interiorSitsAtAndAboveTheFloorLevel() {
            Set<GridPos> interior = RoomGeometry.interior(ROOM);
            assertTrue(interior.stream().allMatch(p -> p.y() >= ROOM.floorY() && p.y() <= ROOM.floorY() + 4));
        }

        @Test
        void theRecessesReachOneBelowAndFiveAbove() {
            Set<GridPos> recesses = RoomGeometry.faceRecesses(ROOM);
            assertTrue(recesses.stream().anyMatch(p -> p.y() == ROOM.floorPlateY()));
            assertTrue(recesses.stream().anyMatch(p -> p.y() == ROOM.ceilingPlateY()));
            assertEquals(63, ROOM.floorPlateY());
            assertEquals(69, ROOM.ceilingPlateY());
        }

        @Test
        void theOpeningCentreIsCarvedInterior() {
            assertTrue(RoomGeometry.interior(ROOM).contains(ROOM.openingCentre()));
        }

        @Test
        void nothingSitsInFrontOfTheOpening() {
            Set<GridPos> all = RoomGeometry.envelope(ROOM);
            assertFalse(all.contains(ROOM.cell(-1, 0, ROOM.floorY())), "the room never reaches into the hallway");
        }

        @Test
        void geometryHoldsForEveryFacingAndNegativeCoordinates() {
            for (Facing f : Facing.values()) {
                RoomPlacement room = new RoomPlacement(new GridPos(-4000, -59, 7), f);
                assertEquals(125, RoomGeometry.interior(room).size(), "facing " + f);
                assertEquals(125, RoomGeometry.faceRecesses(room).size(), "facing " + f);
                assertEquals(44, RoomGeometry.framing(room).size(), "facing " + f);
            }
        }

        @Test
        void depthAndSideInvertCell() {
            for (Facing f : Facing.values()) {
                RoomPlacement room = new RoomPlacement(new GridPos(12, 70, -34), f);
                for (int d = 0; d <= 5; d++) {
                    for (int s = -3; s <= 3; s++) {
                        GridPos p = room.cell(d, s, 70);
                        assertEquals(d, room.depthOf(p), "facing " + f);
                        assertEquals(s, room.sideOf(p), "facing " + f);
                    }
                }
            }
        }
    }
}
