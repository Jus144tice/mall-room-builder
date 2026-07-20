/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Asserts the config spec's shape and defaults, and that the null-safe getters agree with it.
 *
 * <p>Runs on the moddev {@code unitTest} harness, which puts the NeoForge runtime on the test
 * classpath so {@code ModConfigSpec} is a real type. No {@code .toml} is read — the getters are
 * being exercised in exactly the un-loaded state they are written to survive.</p>
 */
class ConfigTest {

    @Test
    void specBuilds() {
        assertNotNull(Config.SPEC);
    }

    @Test
    void valuePathsAreFlatAndNamedAsDocumented() {
        assertEquals(List.of("enabled"), Config.ENABLED.getPath());
        assertEquals(List.of("defaultRoomCount"), Config.DEFAULT_ROOM_COUNT.getPath());
        assertEquals(List.of("defaultHallLength"), Config.DEFAULT_HALL_LENGTH.getPath());
        assertEquals(List.of("buildBlock"), Config.BUILD_BLOCK.getPath());
        assertEquals(List.of("coverDoorThreshold"), Config.COVER_DOOR_THRESHOLD.getPath());
        assertEquals(List.of("autoWalkEnabled"), Config.AUTO_WALK_ENABLED.getPath());
        assertEquals(List.of("abortOnPlayerInput"), Config.ABORT_ON_PLAYER_INPUT.getPath());
        assertEquals(List.of("lookAbortDegrees"), Config.LOOK_ABORT_DEGREES.getPath());
        assertEquals(List.of("maxVerifySweeps"), Config.MAX_VERIFY_SWEEPS.getPath());
        assertEquals(List.of("autoSelectTool"), Config.AUTO_SELECT_TOOL.getPath());
    }

    @Test
    void specDefaultsMatchTheDocumentedValues() {
        assertEquals(true, Config.ENABLED.getDefault());
        assertEquals(1, Config.DEFAULT_ROOM_COUNT.getDefault());
        assertEquals(5, Config.DEFAULT_HALL_LENGTH.getDefault());
        assertEquals("minecraft:cobblestone", Config.BUILD_BLOCK.getDefault());
        assertEquals(true, Config.COVER_DOOR_THRESHOLD.getDefault());
        assertEquals(true, Config.AUTO_WALK_ENABLED.getDefault());
        assertEquals(1.0, Config.AUTO_WALK_SPEED.getDefault());
        assertEquals(true, Config.ABORT_ON_PLAYER_INPUT.getDefault());
        assertEquals(1.0, Config.LOOK_ABORT_DEGREES.getDefault());
        assertEquals(60, Config.ARM_GRACE_TICKS.getDefault());
        assertEquals(4, Config.PLACE_COOLDOWN_TICKS.getDefault());
        assertEquals(4, Config.MAX_VERIFY_SWEEPS.getDefault());
        assertEquals(20000, Config.MAX_QUEUED_BLOCKS.getDefault());
        assertEquals(6.0, Config.MIN_HEALTH.getDefault());
    }

    @Test
    void theSafetyDefaultsAreTheSafeOnes() {
        assertEquals(true, Config.ABORT_ON_PLAYER_INPUT.getDefault(), "the dead-man's switch ships on");
        assertEquals(true, Config.ABORT_ON_LIQUID.getDefault());
        assertEquals(true, Config.ABORT_ON_LOW_HEALTH.getDefault());
        assertEquals(true, Config.PAUSE_WHEN_OUT_OF_MATERIAL.getDefault(), "pause beats abort on restock");
        assertEquals(false, Config.AUTO_SELECT_TOOL.getDefault(), "the player's pickaxe choice is honoured");
        assertEquals(false, Config.DEBUG_LOGGING.getDefault());
    }

    @Test
    void gettersFallBackToDefaultsBeforeTheConfigLoads() {
        // Nothing has loaded a .toml here, so every getter must survive the IllegalStateException.
        assertTrue(Config.enabled());
        assertEquals(1, Config.defaultRoomCount());
        assertEquals(5, Config.defaultHallLength());
        assertEquals("minecraft:cobblestone", Config.buildBlock());
        assertTrue(Config.coverDoorThreshold());
        assertTrue(Config.autoWalkEnabled());
        assertEquals(1.0, Config.autoWalkSpeed());
        assertTrue(Config.autoWalkJump());
        assertEquals(10, Config.stuckTicks());
        assertTrue(Config.abortOnPlayerInput());
        assertEquals(1.0, Config.lookAbortDegrees());
        assertEquals(60, Config.armGraceTicks());
        assertEquals(200, Config.blockTimeoutTicks());
        assertEquals(40, Config.stepOffTimeoutTicks());
        assertEquals(4, Config.placeCooldownTicks());
        assertEquals(4, Config.maxVerifySweeps());
        assertEquals(20000, Config.maxQueuedBlocks());
        assertTrue(Config.autoSelectBuildSlot());
        assertTrue(!Config.autoSelectTool());
        assertTrue(Config.restoreHotbarSlotOnFinish());
        assertTrue(Config.pauseWhenOutOfMaterial());
        assertTrue(Config.abortOnLiquid());
        assertTrue(Config.abortOnLowHealth());
        assertEquals(6.0, Config.minHealth());
        assertTrue(Config.showHudStatus());
        assertTrue(!Config.debugLogging());
    }

    @Test
    void gettersMirrorTheSpecDefaultsExactly() {
        assertEquals(Config.ENABLED.getDefault(), Config.enabled());
        assertEquals(Config.DEFAULT_ROOM_COUNT.getDefault(), Config.defaultRoomCount());
        assertEquals(Config.DEFAULT_HALL_LENGTH.getDefault(), Config.defaultHallLength());
        assertEquals(Config.BUILD_BLOCK.getDefault(), Config.buildBlock());
        assertEquals(Config.LOOK_ABORT_DEGREES.getDefault(), Config.lookAbortDegrees());
        assertEquals(Config.MAX_QUEUED_BLOCKS.getDefault(), Config.maxQueuedBlocks());
        assertEquals(Config.MIN_HEALTH.getDefault(), Config.minHealth());
    }
}
