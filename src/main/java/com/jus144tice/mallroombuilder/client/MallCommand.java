/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.client;

import com.jus144tice.mallroombuilder.Config;
import com.jus144tice.mallroombuilder.core.FillPlan;
import com.jus144tice.mallroombuilder.core.MallAnchor;
import com.jus144tice.mallroombuilder.core.MallCounts;
import com.jus144tice.mallroombuilder.core.MallLayout;
import com.jus144tice.mallroombuilder.core.MallSpec;
import com.jus144tice.mallroombuilder.core.RoomGeometry;
import com.jus144tice.mallroombuilder.core.SpineGeometry;
import com.jus144tice.mallroombuilder.core.Surface;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * The {@code /mallroom} command tree.
 *
 * <pre>
 *   /mallroom room  [both]     [rough|finish] [&lt;surface&gt; &lt;slot&gt;]...
 *   /mallroom spine [length]   [rough|finish] [&lt;surface&gt; &lt;slot&gt;]...
 *   /mallroom fill  room [both]   &lt;surface&gt; &lt;slot&gt;...      fill without carving
 *   /mallroom fill  spine [length] &lt;surface&gt; &lt;slot&gt;...
 *   /mallroom preview ...        same shapes, counts only
 *   /mallroom status
 *   /mallroom stop
 * </pre>
 *
 * <p>Surfaces are {@code floor}, {@code walls}, {@code ceiling} and {@code beam} for a room, and
 * {@code floor} and {@code ceiling} for a spine segment. Slots are hotbar positions <strong>1-9</strong>
 * as you see them. Naming any surface turns on the fill phase; naming none is carve-only.</p>
 *
 * <p>Every job shares one anchoring rule: stand facing the way you want to build, and <strong>the
 * block directly in front of you is the first block of the job</strong>.</p>
 *
 * <p>Registered from {@link RegisterClientCommandsEvent} on the <strong>game</strong> bus, which
 * re-fires on every world join. Nothing reaches the server.</p>
 *
 * <p><strong>No permission gate.</strong> {@code ClientCommandHandler} builds its source with
 * {@code player.getPermissionLevel()}, which is 0 on any normal server — so a
 * {@code requires(hasPermission(n))} would silently make the command not exist.</p>
 */
public final class MallCommand {

    /** Builds the carve half of a spec; the fill half is read from the surface arguments. */
    @FunctionalInterface
    private interface SpecFactory {
        MallSpec create(CommandContext<CommandSourceStack> ctx, boolean finishRecesses);
    }

