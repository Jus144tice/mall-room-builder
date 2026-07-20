/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The fill phase: what gets placed, in what order, out of which slot. */
class FillOrderTest {

    private static final MallAnchor ANCHOR = MallAnchor.of(0, 64, 0, 0.0f);

    private static final FillPlan ALL = FillPlan.builder()
            .with(Surface.FLOOR, 3)
            .with(Surface.WALLS, 5)
            .with(Surface.CEILING, 4)
            .with(Surface.BEAM, 6)
            .build();

    private static MallLayout room(boolean bothSides, FillPlan plan) {
        return new MallLayout(ANCHOR, MallSpec.room(bothSides, 3, true).withFill(plan));
    }

    private static MallLayout spine(int length, FillPlan plan) {
        return new MallLayout(ANCHOR, MallSpec.spine(length, 3, true).withFill(plan));
    }

    private static List<Surface> surfaceRun(List<MallLayout.FillCell> order) {
        List<Surface> runs = new ArrayList<>();
        for (MallLayout.FillCell cell : order) {
            if (runs.isEmpty() || runs.get(runs.size() - 1) != cell.surface()) {
                runs.add(cell.surface());
            }
        }
        return runs;
    }

    @Nested
    @DisplayName("coverage")
    class Coverage {

        @Test
        void aFullyFilledRoomPlaces125() {
            assertEquals(125, room(false, ALL).fillOrder().size());
            assertEquals(125, room(false, ALL).counts().placedTotal());
        }

        @Test
        void bothSidesDoublesIt() {
            assertEquals(250, room(true, ALL).fillOrder().size());
        }

        @Test
        void aPartialPlanPlacesOnlyWhatItNames() {
            FillPlan floorOnly = FillPlan.builder().with(Surface.FLOOR, 3).build();
            assertEquals(25, room(false, floorOnly).fillOrder().size());

            FillPlan floorAndBeam = FillPlan.builder()
                    .with(Surface.FLOOR, 3)
                    .with(Surface.BEAM, 6)
                    .build();
            assertEquals(30, room(false, floorAndBeam).fillOrder().size());
        }

        @Test
        void aSpineFillIsFloorAndCeilingOnly() {
            FillPlan plan = FillPlan.builder()
                    .with(Surface.FLOOR, 3)
                    .with(Surface.CEILING, 4)
                    .with(Surface.WALLS, 5)
                    .with(Surface.BEAM, 6)
                    .build();
            assertEquals(42, spine(7, plan).fillOrder().size(), "walls and beam contribute nothing to a corridor");
        }

        @Test
        void noFillPlanMeansNoFillWork() {
            assertTrue(new MallLayout(ANCHOR, MallSpec.room(false, 3, true))
                    .fillOrder()
                    .isEmpty());
        }

        @Test
        void everyFilledCellIsAlsoACarvedCell() {
            // You cannot fill a recess that was never cut.
            MallLayout l = room(true, ALL);
            for (MallLayout.FillCell cell : l.fillOrder()) {
                assertTrue(l.carve().contains(cell.pos()), "fill target " + cell.pos() + " is not carved");
            }
        }

        @Test
        void noCellIsFilledTwice() {
            List<MallLayout.FillCell> order = room(true, ALL).fillOrder();
            assertEquals(
                    order.size(),
                    new HashSet<>(order.stream().map(MallLayout.FillCell::pos).toList()).size());
        }
    }

    @Nested
    @DisplayName("order")
    class Order {

        @Test
        void surfacesRunFloorThenWallsThenCeilingThenBeam() {
            // Floor first restores the level the carve dropped you from; ceiling and beam go last,
            // placed from below where they are always in reach.
            assertEquals(
                    List.of(Surface.FLOOR, Surface.WALLS, Surface.CEILING, Surface.BEAM),
                    surfaceRun(room(false, ALL).fillOrder()));
        }

        @Test
        void eachSurfaceIsOneContiguousRun() {
            // Otherwise the hotbar would thrash between materials.
            List<Surface> runs = surfaceRun(room(true, ALL).fillOrder());
            assertEquals(new HashSet<>(runs).size(), runs.size(), "a surface is never revisited");
        }

        @Test
        void theFloorIsLaidFarToNearSoYouBackOutOfTheRoom() {
            List<MallLayout.FillCell> order = room(false, ALL).fillOrder();
            RoomPlacement place = ANCHOR.facedRoom();
            int previous = Integer.MAX_VALUE;
            for (MallLayout.FillCell cell : order) {
                if (cell.surface() != Surface.FLOOR) {
                    continue;
                }
                int depth = place.depthOf(cell.pos());
                assertTrue(depth <= previous, "floor should run far to near, saw " + depth + " after " + previous);
                previous = depth;
            }
            assertEquals(0, previous, "and end at the entrance");
        }

        @Test
        void theCeilingRunsNearToFar() {
            List<MallLayout.FillCell> order = room(false, ALL).fillOrder();
            RoomPlacement place = ANCHOR.facedRoom();
            int previous = Integer.MIN_VALUE;
            for (MallLayout.FillCell cell : order) {
                if (cell.surface() != Surface.CEILING) {
                    continue;
                }
                int depth = place.depthOf(cell.pos());
                assertTrue(depth >= previous, "ceiling should run near to far");
                previous = depth;
            }
        }

        @Test
        void aPartialPlanKeepsTheRelativeOrder() {
            FillPlan plan = FillPlan.builder()
                    .with(Surface.BEAM, 6)
                    .with(Surface.FLOOR, 3)
                    .build();
            assertEquals(
                    List.of(Surface.FLOOR, Surface.BEAM),
                    surfaceRun(room(false, plan).fillOrder()));
        }
    }

    @Nested
    @DisplayName("fill-only jobs")
    class FillOnly {

        @Test
        void carveNothingButStillKnowEverySurface() {
            MallSpec spec = MallSpec.room(false, 3, true).fillOnly(ALL);
            MallLayout l = new MallLayout(ANCHOR, spec);

            assertFalse(spec.carve());
            assertTrue(l.carve().isEmpty(), "nothing is queued to mine");
            assertEquals(125, l.fillOrder().size(), "but the whole surface set is still filled");
            assertEquals(0, l.counts().minedTotal());
            assertEquals(125, l.counts().placedTotal());
        }

        @Test
        void framingIsStillProtected() {
            // The backfill watcher needs it even when nothing is being carved.
            MallLayout l = new MallLayout(ANCHOR, MallSpec.room(false, 3, true).fillOnly(ALL));
            assertEquals(44, l.framing().size());
        }

        @Test
        void aSpecThatNeitherCarvesNorFillsIsRejectedAtTheEdge() {
            MallSpec empty = MallSpec.room(false, 3, true).fillOnly(FillPlan.none());
            assertFalse(empty.doesSomething());
            assertTrue(MallSpec.room(false, 3, true).doesSomething());
            assertTrue(empty.withFill(ALL).doesSomething());
        }

        @Test
        void modeNameSaysWhatWillHappen() {
            assertEquals("finish", MallSpec.room(false, 3, true).modeName());
            assertEquals("rough", MallSpec.room(false, 3, false).modeName());
            assertEquals(
                    "finish+fill", MallSpec.room(false, 3, true).withFill(ALL).modeName());
            assertEquals(
                    "rough+fill", MallSpec.room(false, 3, false).withFill(ALL).modeName());
            assertEquals("fill", MallSpec.room(false, 3, true).fillOnly(ALL).modeName());
        }
    }
}
