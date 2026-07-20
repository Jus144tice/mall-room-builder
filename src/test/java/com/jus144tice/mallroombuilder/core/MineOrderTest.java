/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Ordering safety. These are the assertions that stop a job deadlocking on an unreachable ceiling or
 * digging the floor out from under the player.
 */
class MineOrderTest {

    private static final MallAnchor ANCHOR = MallAnchor.of(0, 64, 0, 0.0f);

    private static MallLayout room(boolean bothSides) {
        return new MallLayout(ANCHOR, MallSpec.room(bothSides, 3));
    }

    private static Map<GridPos, Integer> indexOf(List<GridPos> order) {
        Map<GridPos, Integer> index = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            index.put(order.get(i), i);
        }
        return index;
    }

    @Test
    void isExactlyAPermutationOfTheCarveSet() {
        MallLayout l = room(true);
        List<GridPos> order = l.mineOrder();
        assertEquals(l.carve().size(), order.size(), "no omissions");
        assertEquals(order.size(), new HashSet<>(order).size(), "no duplicates");
        assertEquals(l.carve(), new HashSet<>(order));
    }

    @Test
    void neverQueuesFraming() {
        MallLayout l = room(true);
        assertTrue(l.mineOrder().stream().noneMatch(l.framing()::contains));
    }

    @Nested
    @DisplayName("floor recesses come last, across the whole job")
    class FloorLast {

        @Test
        void everyFloorCellFollowsEveryNonFloorCell() {
            List<GridPos> order = room(true).mineOrder();
            int floorY = ANCHOR.floorPlateY();

            int lastNonFloor = -1;
            int firstFloor = Integer.MAX_VALUE;
            for (int i = 0; i < order.size(); i++) {
                if (order.get(i).y() == floorY) {
                    firstFloor = Math.min(firstFloor, i);
                } else {
                    lastNonFloor = Math.max(lastNonFloor, i);
                }
            }
            assertTrue(
                    firstFloor > lastNonFloor,
                    "carving a floor early would drop the player and cost reach on every ceiling after it");
        }

        @Test
        void noFloorCellIsMinedBeforeTheCellAboveIt() {
            MallLayout l = room(true);
            Map<GridPos, Integer> index = indexOf(l.mineOrder());
            int floorY = ANCHOR.floorPlateY();

            for (GridPos p : index.keySet()) {
                if (p.y() != floorY) {
                    continue;
                }
                Integer above = index.get(p.plus(0, 1, 0));
                if (above != null) {
                    assertTrue(index.get(p) > above, "floor cell " + p + " must follow the cell above it");
                }
            }
        }
    }

    @Nested
    @DisplayName("slice by slice, near to far")
    class SliceOrder {

        @Test
        void aRoomIsCarvedFrontToBack() {
            // The far ceiling is ~5.25 blocks from the opening -- past reach -- and unreachable until
            // the near slices are open. Carving the whole ceiling first would deadlock on block one.
            assertSliceOrder(room(false), ANCHOR.facedRoom(), RoomGeometry.BACK_PLATE_DEPTH);
        }

        @Test
        void aSpineSegmentIsCarvedFrontToBackToo() {
            MallLayout l = new MallLayout(ANCHOR, MallSpec.spine(7, 3));
            assertSliceOrder(l, ANCHOR.spineStart(), 6);
        }

        private void assertSliceOrder(MallLayout l, RoomPlacement frame, int lastSlice) {
            Map<GridPos, Integer> index = indexOf(l.mineOrder());
            int floorY = ANCHOR.floorPlateY();

            Map<Integer, Integer> firstOfSlice = new HashMap<>();
            Map<Integer, Integer> lastOfSlice = new HashMap<>();
            for (Map.Entry<GridPos, Integer> e : index.entrySet()) {
                GridPos p = e.getKey();
                if (p.y() == floorY) {
                    continue; // the floor pass is deliberately separate
                }
                int d = frame.depthOf(p);
                if (d < 0 || d > lastSlice) {
                    continue;
                }
                firstOfSlice.merge(d, e.getValue(), Math::min);
                lastOfSlice.merge(d, e.getValue(), Math::max);
            }

            for (int d = 0; d < lastSlice; d++) {
                assertTrue(
                        lastOfSlice.get(d) < firstOfSlice.get(d + 1),
                        "slice " + d + " must finish before slice " + (d + 1) + " starts");
            }
        }

        @Test
        void withinASliceTheCeilingLeadsAndTheBodyGoesTopDown() {
            MallLayout l = room(false);
            RoomPlacement room = ANCHOR.facedRoom();
            Map<GridPos, Integer> index = indexOf(l.mineOrder());

            for (int d = 0; d < RoomGeometry.BACK_PLATE_DEPTH; d++) {
                Integer previousLayer = null;
                for (int dy = RoomGeometry.INTERIOR_SIZE; dy >= 0; dy--) {
                    Integer earliest = null;
                    for (int s = -3; s <= 3; s++) {
                        Integer at =
                                index.get(room.cell(d, s, ANCHOR.playerFeet().y() + dy));
                        if (at != null) {
                            earliest = earliest == null ? at : Math.min(earliest, at);
                        }
                    }
                    if (earliest == null) {
                        continue;
                    }
                    if (previousLayer != null) {
                        assertTrue(previousLayer < earliest, "slice " + d + " layer dy=" + dy + " out of order");
                    }
                    previousLayer = earliest;
                }
            }
        }
    }

    @Test
    void theFacedRoomIsFinishedBeforeTheOppositeOneStarts() {
        MallLayout l = room(true);
        List<GridPos> order = l.mineOrder();
        int floorY = ANCHOR.floorPlateY();
        RoomPlacement faced = ANCHOR.facedRoom();

        int lastFaced = -1;
        int firstOpposite = Integer.MAX_VALUE;
        for (int i = 0; i < order.size(); i++) {
            GridPos p = order.get(i);
            if (p.y() == floorY) {
                continue; // floors run as one pass at the end, across both rooms
            }
            if (ANCHOR.alongOf(p) >= MallAnchor.START_OFFSET) {
                lastFaced = Math.max(lastFaced, i);
            } else {
                firstOpposite = Math.min(firstOpposite, i);
            }
        }
        assertTrue(lastFaced < firstOpposite, "one room at a time");
        assertTrue(faced.depthOf(order.get(0)) >= 0, "the job opens in the room being faced");
    }

    @Test
    void aSpineSegmentHasNoFloorPassAtAll() {
        MallLayout l = new MallLayout(ANCHOR, MallSpec.spine(7, 3));
        assertTrue(l.mineOrder().stream().noneMatch(p -> p.y() == ANCHOR.floorPlateY()));
    }
}
