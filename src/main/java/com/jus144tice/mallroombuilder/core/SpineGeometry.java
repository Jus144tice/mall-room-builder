/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One segment of spine hallway: 3 wide, and either 5 or 7 tall depending on whether the finishing
 * recesses are being cut.
 *
 * <p>Like a room, the corridor's floor and ceiling are eventually replaced, so each gets a 1-block
 * recess to hold the finishing course — {@code dy = -1} below and {@code dy = 5} above. Cutting
 * those makes the segment 7 tall and drops the player a block, exactly as a room does, which is what
 * keeps a finished room flush with the corridor beside it.</p>
 *
 * <p>Unlike a room there is <strong>no framing</strong>: the corridor's sides are where rooms open,
 * so there are no side recesses and therefore no corner cells to leave standing. Every cell in the
 * box is carved.</p>
 */
public final class SpineGeometry {

    /** Default segment length, in blocks along the run. */
    public static final int DEFAULT_LENGTH = 7;

    /** Half-width: the centre lane plus one each side. */
    public static final int RADIUS = 1;

    /** Finished interior height, matching a room's. */
    public static final int INTERIOR_HEIGHT = RoomGeometry.INTERIOR_SIZE;

    /** Height once the floor and ceiling recesses are cut. */
    public static final int ENVELOPE_HEIGHT = INTERIOR_HEIGHT + 2;

    /** Cells across the segment's width. */
    public static final int WIDTH = RADIUS * 2 + 1;

    private SpineGeometry() {}

    /** The finished corridor volume: 3 wide, 5 tall. */
    public static Set<GridPos> interior(RoomPlacement start, int length) {
        return slab(start, length, 0, INTERIOR_HEIGHT - 1);
    }

    /** The floor and ceiling recesses that hold the finishing course. */
    public static Set<GridPos> recesses(RoomPlacement start, int length) {
        Set<GridPos> out = new LinkedHashSet<>(slab(start, length, -1, -1));
        out.addAll(slab(start, length, INTERIOR_HEIGHT, INTERIOR_HEIGHT));
        return out;
    }

    /**
     * Every cell to mine.
     *
     * @param includeRecesses cut the floor and ceiling recesses as well as the interior
     */
    public static Set<GridPos> carve(RoomPlacement start, int length, boolean includeRecesses) {
        Set<GridPos> out = new LinkedHashSet<>(interior(start, length));
        if (includeRecesses) {
            out.addAll(recesses(start, length));
        }
        return out;
    }

    /** How many blocks a segment costs. */
    public static int cellCount(int length, boolean includeRecesses) {
        return length * WIDTH * (includeRecesses ? ENVELOPE_HEIGHT : INTERIOR_HEIGHT);
    }

    private static Set<GridPos> slab(RoomPlacement start, int length, int fromDy, int toDy) {
        Set<GridPos> out = new LinkedHashSet<>();
        int floorY = start.floorY();
        for (int d = 0; d < length; d++) {
            for (int s = -RADIUS; s <= RADIUS; s++) {
                for (int dy = fromDy; dy <= toDy; dy++) {
                    out.add(start.cell(d, s, floorY + dy));
                }
            }
        }
        return out;
    }
}
