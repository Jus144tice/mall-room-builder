/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

/**
 * What to build: how many rooms, how long the hallways between them are, and whether to cobble the
 * doorway thresholds.
 *
 * @param roomCount          number of 5x5x5 rooms, at least 1
 * @param hallLength         blocks of hallway strictly between two room envelopes, at least 1.
 *                           Room pitch is {@code 7 + hallLength}.
 * @param coverDoorThreshold extend the hallway floor and ceiling strips through the doorway
 *                           opening. See {@link MallLayout} for why this exists.
 */
public record MallSpec(int roomCount, int hallLength, boolean coverDoorThreshold) {

    public MallSpec {
        if (roomCount < 1) {
            throw new IllegalArgumentException("roomCount must be >= 1, got " + roomCount);
        }
        if (hallLength < 1) {
            throw new IllegalArgumentException("hallLength must be >= 1, got " + hallLength);
        }
    }

    /** Distance between the reference cells of consecutive rooms: the 7-block envelope plus the gap. */
    public int pitch() {
        return RoomGeometry.ENVELOPE_SIZE + hallLength;
    }

    /** Number of hallways: one fewer than the number of rooms. */
    public int hallCount() {
        return roomCount - 1;
    }
}
