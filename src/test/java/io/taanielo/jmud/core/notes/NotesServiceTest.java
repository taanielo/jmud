package io.taanielo.jmud.core.notes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Unit tests for {@link NotesService} covering posting, listing, deletion authorization, validation,
 * and write-through persistence.
 */
class NotesServiceTest {

    private static final RoomId ROOM = RoomId.of("training-yard");
    private static final Username ALICE = Username.of("Alice");
    private static final Username BOB = Username.of("Bob");
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-09T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void postAddsNoteAndPersistsRoom() {
        RecordingRepository repository = new RecordingRepository();
        NotesService service = new NotesService(repository, FIXED);

        PlayerNote note = service.postNote(ROOM, ALICE, "Hello board");

        assertEquals(ALICE, note.author());
        assertEquals("Hello board", note.content());
        assertEquals(Instant.parse("2026-07-09T12:00:00Z"), note.timestamp());
        assertEquals(List.of(note), service.notesInRoom(ROOM));
        assertEquals(1, repository.saves.get(ROOM).size());
    }

    @Test
    void postTrimsContentAndRejectsBlank() {
        NotesService service = new NotesService(new RecordingRepository(), FIXED);
        PlayerNote note = service.postNote(ROOM, ALICE, "  spaced  ");
        assertEquals("spaced", note.content());
        assertThrows(IllegalArgumentException.class, () -> service.postNote(ROOM, ALICE, "   "));
    }

    @Test
    void postRejectsOverlongContent() {
        NotesService service = new NotesService(new RecordingRepository(), FIXED);
        String tooLong = "x".repeat(NotesService.MAX_CONTENT_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> service.postNote(ROOM, ALICE, tooLong));
    }

    @Test
    void postRejectsWhenBoardFull() {
        NotesService service = new NotesService(new RecordingRepository(), FIXED);
        for (int i = 0; i < NotesService.MAX_NOTES_PER_ROOM; i++) {
            service.postNote(ROOM, ALICE, "note " + i);
        }
        assertThrows(IllegalArgumentException.class, () -> service.postNote(ROOM, ALICE, "overflow"));
    }

    @Test
    void notesInRoomReturnsImmutableSnapshot() {
        NotesService service = new NotesService(new RecordingRepository(), FIXED);
        service.postNote(ROOM, ALICE, "one");
        List<PlayerNote> snapshot = service.notesInRoom(ROOM);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(null));
    }

    @Test
    void deleteByAuthorRemovesNoteAndPersists() {
        RecordingRepository repository = new RecordingRepository();
        NotesService service = new NotesService(repository, FIXED);
        service.postNote(ROOM, ALICE, "first");
        service.postNote(ROOM, ALICE, "second");

        NoteDeletionResult result = service.deleteNote(ROOM, 1, ALICE);

        assertTrue(result.success());
        assertEquals("first", result.removed().content());
        assertEquals(1, service.notesInRoom(ROOM).size());
        assertEquals("second", service.notesInRoom(ROOM).get(0).content());
    }

    @Test
    void deleteByNonAuthorIsRejected() {
        NotesService service = new NotesService(new RecordingRepository(), FIXED);
        service.postNote(ROOM, ALICE, "alice's note");

        NoteDeletionResult result = service.deleteNote(ROOM, 1, BOB);

        assertFalse(result.success());
        assertEquals(NoteDeletionResult.Outcome.NOT_AUTHORIZED, result.outcome());
        assertEquals(1, service.notesInRoom(ROOM).size());
    }

    @Test
    void deleteOutOfRangeReportsNoSuchNote() {
        NotesService service = new NotesService(new RecordingRepository(), FIXED);
        service.postNote(ROOM, ALICE, "only");

        assertEquals(NoteDeletionResult.Outcome.NO_SUCH_NOTE, service.deleteNote(ROOM, 2, ALICE).outcome());
        assertEquals(NoteDeletionResult.Outcome.NO_SUCH_NOTE, service.deleteNote(ROOM, 0, ALICE).outcome());
        assertEquals(NoteDeletionResult.Outcome.NO_SUCH_NOTE,
            service.deleteNote(RoomId.of("elsewhere"), 1, ALICE).outcome());
    }

    @Test
    void loadsExistingNotesFromRepositoryAtConstruction() {
        RecordingRepository repository = new RecordingRepository();
        PlayerNote existing = new PlayerNote(
            NoteId.of("n1"), ALICE, "loaded", Instant.parse("2026-01-01T00:00:00Z"), ROOM);
        repository.preloaded.put(ROOM, new ArrayList<>(List.of(existing)));

        NotesService service = new NotesService(repository, FIXED);

        assertEquals(List.of(existing), service.notesInRoom(ROOM));
    }

    /** In-memory {@link NotesRepository} that records the note lists handed to {@link #saveRoom}. */
    private static final class RecordingRepository implements NotesRepository {
        private final Map<RoomId, List<PlayerNote>> preloaded = new HashMap<>();
        private final Map<RoomId, List<PlayerNote>> saves = new HashMap<>();

        @Override
        public Map<RoomId, List<PlayerNote>> loadAll() {
            return preloaded;
        }

        @Override
        public void saveRoom(RoomId roomId, List<PlayerNote> notes) {
            saves.put(roomId, List.copyOf(notes));
        }
    }
}
