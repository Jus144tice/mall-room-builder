/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Drives the engine once per client tick, and cancels the job on anything that invalidates it. */
public final class ClientEvents {

    private static ResourceKey<Level> lastDimension;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.level == null) {
            if (JobEngine.INSTANCE.isRunning()) {
                JobEngine.INSTANCE.abort(mc, "left the world");
            }
            lastDimension = null;
            return;
        }

        // The anchor is world coordinates, so it means nothing after a dimension change.
        ResourceKey<Level> dimension = mc.level.dimension();
        if (lastDimension != null && !lastDimension.equals(dimension) && JobEngine.INSTANCE.isRunning()) {
            JobEngine.INSTANCE.abort(mc, "changed dimension");
        }
        lastDimension = dimension;

        if (mc.isPaused()) {
            return;
        }

        JobEngine.INSTANCE.tick(mc);
    }
}
