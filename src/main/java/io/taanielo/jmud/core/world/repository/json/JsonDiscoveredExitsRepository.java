package io.taanielo.jmud.core.world.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.DiscoveredExitsRepository;
import io.taanielo.jmud.core.world.RoomId;

/**
 * Persists world-scoped hidden-exit discovery state as a single
 * {@code data/world-state/discovered-exits.json} file.
 *
 * <p>All discoveries are eagerly loaded at construction and mirrored into an in-memory snapshot.
 * Writes are handed to a single dedicated write-behind virtual thread, so the tick thread that
 * reveals a hidden exit never blocks on disk (AGENTS.md §5). Because discoveries are rare and
 * world-scoped, the whole (small) file is rewritten on each change; bursts coalesce into at most a
 * couple of actual writes.
 *
 * <p>Loading is deliberately defensive: a missing, empty, or malformed file yields no discoveries
 * rather than throwing, so a corrupt store can never accidentally reveal an exit that no player
 * has found.
 */
@Slf4j
public class JsonDiscoveredExitsRepository implements DiscoveredExitsRepository, AutoCloseable {

    private static final int SCHEMA_VERSION = 1;
    private static final String WORLD_STATE_DIR = "world-state";
    private static final String FILE_NAME = "discovered-exits.json";
    private static final long IDLE_POLL_MILLIS = 25;
    private static final Object WRITE_SIGNAL = new Object();

    private final ObjectMapper objectMapper;
    private final Path dirPath;
    private final Path filePath;

    /** Authoritative in-memory snapshot of what has been persisted (plus not-yet-flushed changes). */
    private final Map<RoomId, Set<Direction>> state = new ConcurrentHashMap<>();
    private final BlockingQueue<Object> writeSignals = new LinkedBlockingQueue<>();
    private final AtomicBoolean pendingWrite = new AtomicBoolean(false);
    private final AtomicReference<Thread> workerThread = new AtomicReference<>();
    private volatile boolean running = true;

    /**
     * Creates a repository rooted at {@code data}, loading existing discoveries and starting the
     * write-behind worker.
     */
    public JsonDiscoveredExitsRepository() {
        this(Path.of("data"));
    }

    /**
     * Creates a repository rooted at the given data directory, loading existing discoveries and
     * starting the write-behind worker thread.
     *
     * @param dataRoot the root data directory (e.g. {@code data})
     */
    public JsonDiscoveredExitsRepository(Path dataRoot) {
        this.objectMapper = JsonDataMapper.create();
        this.dirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(WORLD_STATE_DIR);
        this.filePath = dirPath.resolve(FILE_NAME);
        ensureDirectory(dirPath);
        Thread thread = Thread.ofVirtual().name("discovered-exits-writer").start(this::runWorker);
        workerThread.set(thread);
    }

    @Override
    public Map<RoomId, Set<Direction>> loadAll() {
        Map<RoomId, Set<Direction>> loaded = readFile();
        loaded.forEach((roomId, dirs) -> state.put(roomId, EnumSet.copyOf(dirs)));
        int total = loaded.values().stream().mapToInt(Set::size).sum();
        log.info("Loaded {} discovered hidden exit(s) across {} room(s) from {}",
            total, loaded.size(), filePath);
        return loaded;
    }

    @Override
    public void save(RoomId roomId, Set<Direction> directions) {
        Objects.requireNonNull(roomId, "Room id is required");
        Objects.requireNonNull(directions, "Directions are required");
        if (directions.isEmpty()) {
            state.remove(roomId);
        } else {
            state.put(roomId, EnumSet.copyOf(directions));
        }
        if (pendingWrite.compareAndSet(false, true)) {
            writeSignals.add(WRITE_SIGNAL);
        }
    }

    /**
     * Stops the write-behind worker thread, flushing any pending change to disk before returning so
     * a freshly reconstructed repository observes the latest state.
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
        if (pendingWrite.getAndSet(false)) {
            writeFile();
        }
    }

    // ── private helpers ───────────────────────────────────────────────

    private Map<RoomId, Set<Direction>> readFile() {
        if (!Files.exists(filePath)) {
            return new HashMap<>();
        }
        DiscoveredExitsFileDto dto;
        try {
            dto = objectMapper.readValue(filePath.toFile(), DiscoveredExitsFileDto.class);
        } catch (IOException e) {
            log.warn("Failed to read discovered-exits store {}; treating as empty: {}",
                filePath, e.getMessage());
            return new HashMap<>();
        }
        if (dto == null || dto.schemaVersion() != SCHEMA_VERSION || dto.rooms() == null) {
            if (dto != null && dto.schemaVersion() != SCHEMA_VERSION) {
                log.warn("Ignoring discovered-exits store {} with unsupported schema version {}",
                    filePath, dto.schemaVersion());
            }
            return new HashMap<>();
        }
        Map<RoomId, Set<Direction>> byRoom = new HashMap<>();
        for (RoomDiscoveredExitsDto roomDto : dto.rooms()) {
            addRoom(roomDto, byRoom);
        }
        return byRoom;
    }

    private void addRoom(RoomDiscoveredExitsDto roomDto, Map<RoomId, Set<Direction>> byRoom) {
        if (roomDto == null || roomDto.roomId() == null || roomDto.roomId().isBlank()
            || roomDto.directions() == null) {
            return;
        }
        Set<Direction> directions = EnumSet.noneOf(Direction.class);
        for (String raw : roomDto.directions()) {
            parseDirection(raw).ifPresent(directions::add);
        }
        if (!directions.isEmpty()) {
            byRoom.put(RoomId.of(roomDto.roomId()), directions);
        }
    }

    private Optional<Direction> parseDirection(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Direction.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring unknown direction '{}' in {}", raw, filePath);
            return Optional.empty();
        }
    }

    private void runWorker() {
        while (running) {
            Object signal;
            try {
                signal = writeSignals.poll(IDLE_POLL_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!running) {
                    return;
                }
                continue;
            }
            if (signal == null) {
                continue;
            }
            pendingWrite.set(false);
            writeFile();
        }
    }

    private synchronized void writeFile() {
        List<RoomDiscoveredExitsDto> rooms = new ArrayList<>();
        for (Map.Entry<RoomId, Set<Direction>> entry : state.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            List<String> directions = entry.getValue().stream().map(Direction::name).sorted().toList();
            rooms.add(new RoomDiscoveredExitsDto(entry.getKey().getValue(), directions));
        }
        rooms.sort((a, b) -> a.roomId().compareTo(b.roomId()));
        DiscoveredExitsFileDto dto = new DiscoveredExitsFileDto(SCHEMA_VERSION, rooms);
        try {
            if (rooms.isEmpty()) {
                Files.deleteIfExists(filePath);
                return;
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), dto);
        } catch (IOException e) {
            log.error("Failed to persist discovered hidden exits to {}", filePath, e);
        }
    }

    private void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create world-state directory " + path, e);
        }
    }
}
