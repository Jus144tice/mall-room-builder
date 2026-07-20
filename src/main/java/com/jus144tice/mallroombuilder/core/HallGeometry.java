/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The stretch of spine hallway in front of a room's opening.
 *
 * <p>The hallway is 3 wide and 5 tall, running along the spine perpendicular to the rooms. This
 * class covers only the segment fronting one room slot — the corridor as a whole is built up one
 * slot at a time as rooms are added.</p>
 *
 * <p>The segment spans {@code |side| <= 3}, one wider each way than the room's 5-block frontage, so
 * that consecutive slots tile without leaving an unfinished 1-block strip of floor under each
 * pillar. Slots therefore overlap by a block at the pillars, which costs nothing: the engine
 * retires any cell already in its final state.</p>
 *
 * <p>In practice most of this is already done — you are standing in the hallway when you run the
 * command — so it usually retires on the first sweep. Including it anyway is what finishes the
 * floor and ceiling in front of a freshly opened room.</p>
 */
public final class HallGeometry {

    /** Half-width of the segment: one wider than the room, so slots tile at the pillars. */
    public static final int SEGMENT_RADIUS = RoomGeometry.ENVELOPE_RADIUS;

    private HallGeometry() {}

    /**
     * The hallway's air, immediately in front of the opening.
     *
     * @param depth how many planes of corridor to finish — the hallway's width, normally 3
     */
    public static Set<GridPos> interior(RoomPlacement room, int depth) {
        Set<GridPos> out = new LinkedHashSet<>();
        int floorY = room.floorY();
        for (int d = -depth; d <= -1; d++) {
            for (int s = -SEGMENT_RADIUS; s <= SEGMENT_RADIUS; s++) {
                for (int dy = 0; dy < RoomGeometry.INTERIOR_SIZE; dy++) {
                    out.add(room.cell(d, s, floorY + dy));
                }
            }
        }
        return out;
    }

    /**
     * The hallway's floor and ceiling plates over the same footprint.
     *
     * <p>No side walls: the corridor continues into the neighbouring slots, and the only vertical
     * surfaces facing it are the rooms' own pillars and back walls.</p>
     */
    public static Set<GridPos> visibleSkin(RoomPlacement room, int depth) {
        Set<GridPos> out = new LinkedHashSet<>();
        int floorY = room.floorY();
        for (int d = -depth; d <= -1; d++) {
            for (int s = -SEGMENT_RADIUS; s <= SEGMENT_RADIUS; s++) {
                out.add(room.cell(d, s, floorY - 1));
                out.add(room.cell(d, s, floorY + RoomGeometry.INTERIOR_SIZE));
            }
        }
        return out;
    }
}
