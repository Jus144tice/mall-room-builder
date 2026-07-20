/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

/**
 * What a job will cost, for {@code /mallroom preview} and the HUD.
 *
 * @param carvedCount  cells to mine
 * @param framingCount cells left standing and watched for backfill
 */
public record MallCounts(int carvedCount, int framingCount, int filledCount) {

    public MallCounts(int carvedCount, int framingCount) {
        this(carvedCount, framingCount, 0);
    }

    /** Blocks to mine. */
    public int minedTotal() {
        return carvedCount;
    }

    /** Blocks to place, across every surface being filled. */
    public int placedTotal() {
        return filledCount;
    }

    /** Full stacks of material needed, rounded up. Only meaningful if one material is used throughout. */
    public int stacksNeeded() {
        return (filledCount + 63) / 64;
    }

    /** Every cell the job is aware of, carved or protected. */
    public int envelopeTotal() {
        return carvedCount + framingCount;
    }
}
