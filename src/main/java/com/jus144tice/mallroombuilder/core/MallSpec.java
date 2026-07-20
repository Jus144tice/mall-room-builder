/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

/**
 * What one job builds: the room you are facing, optionally its mirror across the hallway, and the
 * stretch of corridor between them.
 *
 * @param bothSides    also build the room directly opposite, on the far side of the spine
 * @param finishHallway carve and skin the hallway segment fronting the room(s)
 * @param hallDepth    hallway width in blocks, measured across the spine. Normally 3.
 */
public record MallSpec(boolean bothSides, boolean finishHallway, int hallDepth) {

    public MallSpec {
        if (hallDepth < 1) {
            throw new IllegalArgumentException("hallDepth must be >= 1, got " + hallDepth);
        }
    }

    /** Rooms this job will build. */
    public int roomCount() {
        return bothSides ? 2 : 1;
    }

    /**
     * How far back the opposite room's opening plane sits from this one: across the corridor, plus
     * the two wall planes the openings occupy.
     */
    public int oppositeOpeningOffset() {
        return hallDepth + 1;
    }
}
