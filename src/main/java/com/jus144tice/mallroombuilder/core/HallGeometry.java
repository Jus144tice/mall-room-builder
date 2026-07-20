/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One hallway: a 3-wide, 5-tall finished tube inside a 5-wide, 7-tall carved envelope.
 *
 * <p>The hallway is full room height and narrower, so its floor and ceiling planes line up exactly
 * with the room's — the carved hall is 7 tall just like the room, and the two envelopes meet
 * flush.</p>
 *
 * <p>The same {@link RoomGeometry#extremeCount} predicate classifies the cross-section, with the
 * horizontal radius dropped from 3 to 2: per 1-block slice, 35 envelope cells = 15 interior + 16
 * visible skin + 4 framing (the four long corner edges of the tube).</p>
 *
 * <h2>Spans</h2>
 *
 * <p>Two ranges of the along-axis matter, both measured in blocks forward of the anchor:</p>
 * <ul>
 *   <li><strong>gap span</strong> — the {@code hallLength} planes strictly between the two room
 *       envelopes. The hallway's own walls live here.</li>
 *   <li><strong>opening span</strong> — the gap span widened by one plane at each end, so it also
 *       covers the two room wall plates the hallway pierces. Carving air across this span cuts the
 *       doorways <em>for free</em>: the room's wall plate cells that fall inside the tube simply
 *       become air, leaving a 10-block jamb around each opening.</li>
 * </ul>
 */
public final class HallGeometry {

    /** Half-width of the finished tube: 3 wide. */
    public static final int INTERIOR_RADIUS = 1;

    /** Half-width of the carved tube: 5 wide. */
    public static final int ENVELOPE_RADIUS = 2;

    private HallGeometry() {}

    /** First along-coordinate of the gap between room {@code hallIndex} and the next. */
    public static int gapStart(MallSpec spec, int hallIndex) {
        return hallIndex * spec.pitch() + RoomGeometry.ENVELOPE_RADIUS + 1;
    }

    /** Last along-coordinate of the gap. */
    public static int gapEnd(MallSpec spec, int hallIndex) {
        return (hallIndex + 1) * spec.pitch() - RoomGeometry.ENVELOPE_RADIUS - 1;
    }

    /** First along-coordinate of the opening: one plane earlier than the gap, inside the room wall. */
    public static int openingStart(MallSpec spec, int hallIndex) {
        return gapStart(spec, hallIndex) - 1;
    }

    /** Last along-coordinate of the opening. */
    public static int openingEnd(MallSpec spec, int hallIndex) {
        return gapEnd(spec, hallIndex) + 1;
    }

    /**
     * The air cells of the tube, spanning the opening so the doorways through both room wall
     * plates are cut as part of the same volume.
     */
    public static Set<GridPos> interior(MallAnchor anchor, MallSpec spec, int hallIndex) {
        Set<GridPos> out = new LinkedHashSet<>();
        int floorY = anchor.playerFeet().y();
        for (int along = openingStart(spec, hallIndex); along <= openingEnd(spec, hallIndex); along++) {
            for (int dy = 0; dy < RoomGeometry.INTERIOR_SIZE; dy++) {
                for (int side = -INTERIOR_RADIUS; side <= INTERIOR_RADIUS; side++) {
                    out.add(anchor.cell(along, side, floorY + dy));
                }
            }
        }
        return out;
    }

    /**
     * The visible skin of the tube: side walls, floor and ceiling strips over the gap, plus — when
     * {@link MallSpec#coverDoorThreshold()} is set — the floor and ceiling strips across the two
     * doorway planes.
     *
     * <p>That last part is a deliberate departure from the pure framing rule, and it fixes a real
     * hole. Where the hallway pierces a room wall, the room envelope's bottom edge runs along the
     * doorway floor. That is a two-extreme cell, so the framing rule says "never visible, leave it
     * as stone" — but once the doorway is open you walk straight over it and see it. Extending the
     * strips through the opening covers the threshold, at a cost of 6 blocks per doorway.</p>
     */
    public static Set<GridPos> visibleSkin(MallAnchor anchor, MallSpec spec, int hallIndex) {
        Set<GridPos> out = new LinkedHashSet<>();
        int floorY = anchor.playerFeet().y();

        // Whole cross-section shell over the gap, classified by the shared predicate.
        for (int along = gapStart(spec, hallIndex); along <= gapEnd(spec, hallIndex); along++) {
            for (int dy = -1; dy <= RoomGeometry.INTERIOR_SIZE; dy++) {
                for (int side = -ENVELOPE_RADIUS; side <= ENVELOPE_RADIUS; side++) {
                    if (RoomGeometry.extremeCount(side, dy, 0, ENVELOPE_RADIUS) == 1) {
                        out.add(anchor.cell(along, side, floorY + dy));
                    }
                }
            }
        }

        // Doorway thresholds: floor and ceiling strips across the two pierced room wall plates.
        if (spec.coverDoorThreshold()) {
            for (int along : new int[] {openingStart(spec, hallIndex), openingEnd(spec, hallIndex)}) {
                for (int dy : new int[] {-1, RoomGeometry.INTERIOR_SIZE}) {
                    for (int side = -INTERIOR_RADIUS; side <= INTERIOR_RADIUS; side++) {
                        out.add(anchor.cell(along, side, floorY + dy));
                    }
                }
            }
        }
        return out;
    }
}
