/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Asserts the config spec's shape and defaults, and that the null-safe getters agree with it.
 *
 * <p>Runs on the moddev {@code unitTest} harness, so {@code ModConfigSpec} is a real type. No
 * {@code .toml} is read — the getters are exercised in exactly the un-loaded state they are written
 * to survive.</p>
 */
class ConfigTest {

    @Test
    void specBuilds() {
        assertNotNull(Config.SPEC);
    }

    @Test
    void valuePathsAreFlatAndNamedAsDocumented() {
        assertEquals(List.of("enabled"), Config.ENABLED.getPath());
        assertEquals(List.of("hallDepth"), Config.HALL_DEPTH.getPath());
        assertEquals(List.of("finishHallway"), Config.FINISH_HALLWAY.getPath());
        assertEquals(List.of("maxWallScan"), Config.MAX_WALL_SCAN.getPath());
        assertEquals(List.of("autoWalkEnabled"), Config.AUTO_WALK_ENABLED.getPath());
        assertEquals(List.of("abortOnPlayerInput"), Config.ABORT_ON_PLAYER_INPUT.getPath());
        assertEquals(List.of("autoBackfillFraming"), Config.AUTO_BACKFILL_FRAMING.getPath());
        assertEquals(List.of("backfillBlock"), Config.BACKFILL_BLOCK.getPath());
    }

    @Test
    void specDefaultsMatchTheDocumentedValues() {
        assertEquals(true, Config.ENABLED.getDefault());
        assertEquals(3, Config.HALL_DEPTH.getDefault());
        assertEquals(true, Config.FINISH_HALLWAY.getDefault());
        assertEquals(6, Config.MAX_WALL_SCAN.getDefault());
        assertEquals(1.0, Config.AUTO_WALK_SPEED.getDefault());
        assertEquals(1.0, Config.LOOK_ABORT_DEGREES.getDefault());
        assertEquals(60, Config.ARM_GRACE_TICKS.getDefault());
        assertEquals(4, Config.MAX_VERIFY_SWEEPS.getDefault());
        assertEquals(20000, Config.MAX_QUEUED_BLOCKS.getDefault());
        assertEquals("minecraft:cobblestone", Config.BACKFILL_BLOCK.getDefault());
        assertEquals(20, Config.FRAMING_SCAN_INTERVAL.getDefault());
        assertEquals(6.0, Config.MIN_HEALTH.getDefault());
    }

    @Test
    void theSafetyDefaultsAreTheSafeOnes() {
        assertEquals(true, Config.ABORT_ON_PLAYER_INPUT.getDefault(), "the dead-man's switch ships on");
        assertEquals(true, Config.ABORT_ON_LIQUID.getDefault());
        assertEquals(true, Config.ABORT_ON_LOW_HEALTH.getDefault());
        assertEquals(true, Config.AUTO_BACKFILL_FRAMING.getDefault(), "framing gets repaired if it goes missing");
        assertEquals(false, Config.AUTO_SELECT_TOOL.getDefault(), "the player's pickaxe choice is honoured");
        assertEquals(false, Config.DEBUG_LOGGING.getDefault());
    }

    @Test
    void gettersFallBackToDefaultsBeforeTheConfigLoads() {
        assertTrue(Config.enabled());
        assertEquals(3, Config.hallDepth());
        assertTrue(Config.finishHallway());
        assertEquals(6, Config.maxWallScan());
        assertTrue(Config.autoWalkEnabled());
        assertEquals(1.0, Config.autoWalkSpeed());
        assertTrue(Config.autoWalkJump());
        assertEquals(10, Config.stuckTicks());
        assertTrue(Config.abortOnPlayerInput());
        assertEquals(1.0, Config.lookAbortDegrees());
        assertEquals(60, Config.armGraceTicks());
        assertEquals(200, Config.blockTimeoutTicks());
        assertEquals(40, Config.stepOffTimeoutTicks());
        assertEquals(4, Config.maxVerifySweeps());
        assertEquals(20000, Config.maxQueuedBlocks());
        assertTrue(Config.autoBackfillFraming());
        assertEquals("minecraft:cobblestone", Config.backfillBlock());
        assertEquals(20, Config.framingScanInterval());
        assertEquals(4, Config.placeCooldownTicks());
        assertFalse(Config.autoSelectTool());
        assertTrue(Config.restoreHotbarSlotOnFinish());
        assertTrue(Config.abortOnLiquid());
        assertTrue(Config.abortOnLowHealth());
        assertEquals(6.0, Config.minHealth());
        assertTrue(Config.showHudStatus());
        assertFalse(Config.debugLogging());
    }

    @Test
    void gettersMirrorTheSpecDefaultsExactly() {
        assertEquals(Config.ENABLED.getDefault(), Config.enabled());
        assertEquals(Config.HALL_DEPTH.getDefault(), Config.hallDepth());
        assertEquals(Config.MAX_WALL_SCAN.getDefault(), Config.maxWallScan());
        assertEquals(Config.BACKFILL_BLOCK.getDefault(), Config.backfillBlock());
        assertEquals(Config.LOOK_ABORT_DEGREES.getDefault(), Config.lookAbortDegrees());
        assertEquals(Config.MIN_HEALTH.getDefault(), Config.minHealth());
    }
}
