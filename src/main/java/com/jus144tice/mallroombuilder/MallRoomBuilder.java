/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder;

import com.jus144tice.mallroombuilder.client.AutoWalk;
import com.jus144tice.mallroombuilder.client.ClientEvents;
import com.jus144tice.mallroombuilder.client.HudOverlay;
import com.jus144tice.mallroombuilder.client.MallCommand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entrypoint for Mall Room Builder.
 *
 * <p>Strictly client-side ({@code dist = Dist.CLIENT}). Unlike its siblings in this collection this
 * mod has <strong>no mixins</strong>: auto-walk rides NeoForge's {@code MovementInputUpdateEvent},
 * and mining and placing are direct public calls on {@code MultiPlayerGameMode}. Nothing here can
 * fail at class-transform time, and every vanilla symbol it depends on fails at compile time rather
 * than in someone's game. See {@code docs/vanilla-hooks.md}.</p>
 *
 * <p>It sends only vanilla packets, but it does initiate mining and steer movement without input,
 * so it is <em>not</em> "vanilla-server-safe" in the way {@code bedrock-line-placement} is. Treat it
 * as a singleplayer and private-server tool.</p>
 */
@Mod(value = MallRoomBuilder.MODID, dist = Dist.CLIENT)
public final class MallRoomBuilder {

    public static final String MODID = "mallroombuilder";
    public static final Logger LOGGER = LoggerFactory.getLogger("MallRoomBuilder");

    public MallRoomBuilder(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        // All three live on the game bus. RegisterClientCommandsEvent in particular re-fires on
        // every world join, so it must not be on the mod bus.
        NeoForge.EVENT_BUS.register(ClientEvents.class);
        NeoForge.EVENT_BUS.register(MallCommand.class);
        NeoForge.EVENT_BUS.register(AutoWalk.class);
        NeoForge.EVENT_BUS.register(HudOverlay.class);

        LOGGER.info("Mall Room Builder loaded (client-only, no mixins).");
    }

    /** Debug logging, gated so the common case costs nothing. */
    public static void debug(String message) {
        if (Config.debugLogging()) {
            LOGGER.info("[MRB] {}", message);
        }
    }
}
