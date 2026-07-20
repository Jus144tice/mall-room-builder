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
import com.jus144tice.mallroombuilder.core.SpineGeometry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * The {@code /mallroom} command tree.
 *
 * <pre>
 *   /mallroom room [both]              carve a room off the spine
 *   /mallroom spine [length]           carve the next segment of spine
 *   /mallroom preview room [both]      counts only, no side effects
 *   /mallroom preview spine [length]
 *   /mallroom status
 *   /mallroom stop
 * </pre>
 *
 * <p>Both jobs share one rule: stand facing the way you want to build, and <strong>the block
 * directly in front of you is the first block of the job</strong>. For a room you are laterally
 * centred on it; for a spine segment you are on the centre lane.</p>
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
                        .then(Commands.literal("room")
                                .executes(ctx -> run(ctx, roomSpec(false), false))
                                .then(Commands.literal("both").executes(ctx -> run(ctx, roomSpec(true), false))))
                        .then(Commands.literal("spine")
                                .executes(ctx -> run(ctx, spineSpec(Config.spineLength()), false))
                                .then(Commands.argument("length", IntegerArgumentType.integer(1, 64))
                                        .executes(ctx -> run(
                                                ctx, spineSpec(IntegerArgumentType.getInteger(ctx, "length")), false))))
                        .then(Commands.literal("preview")
                                .then(Commands.literal("room")
                                        .executes(ctx -> run(ctx, roomSpec(false), true))
                                        .then(Commands.literal("both").executes(ctx -> run(ctx, roomSpec(true), true))))
                                .then(Commands.literal("spine")
                                        .executes(ctx -> run(ctx, spineSpec(Config.spineLength()), true))
                                        .then(Commands.argument("length", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> run(
                                                        ctx,
                                                        spineSpec(IntegerArgumentType.getInteger(ctx, "length")),
                                                        true)))))
                        .then(Commands.literal("status").executes(MallCommand::status))
                        .then(Commands.literal("stop").executes(MallCommand::stop)));
    }

    private static MallSpec roomSpec(boolean bothSides) {
        return MallSpec.room(bothSides, Config.hallDepth());
    }

    private static MallSpec spineSpec(int length) {
        return MallSpec.spine(length, Config.hallDepth());
    }

    private static int run(CommandContext<CommandSourceStack> ctx, MallSpec spec, boolean previewOnly) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return 0;
        }
        if (previewOnly) {
            return preview(ctx, player, spec);
        }
        String error = JobEngine.INSTANCE.start(mc, spec);
        if (error != null) {
            feedback(ctx, ChatFormatting.RED, error);
            return 0;
        }
        return 1;
    }

    private static int preview(CommandContext<CommandSourceStack> ctx, LocalPlayer player, MallSpec spec) {
        MallAnchor anchor = JobEngine.anchorFor(player);
        MallLayout layout = new MallLayout(anchor, spec);
        MallCounts counts = layout.counts();
        String facing = anchor.facing().name().toLowerCase();

        if (spec.kind() == MallSpec.Kind.SPINE) {
            feedback(
                    ctx,
                    ChatFormatting.AQUA,
                    "Spine segment " + facing + ": " + spec.spineLength() + " long, " + (SpineGeometry.RADIUS * 2 + 1)
                            + " wide, " + SpineGeometry.HEIGHT + " tall");
            feedback(ctx, ChatFormatting.GRAY, "  " + counts.minedTotal() + " blocks to mine");
            feedback(
                    ctx,
                    ChatFormatting.GRAY,
                    "  starts at the block in front of you, y="
                            + anchor.playerFeet().y() + " to y="
                            + (anchor.playerFeet().y() + SpineGeometry.HEIGHT - 1));
        } else {
            feedback(ctx, ChatFormatting.AQUA, spec.roomCount() + " room(s) " + facing);
            feedback(ctx, ChatFormatting.GRAY, "  " + counts.minedTotal() + " blocks to mine");
            feedback(
                    ctx,
                    ChatFormatting.GRAY,
                    "  " + counts.framingCount() + " framing blocks left standing (never mined)");
            feedback(
                    ctx,
                    ChatFormatting.GRAY,
                    "  opening at the block in front of you, carve reaches y=" + anchor.floorPlateY() + " to y="
                            + anchor.ceilingPlateY());
        }
        feedback(ctx, ChatFormatting.DARK_GRAY, "  nothing is placed — decorating is up to you");

        if (!player.onGround()) {
            feedback(ctx, ChatFormatting.YELLOW, "  You are not standing on the ground — the floor follows your feet.");
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
