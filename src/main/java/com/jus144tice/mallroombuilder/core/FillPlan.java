/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Which hotbar slot supplies which surface.
 *
 * <p>Slots are given as the player sees them, <strong>1 to 9</strong>; {@link #inventoryIndex} does
 * the off-by-one into {@code Inventory.selected}. A surface with no entry is simply not filled, so a
 * plan can cover any subset — floor only, everything but the beam, and so on.</p>
 */
public final class FillPlan {

    private static final FillPlan NONE = new FillPlan(new EnumMap<>(Surface.class));

    private final Map<Surface, Integer> slots;

    private FillPlan(Map<Surface, Integer> slots) {
        this.slots = Collections.unmodifiableMap(slots);
    }

    /** A plan that fills nothing. */
    public static FillPlan none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    public boolean covers(Surface surface) {
        return slots.containsKey(surface);
    }

    /** The 1-9 hotbar slot for a surface, or null if it is not being filled. */
    public Integer slot(Surface surface) {
        return slots.get(surface);
    }

    /** The 0-8 inventory index for a surface. Throws if the surface is not covered. */
    public int inventoryIndex(Surface surface) {
        Integer slot = slots.get(surface);
        if (slot == null) {
            throw new IllegalStateException("no slot assigned for " + surface.key());
        }
        return slot - 1;
    }

    /** Covered surfaces, in fill order (the enum's declaration order). */
    public List<Surface> surfaces() {
        return Arrays.stream(Surface.values()).filter(slots::containsKey).toList();
    }

    @Override
    public String toString() {
        if (slots.isEmpty()) {
            return "no fill";
        }
        StringBuilder sb = new StringBuilder();
        for (Surface s : surfaces()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(s.key()).append('=').append(slots.get(s));
        }
        return sb.toString();
    }

    public static final class Builder {

        private final Map<Surface, Integer> slots = new EnumMap<>(Surface.class);

        /** Assigns a 1-9 hotbar slot to a surface. A null slot leaves the surface unfilled. */
        public Builder with(Surface surface, Integer slot) {
            if (slot == null) {
                return this;
            }
            if (slot < 1 || slot > 9) {
                throw new IllegalArgumentException("hotbar slot must be 1-9, got " + slot);
            }
            slots.put(surface, slot);
            return this;
        }

        public FillPlan build() {
            return slots.isEmpty() ? NONE : new FillPlan(new EnumMap<>(slots));
        }
    }
}
