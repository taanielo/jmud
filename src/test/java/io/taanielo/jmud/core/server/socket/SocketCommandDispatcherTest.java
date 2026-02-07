package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.NoOpAuditSink;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

class SocketCommandDispatcherTest {

    @Test
    void blocksCommandsWhenDead() {
        AtomicBoolean executed = new AtomicBoolean(false);
        SocketCommandRegistry registry = new SocketCommandRegistry();
        registry.register(new TestCommand("look", executed));
        AuditService auditService = new AuditService(new NoOpAuditSink(), Clock.systemUTC(), () -> 0L, () -> "test");
        SocketCommandDispatcher dispatcher = new SocketCommandDispatcher(registry, auditService);

        Player deadPlayer = new Player(
            User.of(Username.of("sparky"), Password.of("pw")),
            1,
            0,
            new PlayerVitals(5, 20, 5, 20, 5, 20),
            List.of(),
            "prompt",
            false,
            List.of(),
            null,
            null
        ).die();
        TestContext context = new TestContext(deadPlayer);

        dispatcher.dispatch(context, "look", "corr-1");

        assertFalse(executed.get());
        assertEquals("You cannot act while dead.", context.lastMessage);
    }

    @Test
    void allowsQuitWhenDead() {
        AtomicBoolean executed = new AtomicBoolean(false);
        SocketCommandRegistry registry = new SocketCommandRegistry();
        registry.register(new TestCommand("quit", executed));
        AuditService auditService = new AuditService(new NoOpAuditSink(), Clock.systemUTC(), () -> 0L, () -> "test");
        SocketCommandDispatcher dispatcher = new SocketCommandDispatcher(registry, auditService);

        Player deadPlayer = new Player(
            User.of(Username.of("sparky"), Password.of("pw")),
            1,
            0,
            new PlayerVitals(5, 20, 5, 20, 5, 20),
            List.of(),
            "prompt",
            false,
            List.of(),
            null,
            null
        ).die();
        TestContext context = new TestContext(deadPlayer);

        dispatcher.dispatch(context, "quit", "corr-2");

        assertTrue(executed.get());
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
        private String lastMessage;

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
            lastMessage = message;
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
