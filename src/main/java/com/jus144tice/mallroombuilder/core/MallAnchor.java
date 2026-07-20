/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

/**
 * Where the job goes, derived from where the player stood and what they were looking at.
 *
 * <p>You stand in the spine hallway and face the wall you want opened. Three things are frozen at
 * that moment:</p>
 *
 * <ul>
 *   <li><strong>Facing</strong>, snapped to a cardinal — the direction the room extends.</li>
 *   <li><strong>Feet</strong>, floored. The room's floor is level with the hallway's, so the
 *       interior occupies {@code y in [feetY, feetY+4]}, the floor plate sits at {@code feetY-1}
 *       and the ceiling plate at {@code feetY+5}. Laterally the room is centred on you.</li>
 *   <li><strong>Opening distance</strong> — how many blocks ahead the hallway wall is, found by
 *       scanning rather than assumed, so it does not matter where across the 3-wide corridor you
 *       happen to be standing.</li>
 * </ul>
 *
 * <p>All three are a <strong>snapshot</strong>. The player is about to be walked around and may
 * drop a block off the last floor plate; tracking them live would drift the geometry mid-job.</p>
 */
public record MallAnchor(GridPos playerFeet, Facing facing, int openingDistance) {

    public static MallAnchor of(int feetX, int feetY, int feetZ, float yaw, int openingDistance) {
        return new MallAnchor(new GridPos(feetX, feetY, feetZ), Facing.fromYaw(yaw), openingDistance);
    }

    /** The room you are looking at. */
    public RoomPlacement facedRoom() {
        return new RoomPlacement(cell(openingDistance, 0, playerFeet.y()), facing);
    }

    /**
     * The room directly opposite, across the corridor. Its opening plane is the far wall of the
     * hallway and it extends the other way, so the two rooms mirror each other about the spine.
     */
    public RoomPlacement oppositeRoom(MallSpec spec) {
        int along = openingDistance - spec.oppositeOpeningOffset();
        return new RoomPlacement(cell(along, 0, playerFeet.y()), facing.opposite());
    }

    /** Y of the room and hallway floor plate — the block the player is standing on. */
    public int floorPlateY() {
        return playerFeet.y() - 1;
    }

    /** Y of the ceiling plate. */
    public int ceilingPlateY() {
        return playerFeet.y() + RoomGeometry.INTERIOR_SIZE;
    }

    /**
     * Converts an anchor-relative cell to an absolute position.
     *
     * @param along blocks ahead of the feet block along {@link #facing}
     * @param side  blocks to the left of the feet block
     * @param y     absolute world Y
     */
    public GridPos cell(int along, int side, int y) {
        return playerFeet.at(facing, along, side, y);
    }

    /** How many blocks ahead of the anchor a cell sits. Exact — a dot product with a unit vector. */
    public int alongOf(GridPos pos) {
        GridPos d = pos.minus(playerFeet);
        return d.x() * facing.stepX() + d.z() * facing.stepZ();
    }

    /** How many blocks to the left of the anchor a cell sits. */
    public int sideOf(GridPos pos) {
        GridPos d = pos.minus(playerFeet);
        Facing left = facing.left();
        return d.x() * left.stepX() + d.z() * left.stepZ();
    }
}
