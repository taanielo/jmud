package io.taanielo.jmud.core.bounty.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.bounty.Bounty;
import io.taanielo.jmud.core.bounty.BountyRepository;
import io.taanielo.jmud.core.world.repository.json.JsonDataMapper;

/**
 * Persists the open-bounty ledger as a single {@code data/world-state/bounties.json} file with a
 * versioned schema (see {@code docs/data-schema.md}).
 *
 * <p>All open bounties are eagerly loaded at construction into an authoritative in-memory snapshot,
 * so {@link #findAll()} — called on the tick thread for every mob death — never touches disk
 * (AGENTS.md §5). {@link #save(List)} swaps the snapshot synchronously and hands the write to a single
 * dedicated write-behind virtual thread, mirroring {@code JsonDiscoveredExitsRepository}. Loading is
 * deliberately defensive: a missing, empty, or malformed file yields no bounties rather than throwing,
 * so a corrupt store can never strand escrowed gold in an unreadable record.
 */
@Slf4j
public class JsonBountyRepository implements BountyRepository, AutoCloseable {

    private static final int SCHEMA_VERSION = 1;
    private static final String WORLD_STATE_DIR = "world-state";
    private static final String FILE_NAME = "bounties.json";
    private static final long IDLE_POLL_MILLIS = 25;
    private static final Object WRITE_SIGNAL = new Object();

    private final ObjectMapper objectMapper;
    private final Path dirPath;
    private final Path filePath;

    private volatile List<Bounty> snapshot = List.of();
    private final BlockingQueue<Object> writeSignals = new LinkedBlockingQueue<>();
    private final AtomicBoolean pendingWrite = new AtomicBoolean(false);
    private final AtomicReference<Thread> workerThread = new AtomicReference<>();
    private volatile boolean running = true;

    /**
     * Creates a repository rooted at {@code data}, loading existing bounties and starting the
     * write-behind worker.
     */
    public JsonBountyRepository() {
        this(Path.of("data"));
    }

    /**
     * Creates a repository rooted at the given data directory, loading existing bounties and starting
     * the write-behind worker thread.
     *
     * @param dataRoot the root data directory (e.g. {@code data})
     */
    public JsonBountyRepository(Path dataRoot) {
        this.objectMapper = JsonDataMapper.create();
        this.dirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(WORLD_STATE_DIR);
        this.filePath = dirPath.resolve(FILE_NAME);
        ensureDirectory(dirPath);
        this.snapshot = readFile();
        log.info("Loaded {} open bount{} from {}",
            snapshot.size(), snapshot.size() == 1 ? "y" : "ies", filePath);
        Thread thread = Thread.ofVirtual().name("bounty-writer").start(this::runWorker);
        workerThread.set(thread);
    }

    @Override
    public List<Bounty> findAll() {
        return snapshot;
    }

    @Override
    public void save(List<Bounty> bounties) {
        Objects.requireNonNull(bounties, "bounties is required");
        this.snapshot = List.copyOf(bounties);
        if (pendingWrite.compareAndSet(false, true)) {
            writeSignals.add(WRITE_SIGNAL);
        }
    }

    /**
     * Stops the write-behind worker thread, flushing any pending change to disk before returning so a
     * freshly reconstructed repository observes the latest state.
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

    private List<Bounty> readFile() {
        if (!Files.exists(filePath)) {
            return List.of();
        }
        BountiesFileDto dto;
        try {
            dto = objectMapper.readValue(filePath.toFile(), BountiesFileDto.class);
        } catch (IOException e) {
            log.warn("Failed to read bounty store {}; treating as empty: {}", filePath, e.getMessage());
            return List.of();
        }
        if (dto == null || dto.schemaVersion() != SCHEMA_VERSION) {
            if (dto != null && dto.schemaVersion() != SCHEMA_VERSION) {
                log.warn("Ignoring bounty store {} with unsupported schema version {}",
                    filePath, dto.schemaVersion());
            }
            return List.of();
        }
        @Nullable List<BountyDto> entries = dto.bounties();
        if (entries == null) {
            return List.of();
        }
        List<Bounty> bounties = new ArrayList<>(entries.size());
        for (BountyDto entry : entries) {
            toDomain(entry).ifPresent(bounties::add);
        }
        return List.copyOf(bounties);
    }

    private Optional<Bounty> toDomain(BountyDto dto) {
        if (dto == null || dto.backer() == null || dto.mobTemplateId() == null || dto.mobName() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Bounty(
                Username.of(dto.backer()), dto.mobTemplateId(), dto.mobName(), dto.reward(), dto.postedTick()));
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring invalid bounty entry in {}: {}", filePath, e.getMessage());
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
        List<Bounty> current = snapshot;
        try {
            if (current.isEmpty()) {
                Files.deleteIfExists(filePath);
                return;
            }
            List<BountyDto> dtos = new ArrayList<>(current.size());
            for (Bounty bounty : current) {
                dtos.add(new BountyDto(
                    bounty.backer().getValue(), bounty.mobTemplateId(), bounty.mobName(),
                    bounty.reward(), bounty.postedTick()));
            }
            BountiesFileDto file = new BountiesFileDto(SCHEMA_VERSION, dtos);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), file);
        } catch (IOException e) {
            log.error("Failed to persist bounties to {}", filePath, e);
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
