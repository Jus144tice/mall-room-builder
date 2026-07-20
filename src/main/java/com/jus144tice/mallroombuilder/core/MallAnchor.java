/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

/**
 * Where the job goes, derived purely from where the player is standing and which way they face.
 *
 * <p><strong>Nothing is inferred from the world.</strong> The rule is fixed: you stand on the spine,
 * facing the way you want to build, and <em>the block directly in front of you is the first block of
 * the job</em>. For a room you are laterally centred on it; for a spine segment you are on the centre
 * lane.</p>
 *
 * <p>That determinism is the whole point. An earlier version scanned forward for the first solid
 * block, which broke the moment a room was half-carved — the scan would sail through the opening and
 * anchor the room somewhere else. With a fixed offset the same standing position always describes the
 * same volume, so a partial job can simply be re-run to finish it.</p>
 *
 * <p>Vertically, the room interior occupies {@code y in [feetY, feetY+4]}, its floor recess sits at
 * {@code feetY-1} and its ceiling recess at {@code feetY+5}. A spine segment is the interior height
 * only — its floor stays where you are standing, which is what leaves a finished room flush with the
 * corridor once you lay the room's floor in by hand.</p>
 *
 * <p>The anchor is a <strong>snapshot</strong>. The player is about to be walked around and will drop
 * a block into the floor recess; tracking them live would drift the geometry mid-job.</p>
 */
public record MallAnchor(GridPos playerFeet, Facing facing) {

    /**
     * How far ahead a job starts: always the very next block. Standing on the spine facing a room,
     * one step forward would put you off the spine and inside it.
     */
    public static final int START_OFFSET = 1;

    public static MallAnchor of(int feetX, int feetY, int feetZ, float yaw) {
        return new MallAnchor(new GridPos(feetX, feetY, feetZ), Facing.fromYaw(yaw));
    }

    /** The room you are facing. Its opening plane is the block directly in front of you. */
    public RoomPlacement facedRoom() {
        return new RoomPlacement(cell(START_OFFSET, 0, playerFeet.y()), facing);
    }

    /**
     * The room directly opposite, across the corridor. Its opening plane is the far wall of the
     * spine and it extends the other way, so the two rooms mirror each other.
     */
    public RoomPlacement oppositeRoom(MallSpec spec) {
        int along = START_OFFSET - spec.oppositeOpeningOffset();
        return new RoomPlacement(cell(along, 0, playerFeet.y()), facing.opposite());
    }

    /** The spine segment ahead of you, starting at the very next block. */
    public RoomPlacement spineStart() {
        return new RoomPlacement(cell(START_OFFSET, 0, playerFeet.y()), facing);
    }

    /** Y of a room's floor recess — one below the surface you are standing on. */
    public int floorPlateY() {
        return playerFeet.y() - 1;
    }

    /** Y of a room's ceiling recess. */
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
