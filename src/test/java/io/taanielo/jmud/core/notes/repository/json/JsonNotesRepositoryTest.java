package io.taanielo.jmud.core.notes.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.notes.NoteId;
import io.taanielo.jmud.core.notes.PlayerNote;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link JsonNotesRepository} covering load, write-behind save round-trips, and
 * empty-board file removal.
 */
class JsonNotesRepositoryTest {

    private static final RoomId ROOM = RoomId.of("training-yard");
    private static final Username ALICE = Username.of("Alice");

    @Test
    void loadsNotesGroupedByRoom(@TempDir Path dataRoot) throws IOException {
        Path boardsDir = Files.createDirectories(dataRoot.resolve("boards"));
        Files.writeString(boardsDir.resolve("training-yard.json"), """
            {
              "schema_version": 1,
              "notes": [
                {
                  "id": "n1",
                  "author": "Alice",
                  "content": "Welcome!",
                  "timestamp": "2026-07-09T12:00:00Z",
                  "room_id": "training-yard"
                }
              ]
            }
            """);

        JsonNotesRepository repository = new JsonNotesRepository(dataRoot);
        try {
            Map<RoomId, List<PlayerNote>> all = repository.loadAll();
            assertEquals(1, all.get(ROOM).size());
            PlayerNote note = all.get(ROOM).get(0);
            assertEquals("Welcome!", note.content());
            assertEquals(ALICE, note.author());
            assertEquals(Instant.parse("2026-07-09T12:00:00Z"), note.timestamp());
        } finally {
            repository.close();
        }
    }

    @Test
    void savesAndReloadsNotesRoundTrip(@TempDir Path dataRoot) {
        JsonNotesRepository repository = new JsonNotesRepository(dataRoot);
        PlayerNote note = new PlayerNote(
            NoteId.of("n1"), ALICE, "Persist me", Instant.parse("2026-07-09T12:00:00Z"), ROOM);
        try {
            repository.saveRoom(ROOM, List.of(note));
            waitForFile(dataRoot.resolve("boards").resolve("training-yard.json"), true);
        } finally {
            repository.close();
        }

        JsonNotesRepository reloaded = new JsonNotesRepository(dataRoot);
        try {
            List<PlayerNote> loaded = reloaded.loadAll().get(ROOM);
            assertEquals(1, loaded.size());
            assertEquals("Persist me", loaded.get(0).content());
            assertEquals(ROOM, loaded.get(0).roomId());
        } finally {
            reloaded.close();
        }
    }

    @Test
    void savingEmptyBoardDeletesFile(@TempDir Path dataRoot) {
        JsonNotesRepository repository = new JsonNotesRepository(dataRoot);
        Path file = dataRoot.resolve("boards").resolve("training-yard.json");
        PlayerNote note = new PlayerNote(
            NoteId.of("n1"), ALICE, "temp", Instant.parse("2026-07-09T12:00:00Z"), ROOM);
        try {
            repository.saveRoom(ROOM, List.of(note));
            waitForFile(file, true);
            repository.saveRoom(ROOM, List.of());
            waitForFile(file, false);
            assertFalse(Files.exists(file));
        } finally {
            repository.close();
        }
    }

    private static void waitForFile(Path path, boolean shouldExist) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(path) == shouldExist) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertTrue(Files.exists(path) == shouldExist,
            "Timed out waiting for file " + path + " exists=" + shouldExist);
    }
}
