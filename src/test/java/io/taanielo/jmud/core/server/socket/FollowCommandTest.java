package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link FollowCommand} token matching and delegation to
 * {@link SocketCommandContext#executeFollow(String)}. The auto-follow orchestration itself lives in
 * {@link SocketCommandContextImpl} and is covered by the party/relationship tests and the smoke test.
 */
class FollowCommandTest {

    private FollowCommand command;

    @BeforeEach
    void setUp() {
        command = new FollowCommand(new SocketCommandRegistry());
    }

    // ── Token matching ─────────────────────────────────────────────────

    @Test
    void matchesFollowToken() {
        assertTrue(command.match("FOLLOW Alice").isPresent());
        assertTrue(command.match("follow alice").isPresent());
        assertTrue(command.match("FOLLOW").isPresent());
    }

    @Test
    void matchesUnfollowToken() {
        assertTrue(command.match("UNFOLLOW").isPresent());
        assertTrue(command.match("unfollow").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("look").isPresent());
        assertFalse(command.match("party").isPresent());
        assertFalse(command.match("").isPresent());
    }

    // ── Argument forwarding ────────────────────────────────────────────

    @Test
    void passesTargetToContext() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("FOLLOW Alice");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("Alice", captured.get());
    }

    @Test
    void passesBlankArgsWhenNoTarget() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        command.match("FOLLOW").get().execute(context);

        assertEquals("", captured.get());
    }

    @Test
    void followOffPassesOff() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        command.match("FOLLOW OFF").get().execute(context);

        assertEquals("OFF", captured.get());
    }

    @Test
    void unfollowMapsToOff() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        command.match("UNFOLLOW").get().execute(context);

        assertEquals("OFF", captured.get());
    }

    // ── Metadata ───────────────────────────────────────────────────────

    @Test
    void nameIsFollow() {
        assertEquals("follow", command.name());
    }

    @Test
    void hasShortDescription() {
        assertNotNull(command.shortDescription());
        assertFalse(command.shortDescription().isBlank());
    }

    @Test
    void longDescriptionMentionsOff() {
        String desc = command.longDescription().toUpperCase(Locale.ROOT);
        assertTrue(desc.contains("FOLLOW"));
        assertTrue(desc.contains("OFF"));
    }

    @Test
    void registersItselfWithRegistry() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        new FollowCommand(registry);
        assertTrue(registry.commands().stream().anyMatch(c -> c instanceof FollowCommand));
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static class CapturingContext implements SocketCommandContext {
        private final AtomicReference<String> captured;

        CapturingContext(AtomicReference<String> captured) {
            this.captured = captured;
        }

        @Override public void executeFollow(String args) { captured.set(args); }
        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return null; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) {}
        @Override public void writeLineSafe(String m) {}
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username u, String m) {}
        @Override public void sendToRoom(Player s, Player t, String m) {}
        @Override public void sendToRoom(Player s, String m) {}
        @Override public Optional<Player> resolveTarget(Player s, String i) { return Optional.empty(); }
        @Override public void executeAttack(String a) {}
        @Override public void getItem(String a) {}
        @Override public void dropItem(String a) {}
        @Override public void quaffItem(String a) {}
        @Override public void readItem(String a) {}
        @Override public void writeItem(String a) {}
        @Override public void equipItem(String a) {}
        @Override public void unequipItem(String a) {}
        @Override public void killMob(String a) {}
        @Override public void fleeCombat() {}
        @Override public void sendInventory() {}
        @Override public void sendEquipment() {}
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}
