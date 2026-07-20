/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One mall room: a 5x5x5 finished interior, open along its whole front face onto the spine hallway.
 *
 * <h2>The framing rule</h2>
 *
 * <p>Classify a cell by counting how many of its three coordinates sit at an <em>extreme</em> of the
 * envelope:</p>
 *
 * <pre>
 *   0 extremes -&gt; interior       125   carved to air
 *   1 extreme  -&gt; a flat plate   125   carved, then skinned
 *   2 extremes -&gt; an edge         40 } 44 framing: never visible from inside,
 *   3 extremes -&gt; a corner         4 }     so never mined, whatever it happens to be
 *                                 ---
 *   envelope 6 x 7 x 7          = 294
 * </pre>
 *
 * <p><strong>The front is not an extreme.</strong> Only the back plane counts on the depth axis,
 * because the opening is a hole, not a wall — which is what makes a room five skinned faces rather
 * than six. Get this wrong and the mod walls you in.</p>
 *
 * <p>The plates at {@code |side| == 3} are the pillars that frame each opening when seen from the
 * hallway. Adjacent rooms each carry their own, so the pillar between two rooms reads 2 wide.</p>
 */
public final class RoomGeometry {

    /** Edge length of the finished interior, and its depth from the opening. */
    public static final int INTERIOR_SIZE = 5;

    /** Half-width of the interior, along the spine. */
    public static final int INTERIOR_RADIUS = 2;

    /** Half-width of the envelope: the interior plus a pillar on each side. */
    public static final int ENVELOPE_RADIUS = 3;

    /** Depth at which the back wall plate sits. The only extreme on the depth axis. */
    public static final int BACK_PLATE_DEPTH = INTERIOR_SIZE;

    private RoomGeometry() {}

    /**
     * How many envelope extremes a facing-relative cell sits at.
     *
     * @param d  depth from the opening plane, 0 (open) to 5 (back plate)
     * @param s  offset along the spine, -3 to 3
     * @param dy height above the walking surface, -1 (floor plate) to 5 (ceiling plate)
     */
    public static int extremeCount(int d, int s, int dy) {
        int n = 0;
        if (d == BACK_PLATE_DEPTH) {
            n++;
        }
        if (Math.abs(s) == ENVELOPE_RADIUS) {
            n++;
        }
        if (dy == -1 || dy == INTERIOR_SIZE) {
            n++;
        }
        return n;
    }

    /** The 125 air cells of the finished room. */
    public static Set<GridPos> interior(RoomPlacement room) {
        return collect(room, 0);
    }

    /** The 125 cells that get skinned: back wall, two pillars, floor and ceiling plates. */
    public static Set<GridPos> visibleSkin(RoomPlacement room) {
        return collect(room, 1);
    }

    /** The 44 edge and corner cells. Never mined — whatever is already there stays. */
    public static Set<GridPos> framing(RoomPlacement room) {
        return collect(room, 2);
    }

    /** Every cell of the carved envelope, 294 of them. */
    public static Set<GridPos> envelope(RoomPlacement room) {
        return collect(room, -1);
    }

    /**
     * @param wanted exact extreme count, or 2 for "two or more", or -1 for every cell
     */
    private static Set<GridPos> collect(RoomPlacement room, int wanted) {
        Set<GridPos> out = new LinkedHashSet<>();
        int floorY = room.floorY();
        for (int d = 0; d <= BACK_PLATE_DEPTH; d++) {
            for (int s = -ENVELOPE_RADIUS; s <= ENVELOPE_RADIUS; s++) {
                for (int dy = -1; dy <= INTERIOR_SIZE; dy++) {
                    int extremes = extremeCount(d, s, dy);
                    boolean match = wanted < 0 || (wanted == 2 ? extremes >= 2 : extremes == wanted);
                    if (match) {
                        out.add(room.cell(d, s, floorY + dy));
                    }
                }
            }
        }
        return out;
    }
}
