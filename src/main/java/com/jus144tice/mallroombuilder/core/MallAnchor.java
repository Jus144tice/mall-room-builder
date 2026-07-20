/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

/**
 * Where the mall goes, derived from where the player stood when the command was issued.
 *
 * <p><strong>This is a snapshot, taken once and never re-read.</strong> The player is about to be
 * walked around by the auto-walk driver and may fall a block off the last floor plate; if the
 * geometry tracked their live position it would drift mid-job. Freezing it here is what makes the
 * whole layout deterministic.</p>
 *
 * <p>Conventions, all relative to the player's feet block:</p>
 * <ul>
 *   <li><strong>Floor Y = feet Y.</strong> The interior occupies {@code y in [feetY, feetY+4]}, the
 *       floor plate sits at {@code feetY-1} (the block being stood on) and the ceiling plate at
 *       {@code feetY+5}. The envelope therefore spans 7 in Y.</li>
 *   <li><strong>Laterally centred on the player.</strong> The interior spans +/-2 and the envelope
 *       +/-3 from the feet block on both horizontal axes.</li>
 *   <li>Room <em>i</em> sits {@code i * pitch} blocks along {@code facing}. The player is standing
 *       inside room 0.</li>
 * </ul>
 *
 * @param playerFeet the block containing the player's feet at command time
 * @param facing     the player's yaw snapped to the nearest cardinal
 */
public record MallAnchor(GridPos playerFeet, Facing facing) {

    /** Builds an anchor from a floored feet position and a raw (unnormalised) player yaw. */
    public static MallAnchor of(int feetX, int feetY, int feetZ, float yaw) {
        return new MallAnchor(new GridPos(feetX, feetY, feetZ), Facing.fromYaw(yaw));
    }

    /** The reference cell of room {@code index}: horizontally centred, at floor height. */
    public GridPos roomReference(MallSpec spec, int index) {
        return playerFeet.offset(facing, index * spec.pitch());
    }

    /** Y of the room floor plate — the block the player is standing on. */
    public int floorPlateY() {
        return playerFeet.y() - 1;
    }

    /** Y of the room ceiling plate. */
    public int ceilingPlateY() {
        return playerFeet.y() + RoomGeometry.INTERIOR_SIZE;
    }

    /**
     * Converts a facing-relative cell to an absolute position.
     *
     * @param along blocks forward of the feet block along {@link #facing}
     * @param side  blocks to the left of the feet block
     * @param y     absolute world Y
     */
    public GridPos cell(int along, int side, int y) {
        return playerFeet.at(facing, along, side, y);
    }

    /**
     * How many blocks forward of the anchor a cell sits. The inverse of {@link #cell}'s
     * {@code along}; a dot product with the unit facing vector, so it is exact.
     */
    public int alongOf(GridPos pos) {
        GridPos d = pos.minus(playerFeet);
        return d.x() * facing.stepX() + d.z() * facing.stepZ();
    }

    /** How many blocks to the left of the anchor a cell sits. The inverse of {@link #cell}'s {@code side}. */
    public int sideOf(GridPos pos) {
        GridPos d = pos.minus(playerFeet);
        Facing left = facing.left();
        return d.x() * left.stepX() + d.z() * left.stepZ();
    }
}
