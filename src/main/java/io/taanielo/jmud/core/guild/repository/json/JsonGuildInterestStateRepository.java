package io.taanielo.jmud.core.guild.repository.json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.guild.GuildInterestStateRepository;

/**
 * Persists the world-scoped guild-interest accrual counter as a single
 * {@code data/world-state/guild-interest-state.json} file (issue #800).
 *
 * <p>The single counter is eagerly loaded at construction and mirrored into an in-memory value. Writes
 * are handed to a single dedicated write-behind virtual thread, so the tick thread that bumps the counter
 * on each in-game day never blocks on disk (AGENTS.md §5). Because the counter changes at most once per
 * in-game day, the tiny file is simply rewritten on each change and bursts coalesce into a single write.
 *
 * <p>Loading is deliberately defensive: a missing, empty, or malformed file yields a zero counter rather
 * than throwing, so a corrupt store can never fabricate accrual — at worst it forfeits at most one
 * interest period of progress.
 */
@Slf4j
public class JsonGuildInterestStateRepository implements GuildInterestStateRepository, AutoCloseable {

    private static final int SCHEMA_VERSION = 1;
    private static final String WORLD_STATE_DIR = "world-state";
    private static final String FILE_NAME = "guild-interest-state.json";
    private static final long IDLE_POLL_MILLIS = 25;
    private static final Object WRITE_SIGNAL = new Object();

    private final ObjectMapper objectMapper;
    private final Path dirPath;
    private final Path filePath;

    /** Authoritative in-memory value of what has been persisted (plus a not-yet-flushed change). */
    private final AtomicLong gameDaysElapsed = new AtomicLong();
    private final BlockingQueue<Object> writeSignals = new LinkedBlockingQueue<>();
    private final AtomicBoolean pendingWrite = new AtomicBoolean(false);
    private final AtomicReference<@Nullable Thread> workerThread = new AtomicReference<>();
    private volatile boolean running = true;

    /**
     * Creates a repository rooted at {@code data}, loading the existing counter and starting the
     * write-behind worker.
     */
    public JsonGuildInterestStateRepository() {
        this(Path.of("data"));
    }

    /**
     * Creates a repository rooted at the given data directory, loading the existing counter and starting
     * the write-behind worker thread.
     *
     * @param dataRoot the root data directory (e.g. {@code data})
     */
    public JsonGuildInterestStateRepository(Path dataRoot) {
        this.objectMapper = JsonDataMapper.create();
        this.dirPath = Objects.requireNonNull(dataRoot, "Data root is required").resolve(WORLD_STATE_DIR);
        this.filePath = dirPath.resolve(FILE_NAME);
        ensureDirectory(dirPath);
        Thread thread =
            Thread.ofVirtual().name("guild-interest-state-writer").start(this::runWorker);
        workerThread.set(thread);
    }

    @Override
    public long loadGameDaysElapsed() {
        long loaded = readFile();
        gameDaysElapsed.set(loaded);
        log.info("Loaded guild-interest accrual counter {} from {}", loaded, filePath);
        return loaded;
    }

    @Override
    public void saveGameDaysElapsed(long value) {
        gameDaysElapsed.set(Math.max(0, value));
        if (pendingWrite.compareAndSet(false, true)) {
            writeSignals.add(WRITE_SIGNAL);
        }
    }

    /**
     * Stops the write-behind worker thread, flushing any pending change to disk before returning so a
     * freshly reconstructed repository observes the latest counter.
     */
    @Override
    public void close() {
        running = false;
        @Nullable Thread thread = workerThread.get();
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

    private long readFile() {
        if (!Files.exists(filePath)) {
            return 0L;
        }
        @Nullable GuildInterestStateDto dto;
        try {
            dto = objectMapper.readValue(filePath.toFile(), GuildInterestStateDto.class);
        } catch (IOException e) {
            log.warn("Failed to read guild-interest state {}; treating as zero: {}",
                filePath, e.getMessage());
            return 0L;
        }
        if (dto == null || dto.schemaVersion() != SCHEMA_VERSION) {
            if (dto != null) {
                log.warn("Ignoring guild-interest state {} with unsupported schema version {}",
                    filePath, dto.schemaVersion());
            }
            return 0L;
        }
        return Math.max(0, dto.gameDaysElapsed());
    }

    private void runWorker() {
        while (running) {
            @Nullable Object signal;
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
        GuildInterestStateDto dto = new GuildInterestStateDto(SCHEMA_VERSION, gameDaysElapsed.get());
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), dto);
        } catch (IOException e) {
            log.error("Failed to persist guild-interest state to {}", filePath, e);
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
