/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import java.util.List;

/**
 * A face of a carved volume that can be filled with its own material.
 *
 * <p>The order the constants are declared in is the order they are filled: floor first — it restores
 * the walking level the carve dropped you from — then walls, then ceiling and beam last, placed from
 * below where they are always in reach.</p>
 */
public enum Surface {

    /** The 1-block recess under the walking surface. Filling it puts you back at your original level. */
    FLOOR("floor"),

    /** A room's back wall plus both side walls. Spine segments have none. */
    WALLS("walls"),

    /** The recess above the room, excluding the beam row at the opening. */
    CEILING("ceiling"),

    /** The ceiling row across the entrance plane — the lintel over the opening. Rooms only. */
    BEAM("beam");

    private final String key;

    Surface(String key) {
        this.key = key;
    }

    /** The word used for this surface as a command argument. */
    public String key() {
        return key;
    }

    /** Surfaces a room can have. */
    public static List<Surface> forRoom() {
        return List.of(FLOOR, WALLS, CEILING, BEAM);
    }

    /** Surfaces a spine segment can have: its sides are always some room's wall or opening. */
    public static List<Surface> forSpine() {
        return List.of(FLOOR, CEILING);
    }

    public static List<Surface> forKind(MallSpec.Kind kind) {
        return kind == MallSpec.Kind.SPINE ? forSpine() : forRoom();
    }
}
