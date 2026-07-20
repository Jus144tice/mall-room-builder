/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

/**
 * What one job carves: either a room off the spine, or the next segment of spine itself.
 *
 * <p>Both start at the same place — the block directly in front of the player — so the two commands
 * share an anchor and differ only in the volume they describe.</p>
 *
 * @param kind        room or spine
 * @param bothSides   rooms only: also carve the room directly opposite, across the corridor
 * @param hallDepth   corridor width in blocks. Sets how far back the opposite room sits.
 * @param spineLength spine only: blocks along the run
 */
public record MallSpec(Kind kind, boolean bothSides, int hallDepth, int spineLength) {

    public enum Kind {
        ROOM,
        SPINE
    }

    public MallSpec {
        if (hallDepth < 1) {
            throw new IllegalArgumentException("hallDepth must be >= 1, got " + hallDepth);
        }
        if (kind == Kind.SPINE && spineLength < 1) {
            throw new IllegalArgumentException("spineLength must be >= 1, got " + spineLength);
        }
    }

    public static MallSpec room(boolean bothSides, int hallDepth) {
        return new MallSpec(Kind.ROOM, bothSides, hallDepth, 0);
    }

    public static MallSpec spine(int length, int hallDepth) {
        return new MallSpec(Kind.SPINE, false, hallDepth, length);
    }

    /** Rooms this job will carve. Zero for a spine segment. */
    public int roomCount() {
        if (kind != Kind.ROOM) {
            return 0;
        }
        return bothSides ? 2 : 1;
    }

    /**
     * How far back the opposite room's opening plane sits from this one: across the corridor, plus
     * the two wall planes the openings occupy.
     */
    public int oppositeOpeningOffset() {
        return hallDepth + 1;
    }
}
