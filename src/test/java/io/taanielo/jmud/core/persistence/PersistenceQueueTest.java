package io.taanielo.jmud.core.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.audit.AuditEntry;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSink;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Verifies the write-behind behaviour of {@link PersistenceQueue}: coalescing of
 * bursty saves, synchronous draining via {@link PersistenceQueue#flush}, and the
 * retry-then-count-failure path (AGENTS.md §5, §10).
 */
class PersistenceQueueTest {

    private PersistenceQueue queue;

    @AfterEach
    void tearDown() {
        if (queue != null) {
            queue.close();
        }
    }

    private static Player playerWithGold(int gold) {
        PlayerVitals vitals = new PlayerVitals(20, 20, 10, 10, 10, 10);
        User user = User.of(Username.of("sparky"), Password.hash("pw", 1000));
        return new Player(user, 1, 0, vitals, List.of(), "prompt", false, List.of(), null, null)
            .withGold(gold);
    }

    private static AuditService noOpAuditService() {
        AuditSink sink = new AuditSink() {
            @Override
            public void write(AuditEntry entry) {
            }
        };
        return new AuditService(sink, Clock.systemUTC(), () -> 0L, () -> "correlation");
    }

    @Test
    void burstOfSavesForSamePlayerCoalescesToLatestState() throws InterruptedException {
        CountDownLatch firstSaveStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstSave = new CountDownLatch(1);
        AtomicInteger callCount = new AtomicInteger();
        List<Player> saved = new CopyOnWriteArrayList<>();

        PlayerRepository repository = new PlayerRepository() {
            @Override
            public void savePlayer(Player player) throws RepositoryException {
                int callNumber = callCount.incrementAndGet();
                if (callNumber == 1) {
                    firstSaveStarted.countDown();
                    await(releaseFirstSave);
                }
                saved.add(player);
            }

            @Override
            public Optional<Player> loadPlayer(Username username) {
                return Optional.empty();
            }
        };

        queue = new PersistenceQueue(repository, noOpAuditService());

        // First save starts immediately and blocks "mid-write" so subsequent saves for
        // the same player enqueue while it is in flight.
        queue.enqueueSave(playerWithGold(0));
        assertTrue(firstSaveStarted.await(2, TimeUnit.SECONDS), "first save should have started");

        for (int i = 1; i <= 20; i++) {
            queue.enqueueSave(playerWithGold(i));
        }

        releaseFirstSave.countDown();

        assertTrue(queue.flush(Duration.ofSeconds(5)), "flush should drain the queue");

        // The in-flight first write plus one coalesced write of the latest state.
        assertEquals(2, saved.size(), "burst of saves should collapse to at most 2 writes");
        assertEquals(0, saved.get(0).getGold(), "first (in-flight) write is the initial snapshot");
        assertEquals(20, saved.get(1).getGold(), "second write carries the latest coalesced state");
    }

    @Test
    void flushWaitsForPendingWorkThenReturnsTrue() {
        List<Player> saved = new CopyOnWriteArrayList<>();
        PlayerRepository repository = new PlayerRepository() {
            @Override
            public void savePlayer(Player player) throws RepositoryException {
                saved.add(player);
            }

            @Override
            public Optional<Player> loadPlayer(Username username) {
                return Optional.empty();
            }
        };
        queue = new PersistenceQueue(repository, noOpAuditService());

        queue.enqueueSave(playerWithGold(1));

        assertTrue(queue.flush(Duration.ofSeconds(2)));
        assertEquals(1, saved.size());
        assertEquals(1, saved.get(0).getGold());
    }

    @Test
    void writeFailureRetriesOnceThenIncrementsFailureCounter() {
        AtomicInteger callCount = new AtomicInteger();
        PlayerRepository repository = new PlayerRepository() {
            @Override
            public void savePlayer(Player player) throws RepositoryException {
                callCount.incrementAndGet();
                throw new RepositoryException("simulated failure");
            }

            @Override
            public Optional<Player> loadPlayer(Username username) {
                return Optional.empty();
            }
        };
        queue = new PersistenceQueue(repository, noOpAuditService());

        queue.enqueueSave(playerWithGold(1));

        assertTrue(queue.flush(Duration.ofSeconds(3)));
        assertEquals(2, callCount.get(), "should attempt an initial save plus one retry");
        assertEquals(1, queue.getFailureCount());
    }

    @Test
    void concurrentEnqueueDuringFlushDoesNotLoseNewestSnapshot() throws InterruptedException {
        List<Player> saved = new CopyOnWriteArrayList<>();
        CountDownLatch firstSaveStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstSave = new CountDownLatch(1);
        AtomicInteger callCount = new AtomicInteger();

        PlayerRepository repository = new PlayerRepository() {
            @Override
            public void savePlayer(Player player) throws RepositoryException {
                if (callCount.incrementAndGet() == 1) {
                    firstSaveStarted.countDown();
                    await(releaseFirstSave);
                }
                saved.add(player);
            }

            @Override
            public Optional<Player> loadPlayer(Username username) {
                return Optional.empty();
            }
        };
        queue = new PersistenceQueue(repository, noOpAuditService());

        queue.enqueueSave(playerWithGold(0));
        assertTrue(firstSaveStarted.await(2, TimeUnit.SECONDS));

        Thread flushingThread = Thread.ofVirtual().start(() -> queue.flush(Duration.ofSeconds(5)));

        queue.enqueueSave(playerWithGold(1));
        queue.enqueueSave(playerWithGold(2));
        queue.enqueueSave(playerWithGold(42));

        releaseFirstSave.countDown();
        flushingThread.join(Duration.ofSeconds(5).toMillis());

        assertTrue(queue.flush(Duration.ofSeconds(5)));
        assertEquals(42, saved.get(saved.size() - 1).getGold(), "the newest snapshot must be the last one written");
        assertTrue(Set.of(2, 3).contains(saved.size()), "expected 2-3 writes, got " + saved.size());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
