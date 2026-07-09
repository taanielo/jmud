package io.taanielo.jmud.core.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.RoomId;

class PlayerExplorationTest {

    @Test
    void emptyHasNoVisitedRooms() {
        PlayerExploration exploration = PlayerExploration.empty();

        assertEquals(0, exploration.count());
        assertFalse(exploration.hasVisited(RoomId.of("training-yard")));
    }

    @Test
    void visitAddsRoom() {
        PlayerExploration exploration = PlayerExploration.empty().visit(RoomId.of("armory"));

        assertTrue(exploration.hasVisited(RoomId.of("armory")));
        assertEquals(1, exploration.count());
    }

    @Test
    void visitingSameRoomTwiceIsIdempotentAndReturnsSameInstance() {
        PlayerExploration first = PlayerExploration.empty().visit(RoomId.of("armory"));
        PlayerExploration second = first.visit(RoomId.of("armory"));

        assertSame(first, second, "re-visiting a known room should not allocate a new component");
        assertEquals(1, second.count());
    }

    @Test
    void preservesFirstVisitedOrder() {
        PlayerExploration exploration = PlayerExploration.empty()
            .visit(RoomId.of("a"))
            .visit(RoomId.of("b"))
            .visit(RoomId.of("c"));

        assertEquals(List.of("a", "b", "c"), exploration.toIdList());
    }

    @Test
    void constructorIgnoresBlanksAndDeduplicates() {
        PlayerExploration exploration =
            new PlayerExploration(List.of("a", "a", "", "  ", "b"));

        assertEquals(List.of("a", "b"), exploration.toIdList());
    }

    @Test
    void nullListYieldsEmpty() {
        PlayerExploration exploration = new PlayerExploration(null);

        assertEquals(0, exploration.count());
    }
}
