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
 *   <li>{@link #carve()} — every cell to mine.</li>
 *   <li>{@link #framing()} — every cell to <em>protect</em>. Never queued, and watched during the
 *       job so anything that goes missing can be backfilled. Empty for a spine segment, which is a
 *       plain box.</li>
 * </ul>
 *
 * <p>Nothing is ever placed as part of carving. The mod cuts the shell and the decorating happens by
 * hand; the only block it puts down is a backfill into framing that has gone missing.</p>
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

        // A fill-only job queues nothing to mine, but still needs the framing set so the backfill
        // watcher protects it while placing.
        if (spec.kind() == MallSpec.Kind.SPINE) {
            if (spec.carve()) {
                addSpine(anchor.spineStart(), spec, sortKeys, carveCells);
            }
        } else {
            if (spec.carve()) {
                addRoom(anchor.facedRoom(), 0, spec, carveCells, framingCells, sortKeys);
                if (spec.bothSides()) {
                    addRoom(anchor.oppositeRoom(spec), 1, spec, carveCells, framingCells, sortKeys);
                }
            } else {
                framingCells.addAll(RoomGeometry.framing(anchor.facedRoom()));
                if (spec.bothSides()) {
                    framingCells.addAll(RoomGeometry.framing(anchor.oppositeRoom(spec)));
                }
            }
        }

        // A cell carved for one unit is carved, whatever another unit calls it.
        framingCells.removeAll(carveCells);

        this.carve = Collections.unmodifiableSet(carveCells);
        this.framing = Collections.unmodifiableSet(framingCells);
        this.keys = sortKeys;
    }

    private void addRoom(
            RoomPlacement room,
            int unit,
            MallSpec spec,
            Set<GridPos> carveCells,
            Set<GridPos> framingCells,
            Map<GridPos, SortKey> sortKeys) {
        for (GridPos p : RoomGeometry.carve(room, spec.finishRecesses())) {
            carveCells.add(p);
            sortKeys.putIfAbsent(p, keyFor(room, unit, p));
        }
        framingCells.addAll(RoomGeometry.framing(room));
    }

    private void addSpine(RoomPlacement start, MallSpec spec, Map<GridPos, SortKey> sortKeys, Set<GridPos> carveCells) {
        for (GridPos p : SpineGeometry.carve(start, spec.spineLength(), spec.finishRecesses())) {
            carveCells.add(p);
            sortKeys.putIfAbsent(p, keyFor(start, 0, p));
        }
    }

    /** Cells to mine. Empty for a fill-only job. */
    public Set<GridPos> carve() {
        return carve;
    }

    /**
     * The cells of one surface, across every unit this job covers.
     *
     * <p>For a {@code both} job that is the surface on <em>both</em> rooms, which is what makes one
     * command finish a facing pair in one go.</p>
     */
    public Set<GridPos> surface(Surface surface) {
        Set<GridPos> out = new LinkedHashSet<>();
        if (spec.kind() == MallSpec.Kind.SPINE) {
            out.addAll(SpineGeometry.surface(anchor.spineStart(), spec.spineLength(), surface));
        } else {
            out.addAll(RoomGeometry.surface(anchor.facedRoom(), surface));
            if (spec.bothSides()) {
                out.addAll(RoomGeometry.surface(anchor.oppositeRoom(spec), surface));
            }
        }
        return out;
    }

    /**
     * Every cell to fill, in the order to fill it.
     *
     * <p>Surface order comes from {@link Surface}'s declaration: <strong>floor first</strong>, which
     * restores the walking level the carve dropped you from, then walls, then ceiling and beam last —
     * placed from below, where they are always in reach.</p>
     *
     * <p>Within the floor the order is <strong>far to near</strong>, so you back out toward the
     * entrance as you lay it rather than stranding yourself on the last cell. Everything else runs
     * near to far. A cell you happen to be standing in is not placeable, gets deferred, and is picked
     * up on a sweep once you have moved — the same mechanism the carve uses.</p>
     */
    public List<FillCell> fillOrder() {
        List<FillCell> out = new ArrayList<>();
        for (Surface surface : spec.fill().surfaces()) {
            List<GridPos> cells = new ArrayList<>(surface(surface));
            boolean farFirst = surface == Surface.FLOOR;
            cells.sort(Comparator.comparingInt((GridPos p) -> farFirst ? -depthOf(p) : depthOf(p))
                    .thenComparingInt(p -> Math.abs(anchor.sideOf(p)))
                    .thenComparingInt(anchor::sideOf)
                    .thenComparingInt(GridPos::y));
            for (GridPos p : cells) {
                out.add(new FillCell(p, surface));
            }
        }
        return out;
    }

    /** Distance from the anchor along the facing axis, folded so the opposite room reads the same way. */
    private int depthOf(GridPos pos) {
        int along = anchor.alongOf(pos);
        return along >= 0 ? along : -along;
    }

    /** One cell to fill, and which surface's material it takes. */
    public record FillCell(GridPos pos, Surface surface) {}

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
        int filled = 0;
        for (Surface s : spec.fill().surfaces()) {
            filled += surface(s).size();
        }
        return new MallCounts(carve.size(), framing.size(), filled);
    }

    /** How many blocks one surface needs, for the preview breakdown. */
    public int surfaceCount(Surface surface) {
        return surface(surface).size();
    }

    /**
     * Every cell to mine, in the order to mine it.
     *
     * <p>Two things drive this, and both are load-bearing:</p>
     *
     * <p><strong>Slice by slice, near to far.</strong> Both a room and a spine segment are cut into
     * rock from outside, so the far end is neither reachable nor walkable until the near end is open.
     * Carving the whole ceiling first — which is right for a space you stand in the middle of —
     * deadlocks here: the far ceiling is past the 4.5 reach and you cannot walk in to close the gap.
     * Within each slice the ceiling still leads, so anything unstable overhead drops while there is
     * still solid ground beneath it.</p>
     *
     * <p><strong>Floor recesses last, across the whole job.</strong> Carving a room's floor drops the
     * player a block, and a lower eye costs reach on every ceiling cell after it. Leaving all the
     * floors to a final pass means the job is worked from the original standing height throughout and
     * the one-block drop happens at the very end. A spine segment has no floor recess, so this pass is
     * simply empty for it.</p>
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
        int slice = d >= 0 ? d : -d;
        // Ceiling leads each slice, then the body downwards.
        int tier = dy == RoomGeometry.INTERIOR_SIZE ? 0 : RoomGeometry.INTERIOR_SIZE - dy;
        return new SortKey(isFloor ? 1 : 0, unit, slice, tier, Math.abs(side), side);
    }

    private record SortKey(int floorPass, int unit, int slice, int tier, int absSide, int side) {}
}
