/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

/**
 * What a job will cost, for {@code /mallroom preview} and the HUD.
 *
 * @param airCount  cells that must end up as air
 * @param skinCount cells that must end up as the build block
 */
public record MallCounts(int airCount, int skinCount) {

    /** Blocks to mine: every air cell plus every skin cell (the skin cavity is carved, then filled). */
    public int minedTotal() {
        return airCount + skinCount;
    }

    /** Build blocks needed, in full stacks, rounded up. */
    public int stacksNeeded() {
        return (skinCount + 63) / 64;
    }
}
