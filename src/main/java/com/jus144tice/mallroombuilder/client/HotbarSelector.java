/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.client;

import com.jus144tice.mallroombuilder.Config;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hotbar management.
 *
 * <p>Switching slots is a bare {@code Inventory.selected} write —
 * {@code MultiPlayerGameMode.ensureHasSentCarriedItem()} runs at the head of
 * {@code continueDestroyBlock} and {@code useItemOn} and syncs it for us.</p>
 *
 * <p><strong>Never switch mid-break.</strong> {@code sameDestroyTarget} consults
 * {@code shouldCauseBlockBreakReset}, so a slot change zeroes destroy progress. The engine tracks a
 * {@link #miningSlot()} and only ever swaps away from it between blocks, for a backfill.</p>
 */
public final class HotbarSelector {

    private static final int HOTBAR_SIZE = 9;

    private static int savedSlot = -1;
    private static int miningSlot = -1;

    private HotbarSelector() {}

    /** Resolves the configured backfill block, falling back to cobblestone if it is nonsense. */
    public static Item backfillItem() {
        ResourceLocation id = ResourceLocation.tryParse(Config.backfillBlock());
        if (id == null) {
            return Items.COBBLESTONE;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? Items.COBBLESTONE : item;
    }

    /** The block form of the backfill item, for the collision check before placing. */
    public static Block backfillBlock() {
        return backfillItem() instanceof BlockItem blockItem ? blockItem.getBlock() : Blocks.COBBLESTONE;
    }

    /** Remembers the player's slot so it can be restored when the job ends. */
    public static void remember(LocalPlayer player) {
        if (savedSlot < 0) {
            savedSlot = player.getInventory().selected;
        }
    }

    /** The slot the job mines with. Everything swaps back to this before the next break. */
    public static int miningSlot() {
        return miningSlot;
    }

    /** Latches the current slot as the mining slot. Call at the carve phase boundary. */
    public static void latchMiningSlot(LocalPlayer player) {
        miningSlot = player.getInventory().selected;
    }

    public static boolean onMiningSlot(LocalPlayer player) {
        return miningSlot < 0 || player.getInventory().selected == miningSlot;
    }

    public static void selectMiningSlot(LocalPlayer player) {
        if (miningSlot >= 0) {
            select(player, miningSlot);
        }
    }

    /**
     * The hand already holding the backfill block, main preferred, or null.
     *
     * <p>Checking the off hand first-class is not a nicety: a player who keeps cobblestone there
     * never triggers a hotbar swap at all, so backfill can never disturb a break.</p>
     */
    public static InteractionHand backfillHand(LocalPlayer player) {
        Item wanted = backfillItem();
        if (player.getMainHandItem().getItem() == wanted) {
            return InteractionHand.MAIN_HAND;
        }
        if (player.getOffhandItem().getItem() == wanted) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /**
     * Selects a hotbar slot holding the backfill block.
     *
     * @return false if the player is not carrying any
     */
    public static boolean selectBackfillSlot(LocalPlayer player) {
        Item wanted = backfillItem();
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == wanted) {
                select(player, slot);
                return true;
            }
        }
        return false;
    }

    /**
     * Selects the hotbar slot whose tool digs {@code reference} fastest.
     *
     * <p>Off by default: the player picked which pickaxe to hold, and silently swapping it is the
     * kind of helpfulness nobody asked for.</p>
     */
    public static void selectBestTool(LocalPlayer player, BlockState reference) {
        if (!Config.autoSelectTool()) {
            return;
        }
        int bestSlot = player.getInventory().selected;
        float bestSpeed = player.getInventory().getSelected().getDestroySpeed(reference);
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            float speed = player.getInventory().getItem(slot).getDestroySpeed(reference);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }
        select(player, bestSlot);
    }

    private static void select(LocalPlayer player, int slot) {
        player.getInventory().selected = slot;
        // Keep the dead-man's switch from reading our own write as the player grabbing the mouse.
        InputWatch.setExpectedSlot(slot);
    }

    /** Puts the slot back the way we found it. */
    public static void restore(LocalPlayer player) {
        if (savedSlot >= 0 && Config.restoreHotbarSlotOnFinish()) {
            select(player, savedSlot);
        }
        savedSlot = -1;
        miningSlot = -1;
    }

    /** Forgets saved state without restoring it. */
    public static void forget() {
        savedSlot = -1;
        miningSlot = -1;
    }
}
