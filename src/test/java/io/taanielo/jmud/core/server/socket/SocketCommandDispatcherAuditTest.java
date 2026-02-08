package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.audit.AuditEntry;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.AuditSink;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

class SocketCommandDispatcherAuditTest {

    @Test
    void logsCommandLifecycleWithCorrelationId() {
        AtomicBoolean executed = new AtomicBoolean(false);
        SocketCommandRegistry registry = new SocketCommandRegistry();
        registry.register(new TestCommand("look", executed));
        RecordingAuditSink sink = new RecordingAuditSink();
        AuditService auditService = new AuditService(sink, Clock.systemUTC(), () -> 7L, () -> "corr-1");
        SocketCommandDispatcher dispatcher = new SocketCommandDispatcher(registry, auditService);

        Player player = new Player(
            User.of(Username.of("sparky"), Password.hash("pw", 1000)),
            1,
            0,
            PlayerVitals.defaults(),
            List.of(),
            "prompt",
            false,
            List.of(),
            null,
            null
        );
        TestContext context = new TestContext(player);

        dispatcher.dispatch(context, "look", "corr-123");

        assertTrue(executed.get());
        assertEquals(2, sink.entries.size());
        assertEquals("command.received", sink.entries.get(0).eventType());
        assertEquals("command.execute", sink.entries.get(1).eventType());
        assertEquals("corr-123", sink.entries.get(0).correlationId());
        assertEquals("corr-123", sink.entries.get(1).correlationId());
    }

    private static class RecordingAuditSink implements AuditSink {
        private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

        @Override
        public void write(AuditEntry entry) {
            entries.add(entry);
        }
    }

    private static class TestCommand implements SocketCommandHandler {
        private final String name;
        private final AtomicBoolean executed;

        private TestCommand(String name, AtomicBoolean executed) {
            this.name = name;
            this.executed = executed;
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
            return Optional.of(new SocketCommandMatch(this, context -> executed.set(true)));
        }
    }

    private static class TestContext implements SocketCommandContext {
        private final Player player;

        private TestContext(Player player) {
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
