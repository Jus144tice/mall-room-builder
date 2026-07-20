/*
 * Copyright 2026 Mall Room Builder contributors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.jus144tice.mallroombuilder.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Structure of the {@code /mallroom} tree.
 *
 * <p>The {@code <surface> <slot>} arguments are assembled by recursing over the surfaces not yet
 * named, which is the kind of construction that either works or blows the stack — and it only runs on
 * world join, so a broken tree would surface as "the command silently does not exist" rather than as
 * a crash anyone could read. Worth pinning down here.</p>
 *
 * <p>These assert the shape of the tree, not execution: running a command needs a live client.</p>
 */
class MallCommandTreeTest {

    private static CommandDispatcher<CommandSourceStack> dispatcher;

    @BeforeAll
    static void buildTree() {
        dispatcher = new CommandDispatcher<>();
        MallCommand.register(dispatcher);
    }

    /** Walks a literal path, returning the node it lands on or null. */
    private static CommandNode<CommandSourceStack> node(String... path) {
        CommandNode<CommandSourceStack> current = dispatcher.getRoot();
        for (String name : path) {
            current = current.getChild(name);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static CommandNode<CommandSourceStack> requireNode(String... path) {
        CommandNode<CommandSourceStack> found = node(path);
        assertNotNull(found, "missing command path: " + String.join(" ", path));
        return found;
    }

    @Test
    void theTreeBuildsWithoutRecursingForever() {
        assertNotNull(dispatcher.getRoot().getChild("mallroom"));
    }

    @Nested
    @DisplayName("top level")
    class TopLevel {

        @Test
        void hasTheFourVerbsAndTwoJobKinds() {
            requireNode("mallroom", "room");
            requireNode("mallroom", "spine");
            requireNode("mallroom", "fill");
            requireNode("mallroom", "preview");
            requireNode("mallroom", "status");
            requireNode("mallroom", "stop");
        }

        @Test
        void statusAndStopAreExecutableOnTheirOwn() {
            assertNotNull(requireNode("mallroom", "status").getCommand());
            assertNotNull(requireNode("mallroom", "stop").getCommand());
        }

        @Test
        void bareRoomAndSpineAreExecutable() {
            assertNotNull(requireNode("mallroom", "room").getCommand(), "carve-only room");
            assertNotNull(requireNode("mallroom", "spine").getCommand(), "carve-only spine");
        }
    }

    @Nested
    @DisplayName("modes")
    class Modes {

        @Test
        void roughAndFinishHangOffBothJobKinds() {
            assertNotNull(requireNode("mallroom", "room", "rough").getCommand());
            assertNotNull(requireNode("mallroom", "room", "finish").getCommand());
            assertNotNull(requireNode("mallroom", "spine", "rough").getCommand());
            assertNotNull(requireNode("mallroom", "spine", "finish").getCommand());
        }

        @Test
        void bothCarriesTheSameModes() {
            assertNotNull(requireNode("mallroom", "room", "both").getCommand());
            assertNotNull(requireNode("mallroom", "room", "both", "rough").getCommand());
            assertNotNull(requireNode("mallroom", "room", "both", "finish").getCommand());
        }

        @Test
        void theFillOnlyBranchOffersNoModes() {
            // "fill rough" would be a contradiction: rough means no recesses, so nothing to fill.
            assertNull(node("mallroom", "fill", "room", "rough"));
            assertNull(node("mallroom", "fill", "spine", "finish"));
        }
    }

    @Nested
    @DisplayName("surface arguments")
    class Surfaces {

        @Test
        void aRoomOffersAllFourSurfaces() {
            for (String surface : new String[] {"floor", "walls", "ceiling", "beam"}) {
                requireNode("mallroom", "room", surface);
            }
        }

        @Test
        void aSpineOffersOnlyFloorAndCeiling() {
            requireNode("mallroom", "spine", "floor");
            requireNode("mallroom", "spine", "ceiling");
            assertNull(node("mallroom", "spine", "walls"), "a corridor has no walls to fill");
            assertNull(node("mallroom", "spine", "beam"), "a corridor has no beam");
        }

        @Test
        void surfacesAlsoHangOffTheModes() {
            requireNode("mallroom", "room", "finish", "beam");
            requireNode("mallroom", "spine", "rough", "floor");
            requireNode("mallroom", "room", "both", "finish", "walls");
        }

        @Test
        void surfacesHangOffTheFillOnlyBranchToo() {
            requireNode("mallroom", "fill", "room", "beam");
            requireNode("mallroom", "fill", "spine", "ceiling");
            requireNode("mallroom", "fill", "room", "both", "floor");
        }

        @Test
        void previewMirrorsEverything() {
            requireNode("mallroom", "preview", "room", "finish", "beam");
            requireNode("mallroom", "preview", "spine", "ceiling");
            requireNode("mallroom", "preview", "fill", "room", "walls");
        }
    }

    @Nested
    @DisplayName("any-order surface pairs")
    class AnyOrder {

        /** Steps past a surface literal and its slot argument to the node that follows. */
        private CommandNode<CommandSourceStack> afterPair(CommandNode<CommandSourceStack> from, String surface) {
            CommandNode<CommandSourceStack> literal = from.getChild(surface);
            assertNotNull(literal, "no " + surface + " here");
            assertEquals(1, literal.getChildren().size(), surface + " should carry exactly its slot argument");
            return literal.getChildren().iterator().next();
        }

        @Test
        void everySurfaceIsFollowedByASlotThatExecutes() {
            CommandNode<CommandSourceStack> room = requireNode("mallroom", "room");
            for (String surface : new String[] {"floor", "walls", "ceiling", "beam"}) {
                CommandNode<CommandSourceStack> slot = afterPair(room, surface);
                assertNotNull(slot.getCommand(), surface + " <slot> should be a complete command on its own");
            }
        }

        @Test
        void anySurfaceCanFollowAnyOther() {
            // beam 6 ceiling 4 floor 3 must parse just as ceiling 4 floor 3 beam 6 does.
            CommandNode<CommandSourceStack> afterBeam = afterPair(requireNode("mallroom", "room"), "beam");
            CommandNode<CommandSourceStack> afterCeiling = afterPair(afterBeam, "ceiling");
            CommandNode<CommandSourceStack> afterFloor = afterPair(afterCeiling, "floor");
            assertNotNull(afterFloor.getCommand());
            assertNotNull(afterPair(afterFloor, "walls").getCommand(), "all four, in reverse order");
        }

        @Test
        void aSurfaceIsNeverOfferedTwiceInOneCommand() {
            CommandNode<CommandSourceStack> afterFloor = afterPair(requireNode("mallroom", "room"), "floor");
            assertNull(afterFloor.getChild("floor"), "floor should not be offered again after floor");
            assertNotNull(afterFloor.getChild("walls"));
        }

        @Test
        void theChainBottomsOutWhenEverySurfaceIsNamed() {
            CommandNode<CommandSourceStack> at = requireNode("mallroom", "room");
            for (String surface : new String[] {"floor", "walls", "ceiling", "beam"}) {
                at = afterPair(at, surface);
            }
            assertTrue(at.getChildren().isEmpty(), "nothing left to offer, so the recursion terminated");
            assertNotNull(at.getCommand());
        }
    }

    @Test
    void spineTakesAnOptionalLengthThatStillCarriesSurfaces() {
        CommandNode<CommandSourceStack> spine = requireNode("mallroom", "spine");
        CommandNode<CommandSourceStack> length = spine.getChild("length");
        assertNotNull(length, "spine <length>");
        assertNotNull(length.getCommand(), "length alone is a complete command");
        assertNotNull(length.getChild("floor"), "and still accepts surfaces after it");
        assertNotNull(length.getChild("rough"), "and the modes");
    }
}
