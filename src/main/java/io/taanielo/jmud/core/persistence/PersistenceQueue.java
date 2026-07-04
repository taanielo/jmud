package io.taanielo.jmud.core.persistence;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import lombok.extern.slf4j.Slf4j;

import io.taanielo.jmud.core.audit.AuditEvent;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSubject;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Moves {@link Player} persistence off the tick thread with a write-behind queue.
 *
 * <p>Game code calls {@link #enqueueSave(Player)} to hand off an immutable snapshot;
 * a single dedicated virtual thread performs the actual (blocking) file write, so the
 * tick thread that drains player command queues never touches disk (AGENTS.md §5).
 *
 * <p>Saves are coalesced per username: only the latest enqueued snapshot for a given
 * player is kept, so a burst of saves for the same player (e.g. repeated XP gains in
 * a single combat) collapses into at most a couple of actual writes. {@link #flush}
 * provides a synchronous drain for call sites (QUIT, shutdown) that must guarantee
 * pending writes have completed before proceeding.
 */
@Slf4j
public class PersistenceQueue implements AutoCloseable {

    private static final Duration RETRY_BACKOFF = Duration.ofMillis(200);
    private static final long IDLE_POLL_MILLIS = 25;

    private final PlayerRepository playerRepository;
    private final AuditService auditService;

    /** Latest snapshot pending write for each dirty username. */
    private final ConcurrentHashMap<Username, Player> pending = new ConcurrentHashMap<>();
    /** Usernames currently queued for the worker to pick up; guards against duplicate queue entries. */
    private final Set<Username> queued = ConcurrentHashMap.newKeySet();
    private final BlockingQueue<Username> dirtyUsernames = new LinkedBlockingQueue<>();

    private final AtomicLong failureCount = new AtomicLong();
    /** Number of usernames currently being processed (dequeued but not yet saved/retried). */
    private final AtomicLong inFlight = new AtomicLong();
    private final AtomicReference<@Nullable Thread> workerThread = new AtomicReference<>();
    private volatile boolean running = true;

    /**
     * Creates a persistence queue backed by the given repository, starting its worker thread.
     *
     * @param playerRepository the repository used to perform the actual writes
     * @param auditService audit sink used to record save failures
     */
    public PersistenceQueue(PlayerRepository playerRepository, AuditService auditService) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "Player repository is required");
        this.auditService = Objects.requireNonNull(auditService, "Audit service is required");
        Thread thread = Thread.ofVirtual().name("persistence-queue-writer").start(this::runWorker);
        workerThread.set(thread);
    }

    /**
     * Enqueues a player snapshot for a future write-behind save.
     *
     * <p>The player is defensively snapshotted via {@link Player#snapshotForPersistence()}
     * so later tick-thread mutation of the caller's {@code player} instance cannot be
     * observed by the writer thread. If a save for the same username is already
     * pending, it is replaced by this newer snapshot (coalescing).
     *
     * @param player the player to save; must not be {@code null}
     */
    public void enqueueSave(Player player) {
        Objects.requireNonNull(player, "Player is required");
        Username username = player.getUsername();
        Player snapshot = player.snapshotForPersistence();
        pending.put(username, snapshot);
        if (queued.add(username)) {
            dirtyUsernames.add(username);
        }
    }

    /**
     * Blocks the calling thread until all currently pending and in-flight saves have
     * completed, or the timeout elapses.
     *
     * @param timeout the maximum time to wait
     * @return {@code true} if the queue drained before the timeout, {@code false} otherwise
     */
    public boolean flush(Duration timeout) {
        Objects.requireNonNull(timeout, "Timeout is required");
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (!isIdle()) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return false;
            }
            long sleepMillis = Math.min(IDLE_POLL_MILLIS, Math.max(1, remainingNanos / 1_000_000));
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the number of save attempts that ultimately failed (after the single
     * retry). Exposed for future metrics/monitoring; not currently alerted on.
     *
     * @return the cumulative count of failed saves since this queue was created
     */
    public long getFailureCount() {
        return failureCount.get();
    }

    /**
     * Stops the worker thread. Callers that need pending saves to be durable first
     * must call {@link #flush(Duration)} before closing.
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

    private boolean isIdle() {
        return pending.isEmpty() && dirtyUsernames.isEmpty() && inFlight.get() == 0;
    }

    private void runWorker() {
        while (running) {
            Username username;
            try {
                username = dirtyUsernames.poll(IDLE_POLL_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!running) {
                    return;
                }
                continue;
            }
            if (username == null) {
                continue;
            }
            // Mark this username as "in flight" before touching pending/queued so a
            // concurrent flush() cannot observe a false "idle" window between the
            // blocking-queue take (above) and the save (including its retry) completing.
            inFlight.incrementAndGet();
            try {
                queued.remove(username);
                Player snapshot = pending.remove(username);
                if (snapshot != null) {
                    saveWithRetry(snapshot);
                }
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }

    private void saveWithRetry(Player snapshot) {
        try {
            playerRepository.savePlayer(snapshot);
            return;
        } catch (RepositoryException e) {
            log.warn("Failed to save player {} (write-behind); retrying once", snapshot.getUsername(), e);
        }
        try {
            Thread.sleep(RETRY_BACKOFF.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            playerRepository.savePlayer(snapshot);
        } catch (RepositoryException e) {
            failureCount.incrementAndGet();
            log.error("Failed to save player {} (write-behind) after retry", snapshot.getUsername(), e);
            auditService.emit(new AuditEvent(
                "player.save.failed",
                AuditSubject.player(snapshot.getUsername()),
                null,
                null,
                "failure",
                auditService.newCorrelationId(),
                Map.of()
            ));
        }
    }
}
