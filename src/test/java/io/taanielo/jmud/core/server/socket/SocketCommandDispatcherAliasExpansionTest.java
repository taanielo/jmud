package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.audit.AuditService;
import io.taanielo.jmud.core.audit.NoOpAuditSink;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests verifying that {@link SocketCommandDispatcher} expands a player's aliases
 * before command lookup.
 */
class SocketCommandDispatcherAliasExpansionTest {

    @Test
    void expandsAliasBeforeMatching() {
        CapturingCommand kill = new CapturingCommand("kill");
        SocketCommandRegistry registry = new SocketCommandRegistry();
        registry.register(kill);
        SocketCommandDispatcher dispatcher = dispatcher(registry);

        Player player = stubPlayer().defineAlias("k", "kill rat");
        TestContext context = new TestContext(player);

        dispatcher.dispatch(context, "k", "corr-1");

        assertEquals("kill rat", kill.lastCapturedInput);
    }

    @Test
    void appendsTrailingArgsAfterExpansion() {
        CapturingCommand kill = new CapturingCommand("kill");
        SocketCommandRegistry registry = new SocketCommandRegistry();
        registry.register(kill);
        SocketCommandDispatcher dispatcher = dispatcher(registry);

        Player player = stubPlayer().defineAlias("k", "kill");
        TestContext context = new TestContext(player);

        dispatcher.dispatch(context, "k rat", "corr-2");

        assertEquals("kill rat", kill.lastCapturedInput);
    }

    @Test
    void doesNotExpandWhenNoAliasMatches() {
        CapturingCommand look = new CapturingCommand("look");
        SocketCommandRegistry registry = new SocketCommandRegistry();
        registry.register(look);
        SocketCommandDispatcher dispatcher = dispatcher(registry);

        Player player = stubPlayer().defineAlias("k", "kill rat");
        TestContext context = new TestContext(player);

        dispatcher.dispatch(context, "look", "corr-3");

        assertEquals("look", look.lastCapturedInput);
    }

    @Test
    void doesNotExpandWhenNoPlayerIsAuthenticated() {
        CapturingCommand kill = new CapturingCommand("kill");
        SocketCommandRegistry registry = new SocketCommandRegistry();
        registry.register(kill);
        SocketCommandDispatcher dispatcher = dispatcher(registry);

        TestContext context = new TestContext(null);

        dispatcher.dispatch(context, "k", "corr-4");

        assertNull(kill.lastCapturedInput);
    }

    private static SocketCommandDispatcher dispatcher(SocketCommandRegistry registry) {
        AuditService auditService = new AuditService(new NoOpAuditSink(), Clock.systemUTC(), () -> 0L, () -> "test");
        return new SocketCommandDispatcher(registry, auditService);
    }

    private static Player stubPlayer() {
        User user = User.of(Username.of("sparky"), Password.hash("pw", 1000));
        return Player.of(user, "%hp> ");
    }

    /** Matches any input starting with its configured token and records the full input. */
    private static class CapturingCommand implements SocketCommandHandler {
        private final String token;
        private String lastCapturedInput;

        private CapturingCommand(String token) {
            this.token = token;
        }

        @Override
        public String name() {
            return token;
        }

        @Override
        public Optional<SocketCommandMatch> match(String input) {
            String firstWord = input.split("\\s+", 2)[0];
            if (!firstWord.equalsIgnoreCase(token)) {
                return Optional.empty();
            }
            return Optional.of(new SocketCommandMatch(this, context -> lastCapturedInput = input));
        }
    }

    private static class TestContext implements SocketCommandContext {
        private final Player player;

        private TestContext(Player player) {
            this.player = player;
        }

        @Override public boolean isAuthenticated() { return player != null; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String targetInput) {}
        @Override public void sendMove(Direction direction) {}
        @Override public void useAbility(String args) {}
        @Override public void updateAnsi(String args) {}
        @Override public void writeLineWithPrompt(String message) {}
        @Override public void writeLineSafe(String message) {}
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username username, String message) {}
        @Override public void sendToRoom(Player source, Player target, String message) {}
        @Override public void sendToRoom(Player source, String message) {}
        @Override public Optional<Player> resolveTarget(Player source, String input) { return Optional.empty(); }
        @Override public void executeAttack(String args) {}
        @Override public void getItem(String args) {}
        @Override public void dropItem(String args) {}
        @Override public void quaffItem(String args) {}
        @Override public void readItem(String args) {}
        @Override public void writeItem(String args) {}
        @Override public void equipItem(String args) {}
        @Override public void unequipItem(String args) {}
        @Override public void killMob(String args) {}
        @Override public void fleeCombat() {}
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message message) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}
