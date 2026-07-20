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
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * The {@code /mallroom} command tree.
 *
 * <p>Registered from {@link RegisterClientCommandsEvent} on the <strong>game</strong> bus, which
 * re-fires on every world join. Nothing here reaches the server; feedback goes through
 * {@code ClientCommandSourceStack.sendSuccess}, which is overridden to a local system message.</p>
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
                                .executes(ctx -> build(ctx, Config.defaultRoomCount(), Config.defaultHallLength()))
                                .then(Commands.argument("rooms", IntegerArgumentType.integer(1, 32))
                                        .executes(ctx -> build(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, "rooms"),
                                                Config.defaultHallLength()))
                                        .then(Commands.argument("hallLength", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> build(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(ctx, "rooms"),
                                                        IntegerArgumentType.getInteger(ctx, "hallLength"))))))
                        .then(Commands.literal("preview")
                                .executes(ctx -> preview(ctx, Config.defaultRoomCount(), Config.defaultHallLength()))
                                .then(Commands.argument("rooms", IntegerArgumentType.integer(1, 32))
                                        .executes(ctx -> preview(
                                                ctx,
                                                IntegerArgumentType.getInteger(ctx, "rooms"),
                                                Config.defaultHallLength()))
                                        .then(Commands.argument("hallLength", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> preview(
                                                        ctx,
                                                        IntegerArgumentType.getInteger(ctx, "rooms"),
                                                        IntegerArgumentType.getInteger(ctx, "hallLength"))))))
                        .then(Commands.literal("status").executes(MallCommand::status))
                        .then(Commands.literal("stop").executes(MallCommand::stop)));
    }

    private static MallSpec specOf(int rooms, int hallLength) {
        return new MallSpec(rooms, hallLength, Config.coverDoorThreshold());
    }

    private static int preview(CommandContext<CommandSourceStack> ctx, int rooms, int hallLength) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return 0;
        }
        MallSpec spec = specOf(rooms, hallLength);
        MallAnchor anchor = MallAnchor.of(
                Mth.floor(player.getX()), Mth.floor(player.getY()), Mth.floor(player.getZ()), player.getYRot());
        MallLayout layout = new MallLayout(anchor, spec);
        MallCounts counts = layout.counts();

        int extent = (rooms - 1) * spec.pitch() + 7;
        String facing = anchor.facing().name().toLowerCase();

        feedback(ctx, ChatFormatting.AQUA, rooms + " room(s), hallway " + hallLength + " long, running " + facing);
        feedback(
                ctx,
                ChatFormatting.GRAY,
                "  " + counts.minedTotal() + " blocks to mine (" + counts.airCount() + " air + " + counts.skinCount()
                        + " wall cavity)");
        feedback(
                ctx,
                ChatFormatting.GRAY,
                "  " + counts.skinCount() + " " + Config.buildBlock() + " to place (" + counts.stacksNeeded()
                        + " stacks)");
        feedback(
                ctx,
                ChatFormatting.GRAY,
                "  extends " + extent + " blocks " + facing + ", floor at y="
                        + anchor.playerFeet().y());

        if (!player.onGround()) {
            feedback(
                    ctx,
                    ChatFormatting.YELLOW,
                    "  You are not standing on the ground — the floor will be at your feet.");
        }
        return 1;
    }

    private static int build(CommandContext<CommandSourceStack> ctx, int rooms, int hallLength) {
        Minecraft mc = Minecraft.getInstance();
        String error = JobEngine.INSTANCE.start(mc, specOf(rooms, hallLength));
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
