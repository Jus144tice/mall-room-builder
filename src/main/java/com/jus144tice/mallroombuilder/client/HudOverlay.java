/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.client;

import com.jus144tice.mallroombuilder.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Progress readout while a job runs.
 *
 * <p>Not decoration. A full mall is hundreds of blocks at a hard floor of about five ticks each, so
 * a job runs for minutes; without a visible counter there is no way to tell "working" from
 * "wedged".</p>
 */
public final class HudOverlay {

    private static final int MARGIN = 6;
    private static final int TEXT_COLOUR = 0xFFE0E0E0;

    private HudOverlay() {}

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!Config.showHudStatus() || !JobEngine.INSTANCE.isRunning()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.drawString(mc.font, "[Mall] " + JobEngine.INSTANCE.statusLine(), MARGIN, MARGIN, TEXT_COLOUR, true);
        graphics.drawString(
                mc.font, "any key or mouse move to stop", MARGIN, MARGIN + mc.font.lineHeight + 1, 0xFF909090, true);
    }
}
