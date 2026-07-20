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

/** Composition: rooms plus hallways, with air winning so doorways cut themselves. */
class MallLayoutTest {

    private static final MallAnchor ANCHOR = MallAnchor.of(0, 64, 0, 0.0f); // SOUTH

    private static MallLayout layout(int rooms, int hallLength, boolean thresholds) {
        return new MallLayout(ANCHOR, new MallSpec(rooms, hallLength, thresholds));
    }

    @Nested
    @DisplayName("a single room matches the isolated-room spec exactly")
    class SingleRoom {

        @Test
        void airAndSkinAndMinedAre125_150_275() {
            MallLayout l = layout(1, 5, true);
            assertEquals(125, l.air().size());
            assertEquals(150, l.skin().size());
            assertEquals(275, l.counts().minedTotal());
        }

        @Test
        void noHallwayIsGeneratedForOneRoom() {
            assertEquals(0, new MallSpec(1, 5, true).hallCount());
        }
    }

    @Nested
    @DisplayName("two rooms joined by a hallway")
    class TwoRooms {

        @Test
        void composedCountsAre355Air_362Skin_717Mined() {
            MallLayout l = layout(2, 5, true);
            assertEquals(355, l.air().size());
            assertEquals(362, l.skin().size());
            assertEquals(717, l.counts().minedTotal());
        }

        @Test
        void airIsTwoInteriorsPlusTheTube() {
            MallLayout l = layout(2, 5, true);
            assertEquals(2 * 125 + 105, l.air().size());
        }

        @Test
        void doorwaysRemoveExactlyThirtySkinBlocks() {
            // Two isolated rooms plus the hallway's own skin, before air subtraction.
            int naive = 2 * 150 + 92;
            assertEquals(392, naive);
            assertEquals(naive - 30, layout(2, 5, true).skin().size(), "15 cells per pierced wall plate, twice");
        }

        @Test
        void eachPiercedWallPlateKeepsATenBlockJamb() {
            MallLayout l = layout(2, 5, true);
            // Room 0's far wall plate sits at along = 3. Of its 25 cells, 15 became doorway air.
            long jamb = l.skin().stream()
                    .filter(p -> ANCHOR.alongOf(p) == 3)
                    .filter(p -> p.y() >= 64 && p.y() <= 68)
                    .count();
            assertEquals(10, jamb);
        }
    }

    @Nested
    @DisplayName("invariants that must hold for every shape")
    class Invariants {

        @Test
        void airAndSkinAreAlwaysDisjoint() {
            for (int rooms = 1; rooms <= 4; rooms++) {
                for (int hall = 1; hall <= 7; hall++) {
                    MallLayout l = layout(rooms, hall, true);
                    Set<GridPos> both = new HashSet<>(l.air());
                    both.retainAll(l.skin());
                    assertTrue(both.isEmpty(), "rooms=" + rooms + " hall=" + hall);
                }
            }
        }

        @Test
        void framingIsNeverTouched() {
            MallLayout l = layout(1, 5, true);
            Set<GridPos> framing = RoomGeometry.framing(ANCHOR.roomReference(l.spec(), 0));
            assertEquals(68, framing.size());
            assertTrue(framing.stream()
                    .noneMatch(p -> l.air().contains(p) || l.skin().contains(p)));
        }

        @Test
        void adjacentRoomEnvelopesNeverOverlap() {
            MallSpec s = new MallSpec(3, 1, true); // tightest legal spacing
            Set<GridPos> seen = new HashSet<>();
            for (int i = 0; i < s.roomCount(); i++) {
                for (GridPos p : RoomGeometry.envelope(ANCHOR.roomReference(s, i))) {
                    assertTrue(seen.add(p), "room envelopes overlap at " + p);
                }
            }
        }

        @Test
        void countsGrowMonotonicallyWithRoomCount() {
            int previous = 0;
            for (int rooms = 1; rooms <= 6; rooms++) {
                int mined = layout(rooms, 5, true).counts().minedTotal();
                assertTrue(mined > previous, "rooms=" + rooms);
                previous = mined;
            }
        }

        @Test
        void everyFacingProducesTheSameCounts() {
            for (Facing f : Facing.values()) {
                MallLayout l = new MallLayout(new MallAnchor(new GridPos(-77, 12, 300), f), new MallSpec(3, 5, true));
                assertEquals(585, l.air().size(), "facing " + f);
                assertEquals(574, l.skin().size(), "facing " + f);
            }
        }

        @Test
        void turningOffThresholdsRemovesTwelvePerHallway() {
            assertEquals(
                    24,
                    layout(3, 5, true).skin().size()
                            - layout(3, 5, false).skin().size(),
                    "two hallways, 12 blocks each");
        }
    }

    @Nested
    @DisplayName("the player's own position")
    class PlayerPosition {

        @Test
        void thePlayerStandsInsideRoomZero() {
            MallLayout l = layout(2, 5, true);
            assertTrue(l.air().contains(ANCHOR.playerFeet()), "feet block is interior air");
            assertTrue(l.skin().contains(ANCHOR.playerFeet().plus(0, -1, 0)), "standing on floor skin");
        }

        @Test
        void theBlockAboveTheFeetIsAlsoInterior() {
            assertTrue(layout(1, 5, true).air().contains(ANCHOR.playerFeet().plus(0, 1, 0)));
        }

        @Test
        void theCeilingPlateIsFiveAboveTheFeet() {
            assertTrue(layout(1, 5, true).skin().contains(ANCHOR.playerFeet().plus(0, 5, 0)));
            assertFalse(layout(1, 5, true).air().contains(ANCHOR.playerFeet().plus(0, 5, 0)));
        }
    }
}
