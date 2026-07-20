/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

/**
 * One room's position and orientation: where its opening is, and which way it goes back.
 *
 * <p>Rooms bud off a spine hallway perpendicular to it, so a room is <em>directed</em> — the face
 * toward the hallway is open and the opposite face is a solid back wall. Everything about a room is
 * therefore described in three facing-relative numbers:</p>
 *
 * <ul>
 *   <li><strong>depth</strong> — blocks back from the opening plane. 0 is the opening itself, 4 is
 *       the last interior plane, 5 is the back wall plate.</li>
 *   <li><strong>side</strong> — blocks left or right along the spine. The interior spans +/-2 and
 *       the pillars sit at +/-3.</li>
 *   <li><strong>y</strong> — absolute world height. The floor plate is one below the opening
 *       centre, the ceiling plate five above.</li>
 * </ul>
 *
 * @param openingCentre the middle cell of the opening plane, at floor height — the block a player
 *                      standing in the hallway looks straight at
 * @param depth         the direction the room extends, away from the hallway
 */
public record RoomPlacement(GridPos openingCentre, Facing depth) {

    /** Absolute position of a facing-relative cell. */
    public GridPos cell(int d, int side, int y) {
        return openingCentre.at(depth, d, side, y);
    }

    /** How far back from the opening a cell sits. Exact — a dot product with a unit vector. */
    public int depthOf(GridPos pos) {
        GridPos v = pos.minus(openingCentre);
        return v.x() * depth.stepX() + v.z() * depth.stepZ();
    }

    /** How far along the spine a cell sits, relative to the opening's centre. */
    public int sideOf(GridPos pos) {
        GridPos v = pos.minus(openingCentre);
        Facing left = depth.left();
        return v.x() * left.stepX() + v.z() * left.stepZ();
    }

    /** Floor level of the interior — the surface the player walks on. */
    public int floorY() {
        return openingCentre.y();
    }

    /** Y of the floor plate, one below the walking surface. */
    public int floorPlateY() {
        return openingCentre.y() - 1;
    }

    /** Y of the ceiling plate. */
    public int ceilingPlateY() {
        return openingCentre.y() + RoomGeometry.INTERIOR_SIZE;
    }
}
