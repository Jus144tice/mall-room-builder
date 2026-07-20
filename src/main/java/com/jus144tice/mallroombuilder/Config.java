/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config, stored in {@code config/mallroombuilder-client.toml}.
 *
 * <p>Every getter is null-safe: queried before the config has loaded, it returns the documented
 * default rather than throwing.</p>
 */
public final class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // --- What to carve ------------------------------------------------------

    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER.comment(
                    "Master switch. When false, /mallroom build refuses to start.")
            .define("enabled", true);

    public static final ModConfigSpec.IntValue HALL_DEPTH = BUILDER.comment(
                    "Width of the spine hallway, measured across the corridor. Normally 3.",
                    "Also sets how far apart two facing rooms sit: hallDepth + 1.")
            .defineInRange("hallDepth", 3, 1, 15);

    public static final ModConfigSpec.IntValue SPINE_LENGTH = BUILDER.comment(
                    "Blocks along the run carved by /mallroom spine with no argument.")
            .defineInRange("spineLength", 7, 1, 64);

    public static final ModConfigSpec.BooleanValue CARVE_FINISH_RECESSES = BUILDER.comment(
                    "Whether jobs cut the 1-block finishing recesses by default, on top of the",
                    "finished volume. Override per command with the 'rough' and 'finish' arguments.",
                    "Rough is useful when you do not have the decorative blocks on you: rough the run",
                    "out now, then re-run the same jobs from the same spots later and only the",
                    "recesses get cut.")
            .define("carveFinishRecesses", true);

    // --- Movement -----------------------------------------------------------

    public static final ModConfigSpec.BooleanValue AUTO_WALK_ENABLED = BUILDER.comment(
                    "Steer player movement toward the next block when nothing is in reach.",
                    "With this off the job only mines what you can already touch, then stops.")
            .define("autoWalkEnabled", true);

    public static final ModConfigSpec.DoubleValue AUTO_WALK_SPEED = BUILDER.comment(
                    "Scale applied to the movement impulse, 0.1 (crawl) to 1.0 (normal walk).")
            .defineInRange("autoWalkSpeed", 1.0, 0.1, 1.0);

    public static final ModConfigSpec.BooleanValue AUTO_WALK_JUMP = BUILDER.comment(
                    "Allow the driver to jump for 1-block step-ups and to unstick itself.")
            .define("autoWalkJump", true);

    public static final ModConfigSpec.IntValue STUCK_TICKS = BUILDER.comment(
                    "Ticks of near-zero movement while steering before a jump is attempted.")
            .defineInRange("stuckTicks", 10, 2, 60);

    // --- The dead-man's switch ----------------------------------------------

    public static final ModConfigSpec.BooleanValue ABORT_ON_PLAYER_INPUT = BUILDER.comment(
                    "Abort the job the instant you touch a key, a mouse button, or move the mouse.",
                    "Turning this off is unsupported: the mod would keep mining and walking while you",
                    "are trying to do something else.")
            .define("abortOnPlayerInput", true);

    public static final ModConfigSpec.DoubleValue LOOK_ABORT_DEGREES = BUILDER.comment(
                    "Accumulated mouse-look movement, in degrees, that counts as 'you took over'.",
                    "The mod never rotates the camera itself, so any rotation at all is you.")
            .defineInRange("lookAbortDegrees", 1.0, 0.1, 45.0);

    public static final ModConfigSpec.IntValue ARM_GRACE_TICKS = BUILDER.comment(
                    "How long to wait for you to let go of everything before starting.",
                    "You pressed Enter to send the command, so something is usually still held.")
            .defineInRange("armGraceTicks", 60, 0, 200);

    // --- Job limits and recovery --------------------------------------------

    public static final ModConfigSpec.IntValue BLOCK_TIMEOUT_TICKS = BUILDER.comment(
                    "Give up on a single block after this many ticks and defer it.",
                    "Catches unbreakable blocks and 'no suitable tool' situations.")
            .defineInRange("blockTimeoutTicks", 200, 20, 1200);

    public static final ModConfigSpec.IntValue STEP_OFF_TIMEOUT_TICKS = BUILDER.comment(
                    "How long to wait for you to step off a floor block before mining it anyway.",
                    "Only matters for the last floor cells, where there is nowhere left to walk.",
                    "You drop exactly one block onto untouched stone -- no damage, no suffocation.")
            .defineInRange("stepOffTimeoutTicks", 40, 0, 200);

    public static final ModConfigSpec.IntValue MAX_VERIFY_SWEEPS = BUILDER.comment(
                    "How many times to re-scan for unfinished work before giving up.",
                    "Sweeps are what recover deferred blocks, fallen gravel and server rejections.")
            .defineInRange("maxVerifySweeps", 4, 0, 16);

    public static final ModConfigSpec.IntValue MAX_QUEUED_BLOCKS = BUILDER.comment(
                    "Refuse a job larger than this. A safety net against a bad hallDepth.")
            .defineInRange("maxQueuedBlocks", 20000, 100, 200000);

    // --- Framing backfill ---------------------------------------------------

    public static final ModConfigSpec.BooleanValue AUTO_BACKFILL_FRAMING = BUILDER.comment(
                    "Watch the framing during a job and replace any cell that goes missing.",
                    "The mod never mines framing, but gravel falls and mobs happen. Backfill runs",
                    "between blocks, never mid-break -- switching hotbar slots mid-break would reset",
                    "destroy progress to zero.")
            .define("autoBackfillFraming", true);

    public static final ModConfigSpec.ConfigValue<String> BACKFILL_BLOCK = BUILDER.comment(
                    "Item used to backfill missing framing. Must be a block item you are carrying.")
            .define("backfillBlock", "minecraft:cobblestone");

    public static final ModConfigSpec.IntValue FRAMING_SCAN_INTERVAL = BUILDER.comment(
                    "Ticks between framing integrity scans. Cheap -- a few dozen block reads.")
            .defineInRange("framingScanInterval", 20, 1, 200);

    public static final ModConfigSpec.IntValue PLACE_COOLDOWN_TICKS = BUILDER.comment(
                    "Ticks between backfill placements. 4 matches vanilla's held-use cadence.")
            .defineInRange("placeCooldownTicks", 4, 0, 20);

    // --- Inventory ----------------------------------------------------------

    public static final ModConfigSpec.BooleanValue ABORT_ON_WRONG_TOOL = BUILDER.comment(
                    "Stop the job if a queued block would not drop with the tool you are holding --",
                    "obsidian with a stone pickaxe, say. The point of the mod is that you keep the",
                    "material, so mining it anyway would be a silent loss. Blocks that need no tool",
                    "at all (dirt, gravel) never trigger this.")
            .define("abortOnWrongTool", true);

    public static final ModConfigSpec.IntValue TOOL_GRACE_TICKS = BUILDER.comment(
                    "How long to wait, holding the wrong tool, before giving up. This is the window a",
                    "tool-replacement mod has to swap a fresh pickaxe in after one breaks; the job",
                    "pauses rather than mining, so nothing is lost while it waits. 20 ticks = 1s.")
            .defineInRange("toolGraceTicks", 40, 0, 400);

    public static final ModConfigSpec.BooleanValue ALLOW_TOOL_SWAP = BUILDER.comment(
                    "Treat a hotbar slot change as a tool swap rather than as you taking over, so long",
                    "as the newly selected item can still harvest the block being worked on.",
                    "Leave true if you run a mod that auto-replaces a broken pickaxe -- otherwise the",
                    "dead-man's switch fires the moment it swaps. Switching to something that cannot",
                    "mine the target still aborts either way.")
            .define("allowToolSwap", true);

    public static final ModConfigSpec.BooleanValue AUTO_SELECT_TOOL = BUILDER.comment(
                    "Switch to the fastest pickaxe in your hotbar before carving.",
                    "Off by default: you chose which pickaxe to hold, and the mod should honour that.")
            .define("autoSelectTool", false);

    public static final ModConfigSpec.BooleanValue RESTORE_HOTBAR_SLOT_ON_FINISH = BUILDER.comment(
                    "Put the selected hotbar slot back where it was when the job ends.")
            .define("restoreHotbarSlotOnFinish", true);

    // --- Safety -------------------------------------------------------------

    public static final ModConfigSpec.BooleanValue ABORT_ON_LIQUID = BUILDER.comment(
                    "Refuse to mine a block touching lava or water, and abort if one appears.",
                    "Stops a job from flooding or draining lava into the mall.")
            .define("abortOnLiquid", true);

    public static final ModConfigSpec.BooleanValue ABORT_ON_LOW_HEALTH =
            BUILDER.comment("Abort if health drops below minHealth.").define("abortOnLowHealth", true);

    public static final ModConfigSpec.DoubleValue MIN_HEALTH = BUILDER.comment(
                    "Health floor, in half-hearts (20 = full). Below this the job aborts.")
            .defineInRange("minHealth", 6.0, 0.0, 20.0);

    // --- Feedback -----------------------------------------------------------

    public static final ModConfigSpec.BooleanValue SHOW_HUD_STATUS = BUILDER.comment(
                    "Draw a progress overlay while a job is running. A room takes minutes.")
            .define("showHudStatus", true);

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER.comment(
                    "Log engine decisions to the client log. For troubleshooting only.")
            .define("debugLogging", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}

    private static boolean safeGet(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }

    private static int safeGet(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }

    private static double safeGet(ModConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }

    public static boolean enabled() {
        return safeGet(ENABLED, true);
    }

    public static int hallDepth() {
        return safeGet(HALL_DEPTH, 3);
    }

    public static int spineLength() {
        return safeGet(SPINE_LENGTH, 7);
    }

    public static boolean carveFinishRecesses() {
        return safeGet(CARVE_FINISH_RECESSES, true);
    }

    public static boolean autoWalkEnabled() {
        return safeGet(AUTO_WALK_ENABLED, true);
    }

    public static double autoWalkSpeed() {
        return safeGet(AUTO_WALK_SPEED, 1.0);
    }

    public static boolean autoWalkJump() {
        return safeGet(AUTO_WALK_JUMP, true);
    }

    public static int stuckTicks() {
        return safeGet(STUCK_TICKS, 10);
    }

    public static boolean abortOnPlayerInput() {
        return safeGet(ABORT_ON_PLAYER_INPUT, true);
    }

    public static double lookAbortDegrees() {
        return safeGet(LOOK_ABORT_DEGREES, 1.0);
    }

    public static int armGraceTicks() {
        return safeGet(ARM_GRACE_TICKS, 60);
    }

    public static int blockTimeoutTicks() {
        return safeGet(BLOCK_TIMEOUT_TICKS, 200);
    }

    public static int stepOffTimeoutTicks() {
        return safeGet(STEP_OFF_TIMEOUT_TICKS, 40);
    }

    public static int maxVerifySweeps() {
        return safeGet(MAX_VERIFY_SWEEPS, 4);
    }

    public static int maxQueuedBlocks() {
        return safeGet(MAX_QUEUED_BLOCKS, 20000);
    }

    public static boolean autoBackfillFraming() {
        return safeGet(AUTO_BACKFILL_FRAMING, true);
    }

    public static String backfillBlock() {
        try {
            return BACKFILL_BLOCK.get();
        } catch (IllegalStateException notLoadedYet) {
            return "minecraft:cobblestone";
        }
    }

    public static int framingScanInterval() {
        return safeGet(FRAMING_SCAN_INTERVAL, 20);
    }

    public static int placeCooldownTicks() {
        return safeGet(PLACE_COOLDOWN_TICKS, 4);
    }

    public static boolean abortOnWrongTool() {
        return safeGet(ABORT_ON_WRONG_TOOL, true);
    }

    public static int toolGraceTicks() {
        return safeGet(TOOL_GRACE_TICKS, 40);
    }

    public static boolean allowToolSwap() {
        return safeGet(ALLOW_TOOL_SWAP, true);
    }

    public static boolean autoSelectTool() {
        return safeGet(AUTO_SELECT_TOOL, false);
    }

    public static boolean restoreHotbarSlotOnFinish() {
        return safeGet(RESTORE_HOTBAR_SLOT_ON_FINISH, true);
    }

    public static boolean abortOnLiquid() {
        return safeGet(ABORT_ON_LIQUID, true);
    }

    public static boolean abortOnLowHealth() {
        return safeGet(ABORT_ON_LOW_HEALTH, true);
    }

    public static double minHealth() {
        return safeGet(MIN_HEALTH, 6.0);
    }

    public static boolean showHudStatus() {
        return safeGet(SHOW_HUD_STATUS, true);
    }

    public static boolean debugLogging() {
        return safeGet(DEBUG_LOGGING, false);
    }
}