    private MallCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    /**
     * Builds the whole tree. Split out from the event handler so it can be exercised in a unit test —
     * the surface arguments are assembled recursively, and "does it terminate and produce the paths I
     * expect" is worth asserting rather than discovering in game.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mallroom")
                .then(roomBranch(false, true))
                .then(spineBranch(false, true))
                .then(Commands.literal("fill").then(roomBranch(false, false)).then(spineBranch(false, false)))
                .then(Commands.literal("preview")
                        .then(roomBranch(true, true))
                        .then(spineBranch(true, true))
                        .then(Commands.literal("fill")
                                .then(roomBranch(true, false))
                                .then(spineBranch(true, false))))
                .then(Commands.literal("status").executes(MallCommand::status))
                .then(Commands.literal("stop").executes(MallCommand::stop)));
    }

    /**
     * @param preview report counts instead of starting a job
     * @param carve   false for the fill-only branch, which skips the rough/finish modes entirely
     */
    private static LiteralArgumentBuilder<CommandSourceStack> roomBranch(boolean preview, boolean carve) {
        SpecFactory one = (ctx, finish) -> base(MallSpec.room(false, Config.hallDepth(), finish), carve);
        SpecFactory pair = (ctx, finish) -> base(MallSpec.room(true, Config.hallDepth(), finish), carve);

        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("room");
        modes(node, one, preview, MallSpec.Kind.ROOM, carve);
        node.then(modes(Commands.literal("both"), pair, preview, MallSpec.Kind.ROOM, carve));
        return node;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> spineBranch(boolean preview, boolean carve) {
        SpecFactory fromConfig =
                (ctx, finish) -> base(MallSpec.spine(Config.spineLength(), Config.hallDepth(), finish), carve);
        SpecFactory fromArg = (ctx, finish) ->
                base(MallSpec.spine(IntegerArgumentType.getInteger(ctx, "length"), Config.hallDepth(), finish), carve);

        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal("spine");
        modes(node, fromConfig, preview, MallSpec.Kind.SPINE, carve);
        node.then(modes(
                Commands.argument("length", IntegerArgumentType.integer(1, 64)),
                fromArg,
                preview,
                MallSpec.Kind.SPINE,
                carve));
        return node;
    }

    /**
     * A fill-only spec has nothing to carve, so it is flagged as such and forced to the finished
     * geometry — there is nothing to fill unless the recesses already exist.
     */
    private static MallSpec base(MallSpec spec, boolean carve) {
        return carve ? spec : spec.fillOnly(FillPlan.none());
    }

    /**
     * Hangs the rough/finish modes and the surface arguments off a node.
     *
     * <p>The fill-only branch skips the modes: {@code /mallroom fill room rough} would be a
     * contradiction, since rough means "no recesses" and there would be nothing to fill.</p>
     */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T modes(
            T node, SpecFactory factory, boolean preview, MallSpec.Kind kind, boolean carve) {
        surfaces(node, ctx -> factory.create(ctx, Config.carveFinishRecesses()), preview, kind);
        if (carve) {
            node.then(surfaces(Commands.literal("rough"), ctx -> factory.create(ctx, false), preview, kind));
            node.then(surfaces(Commands.literal("finish"), ctx -> factory.create(ctx, true), preview, kind));
        }
        return node;
    }

    /** Makes a node executable on its own, then hangs the {@code <surface> <slot>} pairs off it. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T surfaces(
            T node, Function<CommandContext<CommandSourceStack>, MallSpec> base, boolean preview, MallSpec.Kind kind) {
        node.executes(ctx -> run(ctx, withFill(ctx, base.apply(ctx), kind), preview));
        addSurfaceArgs(node, Surface.forKind(kind), base, preview, kind);
        return node;
    }

    /**
     * Builds {@code <surface> <slot>} pairs recursively over the surfaces not yet named, so every
     * subset <em>and every ordering</em> parses and every node along the way executes:
     * {@code floor 3} and {@code beam 6 ceiling 4 floor 3} are both complete commands.
     *
     * <p>Any-order costs a few hundred nodes versus a fixed order's handful. That is nothing here —
     * the tree is built once per world join, lives on the client, and is never sent anywhere.</p>
     */
    private static void addSurfaceArgs(
            ArgumentBuilder<CommandSourceStack, ?> node,
            List<Surface> remaining,
            Function<CommandContext<CommandSourceStack>, MallSpec> base,
            boolean preview,
            MallSpec.Kind kind) {
        for (Surface surface : remaining) {
            List<Surface> rest = remaining.stream().filter(s -> s != surface).toList();
            var slot = Commands.argument(surface.key(), IntegerArgumentType.integer(1, 9));
            slot.executes(ctx -> run(ctx, withFill(ctx, base.apply(ctx), kind), preview));
            addSurfaceArgs(slot, rest, base, preview, kind);
            node.then(Commands.literal(surface.key()).then(slot));
        }
    }

    /** Reads whichever surface arguments were actually supplied and attaches them to the spec. */
    private static MallSpec withFill(CommandContext<CommandSourceStack> ctx, MallSpec spec, MallSpec.Kind kind) {
        FillPlan.Builder plan = FillPlan.builder();
        for (Surface surface : Surface.forKind(kind)) {
            plan.with(surface, optionalSlot(ctx, surface.key()));
        }
        FillPlan built = plan.build();
        return built.isEmpty() ? spec : spec.withFill(built);
    }

    /** Brigadier throws rather than reporting absence, so the lookup has to be guarded. */
    private static Integer optionalSlot(CommandContext<CommandSourceStack> ctx, String name) {
        try {
            return IntegerArgumentType.getInteger(ctx, name);
        } catch (IllegalArgumentException absent) {
            return null;
        }
    }

    private static int run(CommandContext<CommandSourceStack> ctx, MallSpec spec, boolean previewOnly) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return 0;
        }
        if (!spec.carve() && !spec.fills()) {
            feedback(ctx, ChatFormatting.RED, "Name at least one surface and slot, e.g. /mallroom fill spine floor 3.");
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

        if (spec.carve()) {
            feedback(ctx, ChatFormatting.GRAY, "  " + counts.minedTotal() + " blocks to mine");
        }
        if (counts.framingCount() > 0) {
            feedback(
                    ctx,
                    ChatFormatting.GRAY,
                    "  " + counts.framingCount() + " framing blocks left standing (never mined)");
        }

        if (spec.fills()) {
            feedback(ctx, ChatFormatting.GRAY, "  " + counts.placedTotal() + " blocks to place:");
            for (Surface surface : spec.fill().surfaces()) {
                int slot = spec.fill().slot(surface);
                ItemStack stack = player.getInventory().getItem(slot - 1);
                String held = stack.isEmpty() ? "EMPTY" : stack.getHoverName().getString() + " x" + stack.getCount();
                ChatFormatting colour = stack.isEmpty() ? ChatFormatting.RED : ChatFormatting.GRAY;
                feedback(
                        ctx,
                        colour,
                        "    " + surface.key() + ": " + layout.surfaceCount(surface) + " from slot " + slot + " ("
                                + held + ")");
            }
        } else {
            feedback(ctx, ChatFormatting.DARK_GRAY, "  nothing is placed — name a surface and slot to fill");
        }

        if (spec.carve() && spec.finishRecesses()) {
            feedback(
                    ctx,
                    ChatFormatting.GRAY,
                    "  reaches y=" + anchor.floorPlateY() + " to y=" + anchor.ceilingPlateY()
                            + (spec.fill().covers(Surface.FLOOR)
                                    ? "; the floor fill puts you back at this level"
                                    : "; you will end 1 block lower"));
        } else if (spec.carve()) {
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
