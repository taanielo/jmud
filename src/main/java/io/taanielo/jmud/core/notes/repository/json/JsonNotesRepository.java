package io.taanielo.jmud.core.notes.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.notes.NoteId;
import io.taanielo.jmud.core.notes.NotesRepository;
import io.taanielo.jmud.core.notes.NotesRepositoryException;
import io.taanielo.jmud.core.notes.PlayerNote;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Loads and persists room bulletin-board notes as {@code data/boards/<room-id>.json} files.
 *
 * <p>All notes are eagerly loaded at construction. Writes are handed to a single dedicated virtual
 * thread (write-behind), so the tick thread that mutates note state never blocks on disk
 * (AGENTS.md §5). Saves are coalesced per room: only the latest enqueued snapshot for a given room
 * is kept, so a burst of edits collapses into at most a couple of actual writes.
 */
@Slf4j
public class JsonNotesRepository implements NotesRepository, AutoCloseable {

    private static final int SCHEMA_VERSION = 1;
    private static final String BOARDS_DIR = "boards";
    private static final long IDLE_POLL_MILLIS = 25;

    private final ObjectMapper objectMapper;
    private final Path boardsDirPath;

    /** Latest snapshot pending write for each dirty room. */
    private final ConcurrentHashMap<RoomId, List<PlayerNote>> pending = new ConcurrentHashMap<>();
    /** Rooms currently queued for the worker to pick up; guards against duplicate queue entries. */
    private final Set<RoomId> queued = ConcurrentHashMap.newKeySet();
    private final BlockingQueue<RoomId> dirtyRooms = new LinkedBlockingQueue<>();
    private final AtomicReference<@Nullable Thread> workerThread = new AtomicReference<>();
    private volatile boolean running = true;

    /**
     * Creates a repository rooted at {@code data}, loading existing boards and starting the writer.
     *
     * @throws NotesRepositoryException if the boards directory cannot be created or read
     */
    public JsonNotesRepository() {
        this(Path.of("data"));
    }

    /**
     * Creates a repository rooted at the given data directory, loading existing boards and starting
     * the write-behind worker thread.
     *
     * @param dataRoot the root data directory (e.g. {@code data})
     * @throws NotesRepositoryException if the boards directory cannot be created
     */
    public JsonNotesRepository(Path dataRoot) {
        this.objectMapper = JsonDataMapper.create();
        this.boardsDirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(BOARDS_DIR);
        ensureDirectory(boardsDirPath);
        Thread thread = Thread.ofVirtual().name("notes-repository-writer").start(this::runWorker);
        workerThread.set(thread);
    }

    @Override
    public Map<RoomId, List<PlayerNote>> loadAll() {
        Map<RoomId, List<PlayerNote>> byRoom = new HashMap<>();
        try (var stream = Files.list(boardsDirPath)) {
            for (Path path : stream.filter(p -> p.toString().endsWith(".json")).toList()) {
                readBoard(path, byRoom);
            }
        } catch (IOException e) {
            throw new NotesRepositoryException("Failed to list notes data files: " + e.getMessage(), e);
        }
        int total = byRoom.values().stream().mapToInt(List::size).sum();
        log.info("Loaded {} note(s) across {} room(s) from {}", total, byRoom.size(), boardsDirPath);
        return byRoom;
    }

    @Override
    public void saveRoom(RoomId roomId, List<PlayerNote> notes) {
        Objects.requireNonNull(roomId, "Room id is required");
        Objects.requireNonNull(notes, "Notes are required");
        pending.put(roomId, List.copyOf(notes));
        if (queued.add(roomId)) {
            dirtyRooms.add(roomId);
        }
    }

    /**
     * Stops the write-behind worker thread. Any pending writes still queued are best-effort flushed
     * before the thread exits.
     */
    @Override
    public void close() {
        running = false;
        Thread thread = workerThread.get();
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(Duration.ofSeconds(5).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ── private helpers ───────────────────────────────────────────────

    private void readBoard(Path path, Map<RoomId, List<PlayerNote>> byRoom) {
        RoomNotesDto dto = readDto(path);
        if (dto.schemaVersion() != SCHEMA_VERSION) {
            throw new NotesRepositoryException(
                "Unsupported notes schema version " + dto.schemaVersion() + " in " + path);
        }
        List<NoteDto> noteDtos = dto.notes();
        if (noteDtos == null) {
            return;
        }
        for (NoteDto noteDto : noteDtos) {
            PlayerNote note = toDomain(noteDto, path);
            byRoom.computeIfAbsent(note.roomId(), key -> new ArrayList<>()).add(note);
        }
    }

    private PlayerNote toDomain(NoteDto dto, Path source) {
        try {
            Instant timestamp = Instant.parse(Objects.requireNonNull(dto.timestamp(), "Note timestamp is required"));
            return new PlayerNote(
                NoteId.of(Objects.requireNonNull(dto.id(), "Note id is required")),
                Username.of(Objects.requireNonNull(dto.author(), "Note author is required")),
                Objects.requireNonNull(dto.content(), "Note content is required"),
                timestamp,
                RoomId.of(Objects.requireNonNull(dto.roomId(), "Note room id is required"))
            );
        } catch (IllegalArgumentException | NullPointerException | DateTimeParseException e) {
            throw new NotesRepositoryException("Invalid note data in " + source + ": " + e.getMessage(), e);
        }
    }

    private RoomNotesDto readDto(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), RoomNotesDto.class);
        } catch (IOException e) {
            throw new NotesRepositoryException("Failed to read notes data from " + path + ": " + e.getMessage(), e);
        }
    }

    private void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new NotesRepositoryException("Failed to create boards directory " + path, e);
        }
    }

    private void runWorker() {
        while (running) {
            RoomId roomId;
            try {
                roomId = dirtyRooms.poll(IDLE_POLL_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!running) {
                    return;
                }
                continue;
            }
            if (roomId == null) {
                continue;
            }
            queued.remove(roomId);
            List<PlayerNote> snapshot = pending.remove(roomId);
            if (snapshot != null) {
                writeRoom(roomId, snapshot);
            }
        }
    }

    private void writeRoom(RoomId roomId, List<PlayerNote> notes) {
        Path file = boardsDirPath.resolve(fileName(roomId));
        try {
            if (notes.isEmpty()) {
                Files.deleteIfExists(file);
                return;
            }
            List<NoteDto> noteDtos = notes.stream()
                .map(note -> new NoteDto(
                    note.id().value(),
                    note.author().getValue(),
                    note.content(),
                    note.timestamp().toString(),
                    note.roomId().getValue()))
                .toList();
            RoomNotesDto dto = new RoomNotesDto(SCHEMA_VERSION, noteDtos);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), dto);
        } catch (IOException e) {
            log.error("Failed to persist notes for room {} to {}", roomId.getValue(), file, e);
        }
    }

    /** Derives a filesystem-safe board file name from the room id. */
    private static String fileName(RoomId roomId) {
        String sanitized = roomId.getValue().replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized + ".json";
    }
}
