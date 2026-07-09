package io.taanielo.jmud.core.transport.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.transport.Ferry;
import io.taanielo.jmud.core.world.RoomId;

class JsonFerryRepositoryTest {

    @Test
    void loadsFerryDefinitionsFromDataDirectory(@TempDir Path dataRoot) throws IOException {
        writeFerry(dataRoot, "coastal.json", """
            {
              "schema_version": 1,
              "id": "coastal-ferry",
              "name": "Coastal Ferry",
              "deck_room_id": "deck",
              "route": ["north-dock", "south-dock"],
              "ticks_per_leg": 15,
              "start_leg_index": 1,
              "departure_messages": ["Away we go."],
              "arrival_messages": ["We have arrived."]
            }
            """);

        List<Ferry> ferries = new JsonFerryRepository(dataRoot).findAll();

        assertEquals(1, ferries.size());
        Ferry ferry = ferries.get(0);
        assertEquals("coastal-ferry", ferry.id().value());
        assertEquals("Coastal Ferry", ferry.name());
        assertEquals(RoomId.of("deck"), ferry.deckRoomId());
        assertEquals(List.of(RoomId.of("north-dock"), RoomId.of("south-dock")), ferry.route());
        assertEquals(15, ferry.ticksPerLeg());
        assertEquals(1, ferry.startLegIndex());
        assertEquals(List.of("Away we go."), ferry.departureMessages());
        assertEquals(List.of("We have arrived."), ferry.arrivalMessages());
    }

    @Test
    void defaultsStartLegIndexToZeroWhenOmitted(@TempDir Path dataRoot) throws IOException {
        writeFerry(dataRoot, "minimal.json", """
            {
              "schema_version": 1,
              "id": "minimal",
              "name": "Minimal",
              "deck_room_id": "deck",
              "route": ["a", "b"],
              "ticks_per_leg": 4
            }
            """);

        Ferry ferry = new JsonFerryRepository(dataRoot).findAll().get(0);

        assertEquals(0, ferry.startLegIndex());
        assertTrue(ferry.departureMessages().isEmpty());
    }

    @Test
    void returnsEmptyListWhenNoFerriesDirectory(@TempDir Path dataRoot) {
        assertTrue(new JsonFerryRepository(dataRoot).findAll().isEmpty());
    }

    @Test
    void rejectsUnsupportedSchemaVersion(@TempDir Path dataRoot) throws IOException {
        writeFerry(dataRoot, "bad.json", """
            {
              "schema_version": 99,
              "id": "bad",
              "name": "Bad",
              "deck_room_id": "deck",
              "route": ["a", "b"],
              "ticks_per_leg": 4
            }
            """);

        assertThrows(FerryRepositoryException.class, () -> new JsonFerryRepository(dataRoot));
    }

    @Test
    void rejectsRouteWithASingleDock(@TempDir Path dataRoot) throws IOException {
        writeFerry(dataRoot, "single.json", """
            {
              "schema_version": 1,
              "id": "single",
              "name": "Single",
              "deck_room_id": "deck",
              "route": ["a"],
              "ticks_per_leg": 4
            }
            """);

        assertThrows(FerryRepositoryException.class, () -> new JsonFerryRepository(dataRoot));
    }

    private static void writeFerry(Path dataRoot, String fileName, String json) throws IOException {
        Path ferriesDir = dataRoot.resolve("ferries");
        Files.createDirectories(ferriesDir);
        Files.writeString(ferriesDir.resolve(fileName), json);
    }
}
