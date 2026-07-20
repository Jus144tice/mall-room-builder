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
import com.jus144tice.mallroombuilder.core.RoomGeometry;
import com.jus144tice.mallroombuilder.core.SpineGeometry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
 *   /mallroom room  [both]     [rough|finish]
 *   /mallroom spine [length]   [rough|finish]
 *   /mallroom preview room  [both]   [rough|finish]
 *   /mallroom preview spine [length] [rough|finish]
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

    /** Builds a spec once the rough/finish choice is known. */
    @FunctionalInterface
    private interface SpecFactory {
        MallSpec create(CommandContext<CommandSourceStack> ctx, boolean finishRecesses);
    }

    private MallCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher()
                .register(Commands.literal("mallroom")
                        .then(roomBranch(false))
                        .then(spineBranch(false))
                        .then(Commands.literal("preview").then(roomBranch(true)).then(spineBranch(true)))
                        .then(Commands.literal("status").executes(MallCommand::status))
                        .then(Commands.literal("stop").executes(MallCommand::stop)));
    }

    /** {@code room}, {@code room both}, each with the rough/finish tail. */
    private static LiteralArgumentBuilder<CommandSourceStack> roomBranch(boolean preview) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("room");
        modes(node, (ctx, finish) -> MallSpec.room(false, Config.hallDepth(), finish), preview);
        node.then(modes(
                Commands.literal("both"), (ctx, finish) -> MallSpec.room(true, Config.hallDepth(), finish), preview));
        return node;
    }

    /** {@code spine}, {@code spine <length>}, each with the rough/finish tail. */
    private static LiteralArgumentBuilder<CommandSourceStack> spineBranch(boolean preview) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("spine");
        modes(node, (ctx, finish) -> MallSpec.spine(Config.spineLength(), Config.hallDepth(), finish), preview);
        node.then(modes(
                Commands.argument("length", IntegerArgumentType.integer(1, 64)),
                (ctx, finish) ->
                        MallSpec.spine(IntegerArgumentType.getInteger(ctx, "length"), Config.hallDepth(), finish),
                preview));
        return node;
    }

    /**
     * Hangs the default, {@code rough} and {@code finish} executions off a node.
     *
     * <p>The bare form follows {@code carveFinishRecesses}; the two literals override it either way,
     * so the config choice is a default rather than a lock.</p>
     */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T modes(
            T node, SpecFactory factory, boolean preview) {
        node.executes(ctx -> run(ctx, factory.create(ctx, Config.carveFinishRecesses()), preview));
        node.then(Commands.literal("rough").executes(ctx -> run(ctx, factory.create(ctx, false), preview)));
        node.then(Commands.literal("finish").executes(ctx -> run(ctx, factory.create(ctx, true), preview)));
        return node;
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
            int height = spec.finishRecesses() ? SpineGeometry.ENVELOPE_HEIGHT : SpineGeometry.INTERIOR_HEIGHT;
            feedback(
                    ctx,
                    ChatFormatting.AQUA,
                    "Spine segment " + facing + " (" + spec.modeName() + "): " + spec.spineLength() + " long, "
                            + SpineGeometry.WIDTH + " wide, " + height + " tall");
        } else {
            feedback(ctx, ChatFormatting.AQUA, spec.roomCount() + " room(s) " + facing + " (" + spec.modeName() + ")");
        }

        feedback(ctx, ChatFormatting.GRAY, "  " + counts.minedTotal() + " blocks to mine");
        if (counts.framingCount() > 0) {
            feedback(
                    ctx,
                    ChatFormatting.GRAY,
                    "  " + counts.framingCount() + " framing blocks left standing (never mined)");
        }

        if (spec.finishRecesses()) {
            feedback(
                    ctx,
                    ChatFormatting.GRAY,
                    "  reaches y=" + anchor.floorPlateY() + " to y=" + anchor.ceilingPlateY()
                            + "; you will end 1 block lower");
            feedback(ctx, ChatFormatting.DARK_GRAY, "  nothing is placed — decorating is up to you");
        } else {
            int top = anchor.playerFeet().y() + RoomGeometry.INTERIOR_SIZE - 1;
            feedback(
                    ctx,
                    ChatFormatting.GRAY,
                    "  reaches y=" + anchor.playerFeet().y() + " to y=" + top + "; you stay at this level");
            feedback(
                    ctx,
                    ChatFormatting.DARK_GRAY,
                    "  rough only — re-run from this same block later to cut the recesses");
        }

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
