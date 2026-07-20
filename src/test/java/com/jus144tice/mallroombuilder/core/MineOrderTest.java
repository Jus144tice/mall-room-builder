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
 * Ordering safety. These are the assertions that stop the job deadlocking on an unreachable ceiling
 * or digging the floor out from under the player.
 */
class MineOrderTest {

    private static final MallAnchor ANCHOR = MallAnchor.of(0, 64, 0, 0.0f, 2);
    private static final MallSpec SPEC = new MallSpec(true, true, 3);

    private static MallLayout layout() {
        return new MallLayout(ANCHOR, SPEC);
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
        MallLayout l = layout();
        List<GridPos> order = l.mineOrder();
        assertEquals(l.carve().size(), order.size(), "no omissions");
        assertEquals(order.size(), new HashSet<>(order).size(), "no duplicates");
        assertEquals(l.carve(), new HashSet<>(order));
    }

    @Test
    void neverQueuesFraming() {
        MallLayout l = layout();
        assertTrue(l.mineOrder().stream().noneMatch(l.framing()::contains));
    }

    @Nested
    @DisplayName("floor recesses come last, across the whole job")
    class FloorLast {

        @Test
        void everyFloorCellFollowsEveryNonFloorCell() {
            MallLayout l = layout();
            List<GridPos> order = l.mineOrder();
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
            MallLayout l = layout();
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
            MallLayout l = layout();
            RoomPlacement room = ANCHOR.facedRoom();
            Map<GridPos, Integer> index = indexOf(l.mineOrder());
            int floorY = ANCHOR.floorPlateY();

            Map<Integer, Integer> firstOfSlice = new HashMap<>();
            Map<Integer, Integer> lastOfSlice = new HashMap<>();
            for (Map.Entry<GridPos, Integer> e : index.entrySet()) {
                GridPos p = e.getKey();
                if (p.y() == floorY) {
                    continue; // the floor pass is deliberately separate
                }
                int d = room.depthOf(p);
                if (d < 0 || d > RoomGeometry.BACK_PLATE_DEPTH || Math.abs(room.sideOf(p)) > 3) {
                    continue; // not this room
                }
                firstOfSlice.merge(d, e.getValue(), Math::min);
                lastOfSlice.merge(d, e.getValue(), Math::max);
            }

            for (int d = 0; d < RoomGeometry.BACK_PLATE_DEPTH; d++) {
                assertTrue(
                        lastOfSlice.get(d) < firstOfSlice.get(d + 1),
                        "slice " + d + " must finish before slice " + (d + 1) + " starts");
            }
        }

        @Test
        void withinASliceTheCeilingLeadsAndTheBodyGoesTopDown() {
            MallLayout l = layout();
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
    void unitsRunInOrderWithoutInterleaving() {
        MallLayout l = layout();
        List<GridPos> order = l.mineOrder();
        int floorY = ANCHOR.floorPlateY();
        RoomPlacement faced = ANCHOR.facedRoom();

        // Within the non-floor pass, the corridor is finished before the faced room begins.
        int lastHall = -1;
        int firstRoom = Integer.MAX_VALUE;
        for (int i = 0; i < order.size(); i++) {
            GridPos p = order.get(i);
            if (p.y() == floorY) {
                continue;
            }
            int d = faced.depthOf(p);
            // Only the three planes immediately in front of the opening are corridor. Anything
            // further back belongs to the opposite room, which is a later unit, not an earlier one.
            if (d >= -SPEC.hallDepth() && d <= -1) {
                lastHall = Math.max(lastHall, i);
            } else if (d >= 0 && d <= RoomGeometry.BACK_PLATE_DEPTH && Math.abs(faced.sideOf(p)) <= 3) {
                firstRoom = Math.min(firstRoom, i);
            }
        }
        assertTrue(lastHall < firstRoom, "the corridor is cleared before carving into the room");
    }

    @Test
    void aSingleRoomJobOrdersJustAsCleanly() {
        MallLayout l = new MallLayout(ANCHOR, new MallSpec(false, true, 3));
        List<GridPos> order = l.mineOrder();
        assertEquals(l.carve().size(), new HashSet<>(order).size());

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
        assertTrue(firstFloor > lastNonFloor);
    }
}
