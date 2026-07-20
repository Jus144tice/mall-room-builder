/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The fillable surfaces, and the plan that assigns hotbar slots to them. */
class SurfaceTest {

    private static final RoomPlacement ROOM = new RoomPlacement(new GridPos(0, 64, 1), Facing.SOUTH);
    private static final MallAnchor ANCHOR = MallAnchor.of(0, 64, 0, 0.0f);

    @Nested
    @DisplayName("a room's surfaces partition its face recesses exactly")
    class RoomPartition {

        @Test
        void theCountsAre25_75_20_5() {
            assertEquals(25, RoomGeometry.surface(ROOM, Surface.FLOOR).size());
            assertEquals(75, RoomGeometry.surface(ROOM, Surface.WALLS).size(), "back 25 + two side walls 50");
            assertEquals(20, RoomGeometry.surface(ROOM, Surface.CEILING).size(), "ceiling less the beam row");
            assertEquals(5, RoomGeometry.surface(ROOM, Surface.BEAM).size());
        }

        @Test
        void togetherTheyAreExactlyTheFaceRecesses() {
            Set<GridPos> union = new HashSet<>();
            int total = 0;
            for (Surface s : Surface.forRoom()) {
                Set<GridPos> cells = RoomGeometry.surface(ROOM, s);
                total += cells.size();
                union.addAll(cells);
            }
            assertEquals(125, total, "no cell counted twice");
            assertEquals(RoomGeometry.faceRecesses(ROOM), union);
        }

        @Test
        void theyNeverOverlapEachOther() {
            for (Surface a : Surface.forRoom()) {
                for (Surface b : Surface.forRoom()) {
                    if (a == b) {
                        continue;
                    }
                    Set<GridPos> both = new HashSet<>(RoomGeometry.surface(ROOM, a));
                    both.retainAll(RoomGeometry.surface(ROOM, b));
                    assertTrue(both.isEmpty(), a + " overlaps " + b);
                }
            }
        }

        @Test
        void noSurfaceEverTouchesFraming() {
            for (Surface s : Surface.forRoom()) {
                assertTrue(RoomGeometry.surface(ROOM, s).stream().noneMatch(RoomGeometry.framing(ROOM)::contains));
            }
        }
    }

    @Nested
    @DisplayName("the beam")
    class Beam {

        @Test
        void isTheCeilingRowAtTheEntrancePlane() {
            Set<GridPos> beam = RoomGeometry.surface(ROOM, Surface.BEAM);
            assertTrue(beam.stream().allMatch(p -> ROOM.depthOf(p) == 0), "at the opening");
            assertTrue(beam.stream().allMatch(p -> p.y() == ROOM.ceilingPlateY()), "in the ceiling plane");
            assertTrue(beam.stream().allMatch(p -> Math.abs(ROOM.sideOf(p)) <= 2), "5 wide, not over the pillars");
        }

        @Test
        void theCeilingStartsOneDeeperSoTheyDoNotClash() {
            Set<GridPos> ceiling = RoomGeometry.surface(ROOM, Surface.CEILING);
            assertTrue(ceiling.stream().allMatch(p -> ROOM.depthOf(p) >= 1));
            assertEquals(4 * 5, ceiling.size(), "depths 1..4, five wide");
        }

        @Test
        void theCellsOverThePillarsStayFraming() {
            // Those are two-extreme cells, so they are never carved and never filled.
            assertTrue(RoomGeometry.framing(ROOM).contains(ROOM.cell(0, 3, ROOM.ceilingPlateY())));
            assertTrue(RoomGeometry.framing(ROOM).contains(ROOM.cell(0, -3, ROOM.ceilingPlateY())));
        }
    }

    @Nested
    @DisplayName("a spine segment has only a floor and a ceiling")
    class SpineSurfaces {

        @Test
        void floorAndCeilingAreThreeWide() {
            assertEquals(
                    21,
                    SpineGeometry.surface(ANCHOR.spineStart(), 7, Surface.FLOOR).size());
            assertEquals(
                    21,
                    SpineGeometry.surface(ANCHOR.spineStart(), 7, Surface.CEILING)
                            .size());
        }

        @Test
        void wallsAndBeamAreEmpty() {
            assertTrue(
                    SpineGeometry.surface(ANCHOR.spineStart(), 7, Surface.WALLS).isEmpty());
            assertTrue(
                    SpineGeometry.surface(ANCHOR.spineStart(), 7, Surface.BEAM).isEmpty());
        }

        @Test
        void togetherTheyAreExactlyTheRecesses() {
            Set<GridPos> union = new HashSet<>(SpineGeometry.surface(ANCHOR.spineStart(), 7, Surface.FLOOR));
            union.addAll(SpineGeometry.surface(ANCHOR.spineStart(), 7, Surface.CEILING));
            assertEquals(SpineGeometry.recesses(ANCHOR.spineStart(), 7), union);
            assertEquals(42, union.size());
        }

        @Test
        void onlyFloorAndCeilingAreOffered() {
            assertEquals(List.of(Surface.FLOOR, Surface.CEILING), Surface.forSpine());
            assertEquals(Surface.forSpine(), Surface.forKind(MallSpec.Kind.SPINE));
            assertEquals(Surface.forRoom(), Surface.forKind(MallSpec.Kind.ROOM));
        }
    }

    @Nested
    @DisplayName("FillPlan")
    class Plan {

        @Test
        void mapsOneBasedSlotsToZeroBasedIndices() {
            FillPlan plan = FillPlan.builder().with(Surface.FLOOR, 3).build();
            assertEquals(3, plan.slot(Surface.FLOOR));
            assertEquals(2, plan.inventoryIndex(Surface.FLOOR), "hotbar 3 is inventory index 2");
        }

        @Test
        void aSurfaceWithNoSlotIsSimplyNotFilled() {
            FillPlan plan = FillPlan.builder().with(Surface.FLOOR, 3).build();
            assertTrue(plan.covers(Surface.FLOOR));
            assertFalse(plan.covers(Surface.CEILING));
            assertEquals(List.of(Surface.FLOOR), plan.surfaces());
        }

        @Test
        void aNullSlotIsIgnoredSoPartialPlansJustWork() {
            FillPlan plan = FillPlan.builder()
                    .with(Surface.FLOOR, 3)
                    .with(Surface.CEILING, null)
                    .build();
            assertEquals(List.of(Surface.FLOOR), plan.surfaces());
        }

        @Test
        void surfacesComeBackInFillOrderNotTypingOrder() {
            FillPlan plan = FillPlan.builder()
                    .with(Surface.BEAM, 6)
                    .with(Surface.CEILING, 4)
                    .with(Surface.FLOOR, 3)
                    .with(Surface.WALLS, 5)
                    .build();
            assertEquals(List.of(Surface.FLOOR, Surface.WALLS, Surface.CEILING, Surface.BEAM), plan.surfaces());
        }

        @Test
        void rejectsSlotsOutsideTheHotbar() {
            assertThrows(
                    IllegalArgumentException.class, () -> FillPlan.builder().with(Surface.FLOOR, 0));
            assertThrows(
                    IllegalArgumentException.class, () -> FillPlan.builder().with(Surface.FLOOR, 10));
        }

        @Test
        void noneIsEmpty() {
            assertTrue(FillPlan.none().isEmpty());
            assertTrue(FillPlan.builder().build().isEmpty());
            assertThrows(IllegalStateException.class, () -> FillPlan.none().inventoryIndex(Surface.FLOOR));
        }
    }
}
