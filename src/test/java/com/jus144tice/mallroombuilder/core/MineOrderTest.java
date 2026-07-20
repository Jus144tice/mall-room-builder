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
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Ordering safety. These are the assertions that stop the mod digging the floor out from under the
 * player or burying a half-carved room under gravel.
 */
class MineOrderTest {

    private static final MallAnchor ANCHOR = MallAnchor.of(0, 64, 0, 0.0f); // SOUTH
    private static final MallSpec SPEC = new MallSpec(3, 5, true);

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
    void isExactlyAPermutationOfAirPlusSkin() {
        MallLayout l = layout();
        List<GridPos> order = l.mineOrder();

        assertEquals(l.counts().minedTotal(), order.size(), "no omissions");
        assertEquals(order.size(), new HashSet<>(order).size(), "no duplicates");

        Set<GridPos> expected = new HashSet<>(l.air());
        expected.addAll(l.skin());
        assertEquals(expected, new HashSet<>(order));
    }

    @Test
    void noFloorPlateCellIsMinedBeforeTheInteriorDirectlyAboveIt() {
        MallLayout l = layout();
        Map<GridPos, Integer> index = indexOf(l.mineOrder());

        int floorY = ANCHOR.floorPlateY();
        for (GridPos p : index.keySet()) {
            if (p.y() != floorY) {
                continue;
            }
            GridPos above = p.plus(0, 1, 0);
            Integer aboveIndex = index.get(above);
            if (aboveIndex == null) {
                continue; // nothing carved above this plate cell
            }
            assertTrue(
                    index.get(p) > aboveIndex,
                    "floor cell " + p + " must be mined after the cell above it at " + above);
        }
    }

    @Test
    void theCeilingOfAUnitComesBeforeEverythingElseInThatUnit() {
        MallLayout l = layout();
        List<GridPos> order = l.mineOrder();
        int ceilingY = ANCHOR.ceilingPlateY();

        // Within each contiguous run of a unit, once a non-ceiling cell appears no ceiling cell may
        // follow until the along-coordinate has moved into the next unit. Simplest robust check:
        // for every pair in the same unit, ceiling precedes non-ceiling.
        Map<GridPos, Integer> index = indexOf(order);
        Map<Integer, Integer> lastCeiling = new HashMap<>();
        Map<Integer, Integer> firstNonCeiling = new HashMap<>();

        for (GridPos p : order) {
            int unit = unitOf(ANCHOR.alongOf(p));
            if (p.y() == ceilingY) {
                lastCeiling.merge(unit, index.get(p), Math::max);
            } else {
                firstNonCeiling.merge(unit, index.get(p), Math::min);
            }
        }
        for (Map.Entry<Integer, Integer> e : lastCeiling.entrySet()) {
            Integer firstOther = firstNonCeiling.get(e.getKey());
            if (firstOther != null) {
                assertTrue(e.getValue() < firstOther, "unit " + e.getKey() + " mines its ceiling first");
            }
        }
    }

    @Test
    void theFloorPlateOfAUnitComesAfterEverythingElseInThatUnit() {
        MallLayout l = layout();
        List<GridPos> order = l.mineOrder();
        int floorY = ANCHOR.floorPlateY();
        Map<GridPos, Integer> index = indexOf(order);

        Map<Integer, Integer> firstFloor = new HashMap<>();
        Map<Integer, Integer> lastNonFloor = new HashMap<>();
        for (GridPos p : order) {
            int unit = unitOf(ANCHOR.alongOf(p));
            if (p.y() == floorY) {
                firstFloor.merge(unit, index.get(p), Math::min);
            } else {
                lastNonFloor.merge(unit, index.get(p), Math::max);
            }
        }
        for (Map.Entry<Integer, Integer> e : firstFloor.entrySet()) {
            Integer lastOther = lastNonFloor.get(e.getKey());
            if (lastOther != null) {
                assertTrue(e.getValue() > lastOther, "unit " + e.getKey() + " mines its floor last");
            }
        }
    }

    @Test
    void unitsAreMinedInTravelOrderWithNoInterleaving() {
        List<GridPos> order = layout().mineOrder();
        int previousUnit = -1;
        Set<Integer> finished = new HashSet<>();
        for (GridPos p : order) {
            int unit = unitOf(ANCHOR.alongOf(p));
            if (unit != previousUnit) {
                assertTrue(finished.add(unit), "unit " + unit + " was revisited after moving on");
                assertTrue(unit > previousUnit, "units must run forward: " + previousUnit + " -> " + unit);
                previousUnit = unit;
            }
        }
    }

    @Test
    void theBodyIsMinedTopDown() {
        List<GridPos> order = layout().mineOrder();
        Map<GridPos, Integer> index = indexOf(order);
        int floorY = ANCHOR.floorPlateY();
        int ceilingY = ANCHOR.ceilingPlateY();

        for (GridPos p : index.keySet()) {
            if (p.y() <= floorY || p.y() >= ceilingY - 1) {
                continue;
            }
            GridPos above = p.plus(0, 1, 0);
            Integer aboveIndex = index.get(above);
            if (aboveIndex != null && above.y() < ceilingY) {
                assertTrue(index.get(p) > aboveIndex, "body cell " + p + " must follow the one above it");
            }
        }
    }

    /** Mirrors MallLayout's private unit assignment so the tests can reason about units. */
    private static int unitOf(int along) {
        int pitch = SPEC.pitch();
        int room = Math.floorDiv(along + RoomGeometry.ENVELOPE_RADIUS, pitch);
        room = Math.max(0, Math.min(SPEC.roomCount() - 1, room));
        return along <= room * pitch + RoomGeometry.ENVELOPE_RADIUS ? room * 2 : room * 2 + 1;
    }
}
