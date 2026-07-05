package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.NoOpAuditSink;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Verifies that {@link SocketCommandDispatcher} correctly propagates the
 * per-command correlation id into Log4j2's {@link ThreadContext} (MDC) during
 * execution and removes it in the finally block so the tick thread is never
 * left with a stale value.
 */
class SocketCommandDispatcherMdcTest {

    @AfterEach
    void clearThreadContext() {
        // Guard: ensure the MDC is clean between tests regardless of test outcome
        ThreadContext.clearAll();
    }

    @Test
    void threadContextContainsCorrelationIdDuringExecution() {
        AtomicReference<String> capturedId = new AtomicReference<>();
        SocketCommandRegistry registry = new SocketCommandRegistry();
        registry.register(new CapturingCommand("look", capturedId));
        AuditService auditService = new AuditService(new NoOpAuditSink(), Clock.systemUTC(), () -> 0L, () -> "test");
        SocketCommandDispatcher dispatcher = new SocketCommandDispatcher(registry, auditService);

        Player player = new Player(
            User.of(Username.of("tester"), Password.hash("pw", 1000)),
            1,
            0,
            new PlayerVitals(5, 20, 5, 20, 5, 20),
            List.of(),
            "prompt",
            false,
            List.of(),
            null,
            null
        );

        dispatcher.dispatch(new NoOpContext(player), "look", "corr-mdc-1");

        assertEquals("corr-mdc-1", capturedId.get(),
            "ThreadContext must contain the correlationId during command execution");
    }

    @Test
    void threadContextIsClearedAfterDispatch() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        AuditService auditService = new AuditService(new NoOpAuditSink(), Clock.systemUTC(), () -> 0L, () -> "test");
        SocketCommandDispatcher dispatcher = new SocketCommandDispatcher(registry, auditService);

        Player player = new Player(
            User.of(Username.of("tester"), Password.hash("pw", 1000)),
            1,
            0,
            new PlayerVitals(5, 20, 5, 20, 5, 20),
            List.of(),
            "prompt",
            false,
            List.of(),
            null,
            null
        );

        dispatcher.dispatch(new NoOpContext(player), "unknown-cmd", "corr-mdc-2");

        assertNull(ThreadContext.get(SocketCommandDispatcher.MDC_CORRELATION_ID),
            "ThreadContext must be cleared after dispatch completes");
    }

    @Test
    void threadContextIsClearedEvenWhenCommandThrows() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        registry.register(new ThrowingCommand("boom"));
        AuditService auditService = new AuditService(new NoOpAuditSink(), Clock.systemUTC(), () -> 0L, () -> "test");
        SocketCommandDispatcher dispatcher = new SocketCommandDispatcher(registry, auditService);

        Player player = new Player(
            User.of(Username.of("tester"), Password.hash("pw", 1000)),
            1,
            0,
            new PlayerVitals(5, 20, 5, 20, 5, 20),
            List.of(),
            "prompt",
            false,
            List.of(),
            null,
            null
        );

        try {
            dispatcher.dispatch(new NoOpContext(player), "boom", "corr-mdc-3");
        } catch (RuntimeException ignored) {
            // expected
        }

        assertNull(ThreadContext.get(SocketCommandDispatcher.MDC_CORRELATION_ID),
            "ThreadContext must be cleared even when command execution throws");
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    /** Records the MDC correlationId at the moment its command executes. */
    private static class CapturingCommand implements SocketCommandHandler {
        private final String name;
        private final AtomicReference<String> capturedId;

        private CapturingCommand(String name, AtomicReference<String> capturedId) {
            this.name = name;
            this.capturedId = capturedId;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Optional<SocketCommandMatch> match(String input) {
            if (!input.equalsIgnoreCase(name)) {
                return Optional.empty();
            }
            return Optional.of(new SocketCommandMatch(this, context ->
                capturedId.set(ThreadContext.get(SocketCommandDispatcher.MDC_CORRELATION_ID))
            ));
        }
    }

    /** Always throws during execution to test finally-block cleanup. */
    private static class ThrowingCommand implements SocketCommandHandler {
        private final String name;

        private ThrowingCommand(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Optional<SocketCommandMatch> match(String input) {
            if (!input.equalsIgnoreCase(name)) {
                return Optional.empty();
            }
            return Optional.of(new SocketCommandMatch(this, context -> {
                throw new RuntimeException("simulated command failure");
            }));
        }
    }

    private static class NoOpContext implements SocketCommandContext {
        private final Player player;

        private NoOpContext(Player player) {
            this.player = player;
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }

        @Override
        public Player getPlayer() {
            return player;
        }

        @Override
        public List<Client> clients() {
            return List.of();
        }

        @Override
        public List<Username> onlinePlayerNames() {
            return List.of();
        }

        @Override
        public void sendLook() {
        }

        @Override
        public void sendLookAt(String targetInput) {
        }

        @Override
        public void sendMove(Direction direction) {
        }

        @Override
        public void useAbility(String args) {
        }

        @Override
        public void updateAnsi(String args) {
        }

        @Override
        public void writeLineWithPrompt(String message) {
        }

        @Override
        public void writeLineSafe(String message) {
        }

        @Override
        public void sendPrompt() {
        }

        @Override
        public void sendToUsername(Username username, String message) {
        }

        @Override
        public void sendToRoom(Player source, Player target, String message) {
        }

        @Override
        public void sendToRoom(Player source, String message) {
        }

        @Override
        public Optional<Player> resolveTarget(Player source, String input) {
            return Optional.empty();
        }

        @Override
        public void executeAttack(String args) {
        }

        @Override
        public void getItem(String args) {
        }

        @Override
        public void dropItem(String args) {
        }

        @Override
        public void quaffItem(String args) {
        }

        @Override
        public void equipItem(String args) {
        }

        @Override
        public void unequipItem(String args) {
        }

        @Override
        public void killMob(String args) {
        }

        @Override
        public void fleeCombat() {
        }

        @Override
        public void sendInventory() {
        }

        @Override
        public void sendEquipment() {
        }

        @Override
        public void sendMessage(io.taanielo.jmud.core.messaging.Message message) {
        }

        @Override
        public void close() {
        }

        @Override
        public void run() {
        }
    }
}
