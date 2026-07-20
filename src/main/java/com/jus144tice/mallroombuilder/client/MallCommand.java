/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.client;

import com.jus144tice.mallroombuilder.Config;
import com.jus144tice.mallroombuilder.core.MallAnchor;
import com.jus144tice.mallroombuilder.core.MallCounts;
import com.jus144tice.mallroombuilder.core.MallLayout;
import com.jus144tice.mallroombuilder.core.MallSpec;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * The {@code /mallroom} command tree.
 *
 * <p>You stand in the spine hallway facing the wall you want opened; facing picks the side. Adding
 * {@code both} also carves the room directly opposite.</p>
 *
 * <p>Registered from {@link RegisterClientCommandsEvent} on the <strong>game</strong> bus, which
 * re-fires on every world join. Nothing reaches the server; feedback goes through
 * {@code ClientCommandSourceStack.sendSuccess}, overridden to a local system message.</p>
 *
 * <p><strong>No permission gate.</strong> {@code ClientCommandHandler} builds its source with
 * {@code player.getPermissionLevel()}, which is 0 on any normal server — so a
 * {@code requires(hasPermission(n))} would silently make the command not exist.</p>
 */
public final class MallCommand {

    private MallCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher()
                .register(Commands.literal("mallroom")
                        .then(Commands.literal("build")
                                .executes(ctx -> build(ctx, false))
                                .then(Commands.literal("both").executes(ctx -> build(ctx, true))))
                        .then(Commands.literal("preview")
                                .executes(ctx -> preview(ctx, false))
                                .then(Commands.literal("both").executes(ctx -> preview(ctx, true))))
                        .then(Commands.literal("status").executes(MallCommand::status))
                        .then(Commands.literal("stop").executes(MallCommand::stop)));
    }

    private static MallSpec specOf(boolean bothSides) {
        return new MallSpec(bothSides, Config.finishHallway(), Config.hallDepth());
    }

    private static int preview(CommandContext<CommandSourceStack> ctx, boolean bothSides) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return 0;
        }
        MallAnchor anchor = JobEngine.anchorFor(player, level);
        if (anchor == null) {
            feedback(
                    ctx,
                    ChatFormatting.RED,
                    "No wall within " + Config.maxWallScan()
                            + " blocks ahead. Stand in the hallway facing the wall you want opened.");
            return 0;
        }

        MallSpec spec = specOf(bothSides);
        MallLayout layout = new MallLayout(anchor, spec);
        MallCounts counts = layout.counts();
        String facing = anchor.facing().name().toLowerCase();

        feedback(
                ctx,
                ChatFormatting.AQUA,
                spec.roomCount() + " room(s) " + facing + ", opening " + anchor.openingDistance() + " block(s) ahead");
        feedback(ctx, ChatFormatting.GRAY, "  " + counts.minedTotal() + " blocks to mine");
        feedback(
                ctx, ChatFormatting.GRAY, "  " + counts.framingCount() + " framing blocks left standing (never mined)");
        feedback(
                ctx,
                ChatFormatting.GRAY,
                "  floor at y=" + anchor.playerFeet().y() + ", carve reaches y=" + anchor.floorPlateY() + " to y="
                        + anchor.ceilingPlateY());
        feedback(ctx, ChatFormatting.DARK_GRAY, "  nothing is placed — decorating the recesses is up to you");

        if (!player.onGround()) {
            feedback(ctx, ChatFormatting.YELLOW, "  You are not standing on the ground — the floor follows your feet.");
        }
        return 1;
    }

    private static int build(CommandContext<CommandSourceStack> ctx, boolean bothSides) {
        String error = JobEngine.INSTANCE.start(Minecraft.getInstance(), specOf(bothSides));
        if (error != null) {
            feedback(ctx, ChatFormatting.RED, error);
            return 0;
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        feedback(ctx, ChatFormatting.AQUA, "Status: " + JobEngine.INSTANCE.statusLine());
        if (JobEngine.INSTANCE.isRunning()) {
            feedback(ctx, ChatFormatting.GRAY, "  " + JobEngine.INSTANCE.progressLine());
        }
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        if (!JobEngine.INSTANCE.isRunning()) {
            feedback(ctx, ChatFormatting.GRAY, "Nothing is running.");
            return 0;
        }
        JobEngine.INSTANCE.abort(Minecraft.getInstance(), "you asked");
        return 1;
    }

    private static void feedback(CommandContext<CommandSourceStack> ctx, ChatFormatting colour, String message) {
        ctx.getSource().sendSuccess(() -> Component.literal(message).withStyle(colour), false);
    }
}
