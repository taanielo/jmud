package io.taanielo.jmud.core.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.Executors;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.audit.AuditEntry;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSink;
import io.taanielo.jmud.core.authentication.AuthenticationLimiter;
import io.taanielo.jmud.core.authentication.AuthenticationPolicy;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.config.GameConfig;
import io.taanielo.jmud.core.persistence.PersistenceQueue;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.tick.FixedRateTickScheduler;
import io.taanielo.jmud.core.tick.TickRegistry;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Verifies that Micrometer meters are recorded correctly by the instrumented
 * infrastructure classes. Uses a {@link SimpleMeterRegistry} so no JMX
 * connection is required.
 */
class GameMetricsInstrumentationTest {

    @TempDir
    Path tempDir;

    private PersistenceQueue persistenceQueue;

    @AfterEach
    void tearDown() {
        if (persistenceQueue != null) {
            persistenceQueue.close();
        }
    }

    // ── FixedRateTickScheduler ────────────────────────────────────────────────

    @Test
    void tickTimerRecordsOneSampleAfterOneTick() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TickRegistry tickRegistry = new TickRegistry();
        FixedRateTickScheduler scheduler = new FixedRateTickScheduler(
            tickRegistry,
            1000,
            Executors.newSingleThreadScheduledExecutor(),
            registry
        );

        scheduler.runTickForTest();

        Timer timer = registry.find("jmud.tick.duration").timer();
        assertTrue(timer != null, "Timer must be registered");
        assertEquals(1, timer.count(), "Timer should have recorded exactly one tick");
    }

    @Test
    void overrunCounterIncrementedWhenTickExceedsBudget() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TickRegistry tickRegistry = new TickRegistry();
        tickRegistry.register(() -> {
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        FixedRateTickScheduler scheduler = new FixedRateTickScheduler(
            tickRegistry,
            20,
            Executors.newSingleThreadScheduledExecutor(),
            registry
        );

        scheduler.runTickForTest();

        Counter overrunCounter = registry.find("jmud.tick.overruns").counter();
        assertTrue(overrunCounter != null, "Overrun counter must be registered");
        assertEquals(1.0, overrunCounter.count(), 0.001, "One overrun should have been counted");
    }

    // ── PersistenceQueue ──────────────────────────────────────────────────────

    @Test
    void saveFailureCounterIncrementedOnForcedSaveFailure() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        persistenceQueue = new PersistenceQueue(new AlwaysFailingPlayerRepository(), noOpAuditService(), registry);

        Player player = newPlayer("sparky");
        persistenceQueue.enqueueSave(player);
        persistenceQueue.flush(java.time.Duration.ofSeconds(5));

        Counter failureCounter = registry.find("jmud.persistence.saves")
            .tag("result", "failure")
            .counter();
        assertTrue(failureCounter != null, "Save-failure counter must be registered");
        assertEquals(1.0, failureCounter.count(), 0.001, "One save failure should have been counted");
    }

    @Test
    void saveSuccessCounterIncrementedOnSuccessfulSave() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        persistenceQueue = new PersistenceQueue(new NoOpPlayerRepository(), noOpAuditService(), registry);

        Player player = newPlayer("hero");
        persistenceQueue.enqueueSave(player);
        persistenceQueue.flush(java.time.Duration.ofSeconds(5));

        Counter successCounter = registry.find("jmud.persistence.saves")
            .tag("result", "success")
            .counter();
        assertTrue(successCounter != null, "Save-success counter must be registered");
        assertEquals(1.0, successCounter.count(), 0.001, "One successful save should have been counted");
    }

    // ── AuthenticationLimiter ─────────────────────────────────────────────────

    @Test
    void authFailureCounterIncrementedOnRecordFailure() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuthenticationLimiter limiter = new AuthenticationLimiter(
            AuthenticationPolicy.fromConfig(GameConfig.load()),
            Clock.systemUTC(),
            registry
        );

        limiter.recordFailure("client:alice");
        limiter.recordFailure("client:alice");

        Counter failuresCounter = registry.find("jmud.auth.failures").counter();
        assertTrue(failuresCounter != null, "Auth failure counter must be registered");
        assertEquals(2.0, failuresCounter.count(), 0.001, "Two failures should have been counted");
    }

    @Test
    void authLockoutCounterIncrementedWhenMaxAttemptsReached() throws IOException {
        // Write a config with max_attempts=2 so we can trigger a lockout quickly
        Path configPath = tempDir.resolve("auth.properties");
        Files.writeString(configPath, String.join("\n",
            "jmud.auth.allow_new_users=true",
            "jmud.auth.max_attempts=2",
            "jmud.auth.attempt_window_seconds=10",
            "jmud.auth.lockout_seconds=30",
            "jmud.auth.pbkdf2.iterations=1000"
        ));
        AuthenticationPolicy policy = AuthenticationPolicy.fromConfig(GameConfig.load(configPath));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuthenticationLimiter limiter = new AuthenticationLimiter(policy, Clock.systemUTC(), registry);

        limiter.recordFailure("client:bob");
        limiter.recordFailure("client:bob"); // triggers lockout

        Counter lockoutsCounter = registry.find("jmud.auth.lockouts").counter();
        assertTrue(lockoutsCounter != null, "Lockout counter must be registered");
        assertEquals(1.0, lockoutsCounter.count(), 0.001, "One lockout should have been counted");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static AuditService noOpAuditService() {
        AuditSink sink = new AuditSink() {
            @Override
            public void write(AuditEntry entry) {
            }
        };
        return new AuditService(sink, Clock.systemUTC(), () -> 0L, () -> "test");
    }

    private static Player newPlayer(String username) {
        User user = User.of(Username.of(username), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    private static final class AlwaysFailingPlayerRepository implements PlayerRepository {
        @Override
        public void savePlayer(Player player) throws RepositoryException {
            throw new RepositoryException("disk full (simulated)");
        }

        @Override
        public Optional<Player> loadPlayer(Username username) {
            return Optional.empty();
        }
    }

    private static final class NoOpPlayerRepository implements PlayerRepository {
        @Override
        public void savePlayer(Player player) {
            // success
        }

        @Override
        public Optional<Player> loadPlayer(Username username) {
            return Optional.empty();
        }
    }
}
