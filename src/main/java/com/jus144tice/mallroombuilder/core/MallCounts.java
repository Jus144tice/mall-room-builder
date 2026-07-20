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
public record MallCounts(int carvedCount, int framingCount) {

    /** Blocks to mine. Nothing is placed, so this is the whole job. */
    public int minedTotal() {
        return carvedCount;
    }

    /** Every cell the job is aware of, carved or protected. */
    public int envelopeTotal() {
        return carvedCount + framingCount;
    }
}
