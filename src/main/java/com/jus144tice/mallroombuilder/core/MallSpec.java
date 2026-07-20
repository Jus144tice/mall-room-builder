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
 * <p>{@code finishRecesses} is the rough/finish switch. A <em>rough</em> job cuts only the finished
 * volume; a <em>finish</em> job also cuts the 1-block recesses that hold the decorative course.
 * Because the anchor is world-independent and already-carved cells retire on sight, roughing a run
 * out now and re-running the same jobs later cuts exactly the recesses and nothing else.</p>
 *
 * @param kind           room or spine
 * @param bothSides      rooms only: also carve the room directly opposite, across the corridor
 * @param hallDepth      corridor width in blocks. Sets how far back the opposite room sits.
 * @param spineLength    spine only: blocks along the run
 * @param finishRecesses cut the finishing recesses as well as the finished volume
 */
public record MallSpec(Kind kind, boolean bothSides, int hallDepth, int spineLength, boolean finishRecesses) {

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

    public static MallSpec room(boolean bothSides, int hallDepth, boolean finishRecesses) {
        return new MallSpec(Kind.ROOM, bothSides, hallDepth, 0, finishRecesses);
    }

    public static MallSpec spine(int length, int hallDepth, boolean finishRecesses) {
        return new MallSpec(Kind.SPINE, false, hallDepth, length, finishRecesses);
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

    /** "rough" or "finish", for chat and the HUD. */
    public String modeName() {
        return finishRecesses ? "finish" : "rough";
    }
}
