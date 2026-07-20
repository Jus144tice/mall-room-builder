/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MallCountsTest {

    @Test
    void minedTotalIsAirPlusSkin() {
        assertEquals(717, new MallCounts(355, 362).minedTotal());
        assertEquals(275, new MallCounts(125, 150).minedTotal());
    }

    @Test
    void stacksRoundUp() {
        assertEquals(0, new MallCounts(0, 0).stacksNeeded());
        assertEquals(1, new MallCounts(0, 1).stacksNeeded());
        assertEquals(1, new MallCounts(0, 64).stacksNeeded());
        assertEquals(2, new MallCounts(0, 65).stacksNeeded());
        assertEquals(6, new MallCounts(355, 362).stacksNeeded());
        assertEquals(3, new MallCounts(125, 150).stacksNeeded());
    }
}
