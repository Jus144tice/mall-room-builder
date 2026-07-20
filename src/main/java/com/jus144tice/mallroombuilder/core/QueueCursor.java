/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * An ordered work list with a deferral tail and a bounded sweep counter.
 *
 * <p>Work is not strictly sequential: a block can be temporarily unworkable (out of reach, the
 * player is standing on it, a skin cell has nothing to place against yet) without being a failure.
 * Such cells are <em>deferred</em> to a tail list; when the main list runs dry, a sweep moves the
 * tail back to the front and tries again. Sweeps are capped, so a job that genuinely cannot finish
 * stops instead of spinning.</p>
 *
 * <p>Completion is always decided by the caller against the world, never assumed here — that is
 * what lets the engine recover from server rejections, falling gravel and unloaded chunks with one
 * mechanism.</p>
 */
public final class QueueCursor {

    private final int total;
    private final Set<GridPos> pending;
    private final Set<GridPos> deferred = new LinkedHashSet<>();
    private final int maxSweeps;
    private int sweeps;

    public QueueCursor(List<GridPos> order, int maxSweeps) {
        this.pending = new LinkedHashSet<>(order);
        this.total = pending.size();
        this.maxSweeps = maxSweeps;
    }

    /** The first pending cell matching {@code filter}, or null. Does not consume it. */
    public GridPos select(Predicate<GridPos> filter) {
        for (GridPos p : pending) {
            if (filter.test(p)) {
                return p;
            }
        }
        return null;
    }

    /** The next pending cell in order regardless of workability, or null — the steering target. */
    public GridPos peek() {
        for (GridPos p : pending) {
            return p;
        }
        return null;
    }

    /** Marks a cell finished, wherever it currently sits. */
    public void complete(GridPos pos) {
        pending.remove(pos);
        deferred.remove(pos);
    }

    /** Moves a cell to the tail to be retried after the next sweep. */
    public void defer(GridPos pos) {
        if (pending.remove(pos)) {
            deferred.add(pos);
        }
    }

    /** Puts cells back into the pending list — used when a world re-scan finds unfinished work. */
    public void requeue(Collection<GridPos> cells) {
        pending.addAll(cells);
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public boolean hasDeferred() {
        return !deferred.isEmpty();
    }

    /**
     * Moves every deferred cell back into the pending list.
     *
     * @return false if the sweep budget is exhausted, meaning the job should stop
     */
    public boolean sweep() {
        if (deferred.isEmpty()) {
            return true;
        }
        if (sweeps >= maxSweeps) {
            return false;
        }
        sweeps++;
        pending.addAll(deferred);
        deferred.clear();
        return true;
    }

    public int sweepsUsed() {
        return sweeps;
    }

    public int total() {
        return total;
    }

    public int remaining() {
        return pending.size() + deferred.size();
    }

    public int done() {
        return total - remaining();
    }

    public int deferredCount() {
        return deferred.size();
    }

    /** Snapshot of everything not yet finished, pending first then deferred, in order. */
    public List<GridPos> outstanding() {
        List<GridPos> out = new ArrayList<>(remaining());
        out.addAll(pending);
        out.addAll(deferred);
        return out;
    }
}
