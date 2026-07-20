/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The composer, and the single source of truth for what a job touches.
 *
 * <p>Two sets come out of it, and they never overlap:</p>
 *
 * <ul>
 *   <li>{@link #carve()} — every cell to mine. The 5x5x5 interior plus the five 1-block face
 *       recesses (ceiling, back, both sides, floor), and the fronting stretch of corridor.</li>
 *   <li>{@link #framing()} — every cell to <em>protect</em>. Never queued for mining, and watched
 *       during the job so anything that goes missing can be backfilled.</li>
 * </ul>
 *
 * <p>Nothing is ever placed as part of building a room. The mod carves the shell and the decorating
 * happens by hand afterwards; the only block it ever puts down is a backfill into framing that has
 * gone missing.</p>
 */
public final class MallLayout {

    private final MallAnchor anchor;
    private final MallSpec spec;
    private final Set<GridPos> carve;
    private final Set<GridPos> framing;
    private final Map<GridPos, SortKey> keys;

    public MallLayout(MallAnchor anchor, MallSpec spec) {
        this.anchor = anchor;
        this.spec = spec;

        Set<GridPos> carveCells = new LinkedHashSet<>();
        Set<GridPos> framingCells = new LinkedHashSet<>();
        Map<GridPos, SortKey> sortKeys = new LinkedHashMap<>();

        RoomPlacement faced = anchor.facedRoom();

        // Unit 0 is the corridor: it is where the player is standing, so it is usually already open
        // and retires immediately. Doing it first still matters for a fresh spur.
        if (spec.finishHallway()) {
            addHall(faced, spec.hallDepth(), 0, carveCells, sortKeys);
        }
        addRoom(faced, 1, carveCells, framingCells, sortKeys);
        if (spec.bothSides()) {
            addRoom(anchor.oppositeRoom(spec), 2, carveCells, framingCells, sortKeys);
        }

        // A cell carved for one unit is carved, whatever another unit calls it. Adjacent slots
        // deliberately overlap by a block at the pillars.
        framingCells.removeAll(carveCells);

        this.carve = Collections.unmodifiableSet(carveCells);
        this.framing = Collections.unmodifiableSet(framingCells);
        this.keys = sortKeys;
    }

    private void addRoom(
            RoomPlacement room,
            int unit,
            Set<GridPos> carveCells,
            Set<GridPos> framingCells,
            Map<GridPos, SortKey> sortKeys) {
        for (GridPos p : RoomGeometry.interior(room)) {
            carveCells.add(p);
            sortKeys.putIfAbsent(p, keyFor(room, unit, p));
        }
        for (GridPos p : RoomGeometry.visibleSkin(room)) {
            carveCells.add(p);
            sortKeys.putIfAbsent(p, keyFor(room, unit, p));
        }
        framingCells.addAll(RoomGeometry.framing(room));
    }

    private void addHall(
            RoomPlacement faced, int depth, int unit, Set<GridPos> carveCells, Map<GridPos, SortKey> sortKeys) {
        for (GridPos p : HallGeometry.interior(faced, depth)) {
            carveCells.add(p);
            sortKeys.putIfAbsent(p, keyFor(faced, unit, p));
        }
        for (GridPos p : HallGeometry.visibleSkin(faced, depth)) {
            carveCells.add(p);
            sortKeys.putIfAbsent(p, keyFor(faced, unit, p));
        }
    }

    /** Cells to mine. */
    public Set<GridPos> carve() {
        return carve;
    }

    /** Cells to leave standing, and to backfill if they go missing. */
    public Set<GridPos> framing() {
        return framing;
    }

    public MallAnchor anchor() {
        return anchor;
    }

    public MallSpec spec() {
        return spec;
    }

    public MallCounts counts() {
        return new MallCounts(carve.size(), framing.size());
    }

    /**
     * Every cell to mine, in the order to mine it.
     *
     * <p>Two things drive this, and both are load-bearing:</p>
     *
     * <p><strong>Slice by slice, near to far.</strong> A room is an alcove carved into rock from
     * outside, so the far end is neither reachable nor walkable until the near end is open. Doing
     * the whole ceiling first — which is right for a room you stand in the middle of — deadlocks
     * here: the far ceiling is about 5.25 blocks away, past the 4.5 reach, and you cannot walk in to
     * close the gap. Within each slice the ceiling still goes before the body, so anything unstable
     * overhead drops while there is still solid ground beneath it.</p>
     *
     * <p><strong>Floor recesses last, across the whole job.</strong> Carving the floor drops the
     * player a block, and a lower eye costs reach on every ceiling cell after it. Leaving all the
     * floors to a final pass means the whole job is worked from the original standing height, and
     * the one-block drop happens at the very end when nothing is left to reach.</p>
     */
    public List<GridPos> mineOrder() {
        List<GridPos> out = new ArrayList<>(carve);
        out.sort(Comparator.comparing(
                keys::get,
                Comparator.comparingInt(SortKey::floorPass)
                        .thenComparingInt(SortKey::unit)
                        .thenComparingInt(SortKey::slice)
                        .thenComparingInt(SortKey::tier)
                        .thenComparingInt(SortKey::absSide)
                        .thenComparingInt(SortKey::side)));
        return out;
    }

    private SortKey keyFor(RoomPlacement frame, int unit, GridPos pos) {
        int d = frame.depthOf(pos);
        int side = frame.sideOf(pos);
        int dy = pos.y() - frame.floorY();

        boolean isFloor = dy == -1;
        // The corridor runs backwards from the opening, so flip its depth to keep "nearest first"
        // meaningful in the same comparison.
        int slice = d >= 0 ? d : -d;
        // Ceiling leads each slice, then the body downwards.
        int tier = dy == RoomGeometry.INTERIOR_SIZE ? 0 : RoomGeometry.INTERIOR_SIZE - dy;
        return new SortKey(isFloor ? 1 : 0, unit, slice, tier, Math.abs(side), side);
    }

    private record SortKey(int floorPass, int unit, int slice, int tier, int absSide, int side) {}
}
