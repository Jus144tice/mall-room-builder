/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The composer, and the single source of truth for what a job will touch.
 *
 * <p>Rooms and hallways are generated independently and then combined by set algebra in which
 * <strong>air always wins</strong>. That one rule handles the doorways: a hallway's air volume
 * spans the two room wall plates it pierces, so subtracting air from skin at the end removes the 15
 * cells of each pierced plate and leaves a clean 10-block jamb. No special-casing.</p>
 *
 * <h2>Counts are emergent, not fixed</h2>
 *
 * <p>An isolated room is 150 skin and 275 mined. Once rooms are joined those numbers move —
 * doorways subtract, hallways add — so nothing downstream should assume a per-room figure. Ask
 * {@link #counts()}. For reference, two rooms at {@code hallLength = 5} come to 355 air + 362 skin
 * = 717 mined.</p>
 */
public final class MallLayout {

    private final MallAnchor anchor;
    private final MallSpec spec;
    private final Set<GridPos> air;
    private final Set<GridPos> skin;

    public MallLayout(MallAnchor anchor, MallSpec spec) {
        this.anchor = anchor;
        this.spec = spec;

        Set<GridPos> airCells = new LinkedHashSet<>();
        Set<GridPos> skinCells = new LinkedHashSet<>();

        for (int i = 0; i < spec.roomCount(); i++) {
            GridPos reference = anchor.roomReference(spec, i);
            airCells.addAll(RoomGeometry.interior(reference));
            skinCells.addAll(RoomGeometry.visibleSkin(reference));
        }
        for (int i = 0; i < spec.hallCount(); i++) {
            airCells.addAll(HallGeometry.interior(anchor, spec, i));
            skinCells.addAll(HallGeometry.visibleSkin(anchor, spec, i));
        }

        // Air wins: this is what cuts the doorways out of the room wall plates.
        skinCells.removeAll(airCells);

        this.air = Collections.unmodifiableSet(airCells);
        this.skin = Collections.unmodifiableSet(skinCells);
    }

    /** Cells that must end up as air. */
    public Set<GridPos> air() {
        return air;
    }

    /** Cells that must end up as the build block. Disjoint from {@link #air()}. */
    public Set<GridPos> skin() {
        return skin;
    }

    public MallAnchor anchor() {
        return anchor;
    }

    public MallSpec spec() {
        return spec;
    }

    public MallCounts counts() {
        return new MallCounts(air.size(), skin.size());
    }

    /**
     * Every cell to mine, in the order to mine it.
     *
     * <p>Ordering is driven by two hazards: falling into a hole you just dug, and gravel dropping
     * in from above. Within each unit (room 0, hall 0, room 1, ...) the pass order is:</p>
     *
     * <ol>
     *   <li><strong>Ceiling plate first.</strong> Anything unstable above the room is disturbed
     *       while the room is still solid, so it lands on un-carved ground and gets swept up later
     *       rather than burying a hole. The far ceiling corner is about 4.40 blocks from a standing
     *       eye — inside the 4.5 default reach — so the whole ceiling is minable from the original
     *       floor without repositioning.</li>
     *   <li><strong>Then top-down through the body</strong>, one Y layer at a time, so nothing is
     *       ever undermined.</li>
     *   <li><strong>Floor plate last</strong>, near to far, so the player mines the floor behind and
     *       beside themselves and always has un-mined floor ahead.</li>
     * </ol>
     *
     * <p>Units run in travel order, so the player walks forward through the mall exactly once.</p>
     */
    public List<GridPos> mineOrder() {
        List<GridPos> all = new ArrayList<>(air.size() + skin.size());
        all.addAll(air);
        all.addAll(skin);
        return sorted(all);
    }

    /**
     * Every cell to place, in the order to place it — simply the reverse of the carve order,
     * restricted to skin.
     *
     * <p>The reversal is load-bearing, not a convenience. Carving ends at the far end of the mall,
     * so building starts there and there is no walk back. And reversing ceiling-then-body-then-floor
     * gives floor, then body, then ceiling — the only order that works, because the floor has to
     * exist before the player can stand at the right height, and the ceiling wants to go last from
     * below where it is always in reach.
     */
    public List<GridPos> buildOrder() {
        List<GridPos> out = new ArrayList<>(skin);
        out = sorted(out);
        Collections.reverse(out);
        return out;
    }

    private List<GridPos> sorted(List<GridPos> cells) {
        List<Keyed> keyed = new ArrayList<>(cells.size());
        for (GridPos p : cells) {
            keyed.add(key(p));
        }
        keyed.sort(Comparator.comparingInt(Keyed::unit)
                .thenComparingInt(Keyed::pass)
                .thenComparingInt(Keyed::layer)
                .thenComparingInt(Keyed::along)
                .thenComparingInt(Keyed::absSide)
                .thenComparingInt(Keyed::side)
                .thenComparingInt(k -> k.pos().y()));
        List<GridPos> out = new ArrayList<>(keyed.size());
        for (Keyed k : keyed) {
            out.add(k.pos());
        }
        return out;
    }

    private Keyed key(GridPos pos) {
        int along = anchor.alongOf(pos);
        int side = anchor.sideOf(pos);
        int dy = pos.y() - anchor.playerFeet().y();

        int pass;
        int layer;
        if (dy == RoomGeometry.INTERIOR_SIZE) {
            pass = 0; // ceiling plate
            layer = 0;
        } else if (dy == -1) {
            pass = 2; // floor plate
            layer = 0;
        } else {
            pass = 1; // body, top-down
            layer = RoomGeometry.INTERIOR_SIZE - 1 - dy;
        }
        return new Keyed(pos, unitOrdinal(along), pass, layer, along, Math.abs(side), side);
    }

    /**
     * Which unit a cell belongs to, from its along-coordinate alone: room {@code i} is
     * {@code 2i}, the hallway following it is {@code 2i + 1}.
     *
     * <p>Deriving this from {@code along} only is what guarantees a floor-plate cell and the
     * interior directly above it land in the same unit — and therefore that the floor is always
     * mined after the body above it.</p>
     */
    private int unitOrdinal(int along) {
        int pitch = spec.pitch();
        int room = Math.floorDiv(along + RoomGeometry.ENVELOPE_RADIUS, pitch);
        room = Math.max(0, Math.min(spec.roomCount() - 1, room));
        boolean insideRoom = along <= room * pitch + RoomGeometry.ENVELOPE_RADIUS;
        return insideRoom ? room * 2 : room * 2 + 1;
    }

    private record Keyed(GridPos pos, int unit, int pass, int layer, int along, int absSide, int side) {}
}
