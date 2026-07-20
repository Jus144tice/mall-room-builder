/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The build order is the carve order reversed. That falls out to floor, then walls, then ceiling —
 * which is the only sequence that works, since the floor must exist before the player can stand at
 * the right height and the ceiling wants placing last from below.
 */
class BuildOrderTest {

    private static final MallAnchor ANCHOR = MallAnchor.of(0, 64, 0, 0.0f);
    private static final MallSpec SPEC = new MallSpec(2, 5, true);

    private static MallLayout layout() {
        return new MallLayout(ANCHOR, SPEC);
    }

    @Test
    void isExactlyTheReverseOfTheCarveOrderRestrictedToSkin() {
        MallLayout l = layout();
        List<GridPos> carveSkin = new ArrayList<>();
        for (GridPos p : l.mineOrder()) {
            if (l.skin().contains(p)) {
                carveSkin.add(p);
            }
        }
        Collections.reverse(carveSkin);
        assertEquals(carveSkin, l.buildOrder());
    }

    @Test
    void coversEverySkinCellExactlyOnce() {
        MallLayout l = layout();
        List<GridPos> order = l.buildOrder();
        assertEquals(l.skin().size(), order.size());
        assertEquals(l.skin(), new HashSet<>(order));
    }

    @Test
    void placesNoAirCells() {
        MallLayout l = layout();
        assertTrue(l.buildOrder().stream().noneMatch(l.air()::contains));
    }

    @Test
    void withinAUnitTheFloorGoesDownBeforeTheCeiling() {
        MallLayout l = layout();
        List<GridPos> order = l.buildOrder();
        Map<GridPos, Integer> index = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            index.put(order.get(i), i);
        }

        int floorY = ANCHOR.floorPlateY();
        int ceilingY = ANCHOR.ceilingPlateY();
        int lastFloor = Integer.MIN_VALUE;
        int firstCeiling = Integer.MAX_VALUE;
        for (GridPos p : order) {
            // Restrict to room 0 so units do not confound the comparison.
            if (ANCHOR.alongOf(p) < -3 || ANCHOR.alongOf(p) > 3) {
                continue;
            }
            if (p.y() == floorY) {
                lastFloor = Math.max(lastFloor, index.get(p));
            } else if (p.y() == ceilingY) {
                firstCeiling = Math.min(firstCeiling, index.get(p));
            }
        }
        assertTrue(lastFloor < firstCeiling, "room 0 floor must be fully placed before its ceiling starts");
    }

    @Test
    void startsAtTheFarEndSoThereIsNoWalkBack() {
        MallLayout l = layout();
        List<GridPos> carve = l.mineOrder();
        List<GridPos> build = l.buildOrder();

        int lastCarveAlong = ANCHOR.alongOf(carve.get(carve.size() - 1));
        int firstBuildAlong = ANCHOR.alongOf(build.get(0));
        // Both should be deep in the last room rather than back at the start.
        assertTrue(lastCarveAlong > SPEC.pitch() - 4, "carving ends in the far room");
        assertTrue(firstBuildAlong > SPEC.pitch() - 4, "building starts in the far room");
    }
}
