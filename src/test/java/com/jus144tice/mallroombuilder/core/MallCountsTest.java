/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MallCountsTest {

    @Test
    void minedTotalIsJustTheCarveBecauseNothingIsPlaced() {
        assertEquals(397, new MallCounts(397, 44).minedTotal());
        assertEquals(647, new MallCounts(647, 88).minedTotal());
    }

    @Test
    void envelopeIsCarvePlusFraming() {
        // One isolated room: 250 carved + 44 framing = the 6x7x7 envelope.
        assertEquals(294, new MallCounts(250, 44).envelopeTotal());
    }
}
