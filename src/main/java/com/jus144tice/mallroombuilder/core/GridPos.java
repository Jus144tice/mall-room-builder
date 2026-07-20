/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

/**
 * Minecraft-free integer block position.
 *
 * <p>The whole {@code core} package is deliberately free of Minecraft types so the mall geometry
 * can be unit-tested without booting a client. {@code client/} converts {@code BlockPos} to and
 * from this record at the boundary and nowhere else.</p>
 */
public record GridPos(int x, int y, int z) {

    public GridPos plus(int dx, int dy, int dz) {
        return new GridPos(x + dx, y + dy, z + dz);
    }

    public GridPos minus(GridPos other) {
        return new GridPos(x - other.x, y - other.y, z - other.z);
    }

    /** Steps {@code n} blocks along {@code facing} (the mall's forward axis). */
    public GridPos offset(Facing facing, int n) {
        return new GridPos(x + facing.stepX() * n, y, z + facing.stepZ() * n);
    }

    /** Steps {@code n} blocks along {@code facing.left()} (the mall's lateral axis). */
    public GridPos lateral(Facing facing, int n) {
        Facing left = facing.left();
        return new GridPos(x + left.stepX() * n, y, z + left.stepZ() * n);
    }

    public GridPos withY(int newY) {
        return new GridPos(x, newY, z);
    }

    /**
     * Position {@code along} blocks forward, {@code side} blocks left, at absolute height
     * {@code atY}, measured from this position. The three-axis helper the geometry classes use to
     * describe every cell in facing-relative terms.
     */
    public GridPos at(Facing facing, int along, int side, int atY) {
        return offset(facing, along).lateral(facing, side).withY(atY);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
