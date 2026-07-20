/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.client;

import com.jus144tice.mallroombuilder.Config;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The dead-man's switch. Any sign of a human at the controls aborts the job.
 *
 * <p>This is trustworthy for one specific reason: the engine drives movement by writing
 * {@code Input}'s impulse fields directly and <strong>never</strong> touches {@code KeyMapping}
 * state. So {@code options.key*.isDown()} remains a clean read of what the player is physically
 * doing — we are never reading back our own writes.</p>
 *
 * <p>Mouse-look is the strongest signal of the lot. Because the server validates neither breaking
 * nor placing against look direction, the mod never rotates the camera; therefore <em>any</em>
 * rotation is unambiguously the player, and the threshold can sit as low as a single degree with no
 * false positives. It is measured as an accumulated delta from the baseline latched when the job
 * armed, not per tick, so a slow deliberate drag trips it just as surely as a flick.</p>
 */
public final class InputWatch {

    private static float baseYaw;
    private static float basePitch;
    private static int expectedSlot = -1;

    private InputWatch() {}

    /** Every key the watch monitors. Screen state and mouse-look are checked separately. */
    private static KeyMapping[] watched(Minecraft mc) {
        return new KeyMapping[] {
            mc.options.keyUp,
            mc.options.keyDown,
            mc.options.keyLeft,
            mc.options.keyRight,
            mc.options.keyJump,
            mc.options.keyShift,
            mc.options.keySprint,
            mc.options.keyAttack,
            mc.options.keyUse,
            mc.options.keyPickItem,
            mc.options.keyDrop,
            mc.options.keyInventory
        };
    }

    /**
     * True when nothing is held and no screen is open.
     *
     * <p>The job waits for this before starting. It is not paranoia: the player just pressed Enter
     * to send the command, so the chat screen is closing and keys are often still down. Without the
     * wait the dead-man's switch would fire on tick one, every single time.</p>
     */
    public static boolean allReleased(Minecraft mc) {
        if (mc.screen != null) {
            return false;
        }
        for (KeyMapping key : watched(mc)) {
            if (key.isDown()) {
                return false;
            }
        }
        return true;
    }

    /** Latches the look baseline and the expected hotbar slot. Call once, when the job starts. */
    public static void arm(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != null) {
            baseYaw = player.getYRot();
            basePitch = player.getXRot();
            expectedSlot = player.getInventory().selected;
        }
    }

    /** Tells the watch that the engine deliberately changed the hotbar slot. */
    public static void setExpectedSlot(int slot) {
        expectedSlot = slot;
    }

    public static int expectedSlot() {
        return expectedSlot;
    }

    /**
     * @return a human-readable reason the job should abort, or null to keep going
     */
    public static String tripped(Minecraft mc) {
        if (!Config.abortOnPlayerInput()) {
            return null;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return "no player";
        }
        if (mc.screen != null) {
            return "a screen was opened";
        }

        String key = trippedKey(mc);
        if (key != null) {
            return key;
        }

        double look = Math.abs(angleDelta(player.getYRot(), baseYaw)) + Math.abs(player.getXRot() - basePitch);
        if (look > Config.lookAbortDegrees()) {
            return "mouse look";
        }

        if (expectedSlot >= 0 && player.getInventory().selected != expectedSlot) {
            return "hotbar slot changed";
        }
        return null;
    }

    private static String trippedKey(Minecraft mc) {
        if (mc.options.keyUp.isDown()) {
            return "forward key";
        }
        if (mc.options.keyDown.isDown()) {
            return "back key";
        }
        if (mc.options.keyLeft.isDown()) {
            return "left key";
        }
        if (mc.options.keyRight.isDown()) {
            return "right key";
        }
        if (mc.options.keyJump.isDown()) {
            return "jump key";
        }
        if (mc.options.keyShift.isDown()) {
            return "sneak key";
        }
        if (mc.options.keySprint.isDown()) {
            return "sprint key";
        }
        if (mc.options.keyAttack.isDown()) {
            return "attack button";
        }
        if (mc.options.keyUse.isDown()) {
            return "use button";
        }
        if (mc.options.keyPickItem.isDown()) {
            return "pick-block button";
        }
        if (mc.options.keyDrop.isDown()) {
            return "drop key";
        }
        if (mc.options.keyInventory.isDown()) {
            return "inventory key";
        }
        return null;
    }

    /** Shortest signed distance between two yaws, so wrapping past 180 does not read as a spin. */
    private static double angleDelta(float current, float base) {
        double d = (current - base) % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        }
        if (d < -180.0) {
            d += 360.0;
        }
        return d;
    }
}
