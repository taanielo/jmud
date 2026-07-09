package io.taanielo.jmud.core.notes;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Domain service owning the in-memory bulletin-board state for every room and enforcing the rules
 * for posting and deleting notes.
 *
 * <p>The authoritative per-room note lists live in this service and are mutated only on the tick
 * thread via the player command queue (AGENTS.md §5). After each mutation the affected room's note
 * list is handed to the {@link NotesRepository} for write-behind persistence, so no blocking disk
 * I/O happens on the tick thread. All notes are eagerly loaded from the repository at construction
 * so reads never touch disk.
 */
public class NotesService {

    /** Maximum number of characters allowed in a single note's body. */
    public static final int MAX_CONTENT_LENGTH = 500;

    /** Maximum number of notes retained per room's board. */
    public static final int MAX_NOTES_PER_ROOM = 50;

    private final NotesRepository repository;
    private final Clock clock;
    private final Map<RoomId, List<PlayerNote>> notesByRoom;

    /**
     * Creates a notes service, eagerly loading all persisted notes from the repository.
     *
     * @param repository the persistence port used to load notes at startup and persist changes
     * @param clock      the clock used to timestamp newly posted notes
     */
    public NotesService(NotesRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "Notes repository is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.notesByRoom = new HashMap<>();
        repository.loadAll().forEach((roomId, notes) -> notesByRoom.put(roomId, new ArrayList<>(notes)));
    }

    /**
     * Returns an immutable snapshot of the notes pinned to the given room, oldest first.
     *
     * @param roomId the room whose board to read
     * @return the room's notes (empty if the board has none)
     */
    public List<PlayerNote> notesInRoom(RoomId roomId) {
        Objects.requireNonNull(roomId, "Room id is required");
        List<PlayerNote> notes = notesByRoom.get(roomId);
        return notes == null ? List.of() : List.copyOf(notes);
    }

    /**
     * Posts a new note to the given room's board, timestamped with the service clock, and persists
     * the room's updated board.
     *
     * @param roomId  the room to post to
     * @param author  the posting player's username
     * @param content the note body; must be non-blank and at most {@link #MAX_CONTENT_LENGTH} chars
     * @return the newly created note
     * @throws IllegalArgumentException if the content is blank, too long, or the room's board is full
     */
    public PlayerNote postNote(RoomId roomId, Username author, String content) {
        Objects.requireNonNull(roomId, "Room id is required");
        Objects.requireNonNull(author, "Author is required");
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Note content must not be blank");
        }
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException(
                "Note content must be at most " + MAX_CONTENT_LENGTH + " characters");
        }
        List<PlayerNote> notes = notesByRoom.computeIfAbsent(roomId, key -> new ArrayList<>());
        if (notes.size() >= MAX_NOTES_PER_ROOM) {
            throw new IllegalArgumentException(
                "This board is full (" + MAX_NOTES_PER_ROOM + " notes). Delete a note before posting.");
        }
        PlayerNote note = new PlayerNote(NoteId.random(), author, trimmed, Instant.now(clock), roomId);
        notes.add(note);
        repository.saveRoom(roomId, notes);
        return note;
    }

    /**
     * Deletes the note at the given one-based position on the room's board, if the requester is
     * permitted to delete it. Only the note's author may delete it.
     *
     * @param roomId       the room whose board to modify
     * @param oneBasedIndex the note's display position (1 = oldest)
     * @param requester    the username of the player requesting deletion
     * @return the categorised deletion result; on success the room's board is persisted
     */
    public NoteDeletionResult deleteNote(RoomId roomId, int oneBasedIndex, Username requester) {
        Objects.requireNonNull(roomId, "Room id is required");
        Objects.requireNonNull(requester, "Requester is required");
        List<PlayerNote> notes = notesByRoom.get(roomId);
        if (notes == null || oneBasedIndex < 1 || oneBasedIndex > notes.size()) {
            return NoteDeletionResult.noSuchNote();
        }
        PlayerNote note = notes.get(oneBasedIndex - 1);
        if (!note.isAuthoredBy(requester)) {
            return NoteDeletionResult.notAuthorized();
        }
        notes.remove(oneBasedIndex - 1);
        repository.saveRoom(roomId, notes);
        return NoteDeletionResult.deleted(note);
    }
}
