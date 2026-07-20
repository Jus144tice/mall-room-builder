/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.client;

import com.jus144tice.mallroombuilder.Config;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Hotbar management for the build phase.
 *
 * <p>Switching slots is a single field write — {@code Inventory.selected} is public, and
 * {@code MultiPlayerGameMode} calls {@code ensureHasSentCarriedItem()} at the head of
 * {@code useItemOn} and {@code continueDestroyBlock}, which syncs it to the server for us.</p>
 *
 * <p><strong>Never switch slots mid-break.</strong> {@code sameDestroyTarget} consults
 * {@code shouldCauseBlockBreakReset}, so a slot change zeroes destroy progress. The engine only
 * calls in here at phase boundaries.</p>
 */
public final class HotbarSelector {

    private static final int HOTBAR_SIZE = 9;

    private static int savedSlot = -1;

    private HotbarSelector() {}

    /** Resolves the configured build block id, falling back to cobblestone if it is nonsense. */
    public static Item buildItem() {
        ResourceLocation id = ResourceLocation.tryParse(Config.buildBlock());
        if (id == null) {
            return Items.COBBLESTONE;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? Items.COBBLESTONE : item;
    }

    /** The block form of the build item, for the {@code isUnobstructed} collision check. */
    public static Block buildBlock() {
        return buildItem() instanceof BlockItem blockItem ? blockItem.getBlock() : Blocks.COBBLESTONE;
    }

    /** Remembers the player's slot so it can be restored when the job ends. */
    public static void remember(LocalPlayer player) {
        if (savedSlot < 0) {
            savedSlot = player.getInventory().selected;
        }
    }

    /**
     * Selects a hotbar slot holding the build block.
     *
     * @return false if the player has none, which pauses the job rather than failing it
     */
    public static boolean ensureBuildBlock(LocalPlayer player) {
        Item wanted = buildItem();
        if (player.getInventory().getSelected().getItem() == wanted) {
            return true;
        }
        if (!Config.autoSelectBuildSlot()) {
            return false;
        }
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
     * kind of helpfulness nobody asked for. Enable {@code autoSelectTool} to opt in.</p>
     */
    public static void selectBestTool(LocalPlayer player, net.minecraft.world.level.block.state.BlockState reference) {
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
    }

    /** Forgets the saved slot without restoring it. */
    public static void forget() {
        savedSlot = -1;
    }
}
