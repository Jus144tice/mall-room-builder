/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class GridPosTest {

    @Test
    void exposesItsComponents() {
        GridPos p = new GridPos(3, -7, 11);
        assertEquals(3, p.x());
        assertEquals(-7, p.y());
        assertEquals(11, p.z());
    }

    @Test
    void plusAndMinusAreInverses() {
        GridPos origin = new GridPos(10, 20, 30);
        GridPos moved = origin.plus(-4, 5, 6);
        assertEquals(new GridPos(-4, 5, 6), moved.minus(origin));
        assertEquals(origin, moved.plus(4, -5, -6));
    }

    @Test
    void offsetStepsAlongTheFacingAxisOnly() {
        GridPos origin = new GridPos(0, 64, 0);
        assertEquals(new GridPos(0, 64, 3), origin.offset(Facing.SOUTH, 3));
        assertEquals(new GridPos(0, 64, -3), origin.offset(Facing.NORTH, 3));
        assertEquals(new GridPos(3, 64, 0), origin.offset(Facing.EAST, 3));
        assertEquals(new GridPos(-3, 64, 0), origin.offset(Facing.WEST, 3));
    }

    @Test
    void lateralStepsAlongTheLeftAxis() {
        GridPos origin = new GridPos(0, 64, 0);
        // Facing south, left is east (+X).
        assertEquals(new GridPos(2, 64, 0), origin.lateral(Facing.SOUTH, 2));
        // Facing east, left is north (-Z).
        assertEquals(new GridPos(0, 64, -2), origin.lateral(Facing.EAST, 2));
    }

    @Test
    void offsetAndLateralNeverChangeY() {
        GridPos origin = new GridPos(5, 42, -5);
        for (Facing f : Facing.values()) {
            assertEquals(42, origin.offset(f, 9).y());
            assertEquals(42, origin.lateral(f, 9).y());
        }
    }

    @Test
    void atCombinesAlongSideAndAbsoluteHeight() {
        GridPos feet = new GridPos(0, 64, 0);
        // Facing south: along is +Z, left is +X.
        assertEquals(new GridPos(2, 70, 4), feet.at(Facing.SOUTH, 4, 2, 70));
        // Facing west: along is -X, left is +Z.
        assertEquals(new GridPos(-4, 70, 2), feet.at(Facing.WEST, 4, 2, 70));
    }

    @Test
    void withYReplacesHeightOnly() {
        assertEquals(new GridPos(1, 99, 2), new GridPos(1, 3, 2).withY(99));
    }

    @Test
    void hasValueEqualityAndHashing() {
        assertEquals(new GridPos(1, 2, 3), new GridPos(1, 2, 3));
        assertEquals(new GridPos(1, 2, 3).hashCode(), new GridPos(1, 2, 3).hashCode());
        assertNotEquals(new GridPos(1, 2, 3), new GridPos(3, 2, 1));
    }
}
