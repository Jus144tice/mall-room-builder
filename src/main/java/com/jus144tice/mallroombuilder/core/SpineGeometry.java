/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One segment of spine hallway: a plain box, 7 long by 3 wide by 5 tall.
 *
 * <p>Unlike a room this has <strong>no recesses and no framing</strong> — every cell in the box is
 * carved and nothing is left standing. The corridor is a corridor.</p>
 *
 * <p>It is also the interior height only, {@code dy in [0, 4]}, which is what keeps a finished room
 * flush with it. A room's floor recess is carved one <em>below</em> the surface you walk on, so that
 * you can lay a finished floor into it by hand and end up level with the corridor. Carving the
 * corridor floor as well would drop the hallway a block and break that.</p>
 */
public final class SpineGeometry {

    /** Default segment length, in blocks along the run. */
    public static final int DEFAULT_LENGTH = 7;

    /** Half-width: the centre lane plus one each side. */
    public static final int RADIUS = 1;

    /** Interior height, matching a room's. */
    public static final int HEIGHT = RoomGeometry.INTERIOR_SIZE;

    private SpineGeometry() {}

    /**
     * Every cell of the segment.
     *
     * @param start  the frame whose depth 0 is the first block of the segment — the block directly
     *               in front of the player
     * @param length blocks along the run
     */
    public static Set<GridPos> carve(RoomPlacement start, int length) {
        Set<GridPos> out = new LinkedHashSet<>();
        int floorY = start.floorY();
        for (int d = 0; d < length; d++) {
            for (int s = -RADIUS; s <= RADIUS; s++) {
                for (int dy = 0; dy < HEIGHT; dy++) {
                    out.add(start.cell(d, s, floorY + dy));
                }
            }
        }
        return out;
    }

    /** How many blocks a segment of the given length costs. */
    public static int cellCount(int length) {
        return length * (RADIUS * 2 + 1) * HEIGHT;
    }
}
