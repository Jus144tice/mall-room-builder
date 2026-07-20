/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class QueueCursorTest {

    private static final GridPos A = new GridPos(0, 0, 0);
    private static final GridPos B = new GridPos(1, 0, 0);
    private static final GridPos C = new GridPos(2, 0, 0);

    private static QueueCursor cursor(int maxSweeps) {
        return new QueueCursor(List.of(A, B, C), maxSweeps);
    }

    @Test
    void selectReturnsTheFirstMatchInOrderWithoutConsuming() {
        QueueCursor q = cursor(4);
        assertEquals(A, q.select(p -> true));
        assertEquals(A, q.select(p -> true), "select does not consume");
        assertEquals(B, q.select(p -> p.x() > 0));
        assertNull(q.select(p -> p.x() > 99));
    }

    @Test
    void peekIgnoresWorkabilityAndGivesTheSteeringTarget() {
        QueueCursor q = cursor(4);
        assertEquals(A, q.peek());
        q.complete(A);
        assertEquals(B, q.peek());
    }

    @Test
    void completeRemovesFromWhereverTheCellSits() {
        QueueCursor q = cursor(4);
        q.defer(A);
        assertEquals(1, q.deferredCount());
        q.complete(A);
        assertEquals(0, q.deferredCount());
        assertEquals(1, q.done());

        q.complete(B);
        assertEquals(2, q.done());
        assertEquals(1, q.remaining());
    }

    @Test
    void deferMovesToTheTailAndSweepBringsItBack() {
        QueueCursor q = cursor(4);
        q.defer(A);
        assertFalse(q.select(p -> p.equals(A)) != null, "deferred cells are not selectable");
        assertEquals(B, q.peek());

        assertTrue(q.sweep());
        assertEquals(1, q.sweepsUsed());
        assertEquals(A, q.select(p -> p.equals(A)), "back in play after the sweep");
    }

    @Test
    void sweepIsBoundedSoAnUnfinishableJobStopsInsteadOfSpinning() {
        QueueCursor q = cursor(2);
        for (int i = 0; i < 2; i++) {
            q.defer(A);
            assertTrue(q.sweep(), "sweep " + i + " should be allowed");
        }
        q.defer(A);
        assertFalse(q.sweep(), "third sweep exceeds the budget");
        assertEquals(2, q.sweepsUsed());
    }

    @Test
    void sweepingWithNothingDeferredIsAFreeNoOp() {
        QueueCursor q = cursor(0);
        assertTrue(q.sweep(), "nothing to do, so not a failure even with a zero budget");
        assertEquals(0, q.sweepsUsed());
    }

    @Test
    void requeuePutsWorldRescanFindingsBackIntoPlay() {
        QueueCursor q = cursor(4);
        q.complete(A);
        q.complete(B);
        q.complete(C);
        assertFalse(q.hasPending());

        q.requeue(List.of(A, C));
        assertTrue(q.hasPending());
        assertEquals(2, q.remaining());
        assertEquals(1, q.done());
    }

    @Test
    void tracksProgressAgainstTheOriginalTotal() {
        QueueCursor q = cursor(4);
        assertEquals(3, q.total());
        assertEquals(3, q.remaining());
        assertEquals(0, q.done());

        q.complete(A);
        q.defer(B);
        assertEquals(1, q.done());
        assertEquals(2, q.remaining(), "deferred still counts as outstanding");
        assertTrue(q.hasDeferred());
    }

    @Test
    void outstandingListsPendingFirstThenDeferred() {
        QueueCursor q = cursor(4);
        q.defer(A);
        assertEquals(List.of(B, C, A), q.outstanding());
    }

    @Test
    void duplicateInputCellsAreCollapsed() {
        QueueCursor q = new QueueCursor(List.of(A, A, B), 4);
        assertEquals(2, q.total());
    }
}
