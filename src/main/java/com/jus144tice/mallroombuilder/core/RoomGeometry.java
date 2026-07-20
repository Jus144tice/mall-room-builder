/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One mall room: a 5x5x5 finished interior inside a 7x7x7 carved envelope.
 *
 * <p>A room is symmetric under 90-degree rotation, so none of this depends on which way the mall
 * runs — it is expressed directly in world axes relative to the room's reference cell.</p>
 *
 * <h2>The framing rule</h2>
 *
 * <p>The shell (envelope minus interior) is 218 blocks, but only 150 of them are ever <em>seen</em>
 * from inside the finished room: the six flat 5x5 face plates. The other 68 lie on the cube's 12
 * edges and 8 corners, hidden behind the plates from every interior viewpoint. Those are the
 * <strong>framing</strong>, and the whole point of this mod is that they are never mined.</p>
 *
 * <p>The test is delightfully simple. For a shell cell, count how many of its three coordinates sit
 * at an <em>extreme</em> of the envelope:</p>
 *
 * <pre>
 *   0 extremes -&gt; interior
 *   1 extreme  -&gt; a flat face plate   (visible, mined and skinned)
 *   2 extremes -&gt; an edge             (framing, left alone)
 *   3 extremes -&gt; a corner            (framing, left alone)
 * </pre>
 *
 * <p>This yields exactly 150 / 68 / 218, and {@link HallGeometry} reuses the identical predicate
 * for the hallway cross-section.</p>
 */
public final class RoomGeometry {

    /** Edge length of the finished interior. */
    public static final int INTERIOR_SIZE = 5;

    /** Edge length of the carved envelope: the interior plus a 1-block skin on each side. */
    public static final int ENVELOPE_SIZE = 7;

    /** Half-width of the interior, in blocks, from the reference cell. */
    public static final int INTERIOR_RADIUS = INTERIOR_SIZE / 2; // 2

    /** Half-width of the envelope, in blocks, from the reference cell. */
    public static final int ENVELOPE_RADIUS = ENVELOPE_SIZE / 2; // 3

    private RoomGeometry() {}

    /**
     * True if a cell offset is at an envelope extreme on at least two axes — an edge or a corner,
     * and therefore never visible from inside.
     *
     * @param dx     horizontal offset from the reference cell
     * @param dy     vertical offset from the floor (interior is 0..4, plates are -1 and 5)
     * @param dz     horizontal offset from the reference cell
     * @param radius half-width of the envelope on the horizontal axes
     */
    public static int extremeCount(int dx, int dy, int dz, int radius) {
        int n = 0;
        if (Math.abs(dx) == radius) {
            n++;
        }
        if (dy == -1 || dy == INTERIOR_SIZE) {
            n++;
        }
        if (Math.abs(dz) == radius) {
            n++;
        }
        return n;
    }

    /** The 125 air cells of the finished room. */
    public static Set<GridPos> interior(GridPos reference) {
        Set<GridPos> out = new LinkedHashSet<>();
        for (int dy = 0; dy < INTERIOR_SIZE; dy++) {
            for (int dx = -INTERIOR_RADIUS; dx <= INTERIOR_RADIUS; dx++) {
                for (int dz = -INTERIOR_RADIUS; dz <= INTERIOR_RADIUS; dz++) {
                    out.add(reference.plus(dx, dy, dz));
                }
            }
        }
        return out;
    }

    /** All 343 cells of the carved envelope, interior included. */
    public static Set<GridPos> envelope(GridPos reference) {
        Set<GridPos> out = new LinkedHashSet<>();
        for (int dy = -1; dy <= INTERIOR_SIZE; dy++) {
            for (int dx = -ENVELOPE_RADIUS; dx <= ENVELOPE_RADIUS; dx++) {
                for (int dz = -ENVELOPE_RADIUS; dz <= ENVELOPE_RADIUS; dz++) {
                    out.add(reference.plus(dx, dy, dz));
                }
            }
        }
        return out;
    }

    /** The 150 shell cells visible from inside: the six flat 5x5 face plates. */
    public static Set<GridPos> visibleSkin(GridPos reference) {
        Set<GridPos> out = new LinkedHashSet<>();
        forEachShellCell(reference, out, 1);
        return out;
    }

    /** The 68 shell cells on the envelope's edges and corners. Never mined. */
    public static Set<GridPos> framing(GridPos reference) {
        Set<GridPos> out = new LinkedHashSet<>();
        forEachShellCell(reference, out, 2);
        return out;
    }

    /**
     * Collects shell cells whose extreme count is exactly {@code wanted} (1 = visible face plate),
     * or at least {@code wanted} when {@code wanted >= 2} (edges and corners together).
     */
    private static void forEachShellCell(GridPos reference, Set<GridPos> out, int wanted) {
        for (int dy = -1; dy <= INTERIOR_SIZE; dy++) {
            for (int dx = -ENVELOPE_RADIUS; dx <= ENVELOPE_RADIUS; dx++) {
                for (int dz = -ENVELOPE_RADIUS; dz <= ENVELOPE_RADIUS; dz++) {
                    int extremes = extremeCount(dx, dy, dz, ENVELOPE_RADIUS);
                    boolean match = (wanted == 1) ? extremes == 1 : extremes >= wanted;
                    if (match) {
                        out.add(reference.plus(dx, dy, dz));
                    }
                }
            }
        }
    }
}
