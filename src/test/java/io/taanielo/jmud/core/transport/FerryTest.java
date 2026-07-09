package io.taanielo.jmud.core.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.world.RoomId;

class FerryTest {

    private static final RoomId DECK = RoomId.of("deck");
    private static final List<RoomId> ROUTE = List.of(RoomId.of("a"), RoomId.of("b"), RoomId.of("c"));

    private static Ferry ferry(int startLegIndex) {
        return new Ferry(FerryId.of("f"), "Ferry", DECK, ROUTE, 5, startLegIndex, List.of(), List.of());
    }

    @Test
    void nextLegIndexCyclesBackToStart() {
        Ferry ferry = ferry(0);
        assertEquals(1, ferry.nextLegIndex(0));
        assertEquals(2, ferry.nextLegIndex(1));
        assertEquals(0, ferry.nextLegIndex(2));
    }

    @Test
    void dockAtResolvesTheRouteEntry() {
        Ferry ferry = ferry(0);
        assertEquals(RoomId.of("a"), ferry.dockAt(0));
        assertEquals(RoomId.of("c"), ferry.dockAt(2));
    }

    @Test
    void rejectsRoutesWithFewerThanTwoDocks() {
        assertThrows(IllegalArgumentException.class, () -> new Ferry(
            FerryId.of("f"), "Ferry", DECK, List.of(RoomId.of("a")), 5, 0, List.of(), List.of()));
    }

    @Test
    void rejectsNonPositiveTicksPerLeg() {
        assertThrows(IllegalArgumentException.class, () -> new Ferry(
            FerryId.of("f"), "Ferry", DECK, ROUTE, 0, 0, List.of(), List.of()));
    }

    @Test
    void rejectsStartLegIndexOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> ferry(3));
        assertThrows(IllegalArgumentException.class, () -> ferry(-1));
    }

    @Test
    void acceptsEmptyFlavourPools() {
        Ferry ferry = new Ferry(FerryId.of("f"), "Ferry", DECK, ROUTE, 5, 0, List.of(), List.of());
        assertEquals(List.of(), ferry.departureMessages());
        assertEquals(List.of(), ferry.arrivalMessages());
    }

    @Test
    void rejectsBlankFerryId() {
        assertThrows(IllegalArgumentException.class, () -> FerryId.of("  "));
    }
}
