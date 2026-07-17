package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * Unit tests for {@link PartyCommand} token matching and its delegation to
 * {@link SocketCommandContext#executeParty(String)} (management sub-commands) and
 * {@link SocketCommandContext#partyChat(String)} (the {@code PTELL} chat verb). The delivery of a
 * party-chat line to online members across rooms lives in {@link SocketCommandContextImpl} and is
 * covered by the end-to-end smoke test.
 */
class PartyCommandTest {

    private PartyCommand command;

    @BeforeEach
    void setUp() {
        command = new PartyCommand(new SocketCommandRegistry());
    }

    // ── Token matching ─────────────────────────────────────────────────

    @Test
    void matchesPartyToken() {
        assertTrue(command.match("PARTY").isPresent());
        assertTrue(command.match("party form").isPresent());
        assertTrue(command.match("PARTY INVITE Alice").isPresent());
    }

    @Test
    void matchesPtellToken() {
        assertTrue(command.match("PTELL hello team").isPresent());
        assertTrue(command.match("ptell hello team").isPresent());
        assertTrue(command.match("PTELL").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("look").isPresent());
        assertFalse(command.match("guild").isPresent());
        assertFalse(command.match("").isPresent());
    }

    // ── Routing: PTELL is party chat ───────────────────────────────────

    @Test
    void ptellForwardsMessageToPartyChat() {
        Captured captured = new Captured();
        CapturingContext context = new CapturingContext(captured);

        command.match("PTELL regroup at the shrine").get().execute(context);

        assertEquals("regroup at the shrine", captured.chatMessage.get());
        assertNull(captured.partyArgs.get(), "PTELL must not route to PARTY sub-command handling");
    }

    @Test
    void ptellWithoutMessageForwardsBlankToPartyChat() {
        Captured captured = new Captured();
        CapturingContext context = new CapturingContext(captured);

        command.match("PTELL").get().execute(context);

        assertEquals("", captured.chatMessage.get());
    }

    // ── Routing: PARTY management still goes to executeParty ───────────

    @Test
    void partySubCommandForwardsToExecuteParty() {
        Captured captured = new Captured();
        CapturingContext context = new CapturingContext(captured);

        command.match("PARTY INVITE Alice").get().execute(context);

        assertEquals("INVITE Alice", captured.partyArgs.get());
        assertNull(captured.chatMessage.get(), "PARTY sub-command must not route to party chat");
    }

    @Test
    void barePartyForwardsBlankToExecuteParty() {
        Captured captured = new Captured();
        CapturingContext context = new CapturingContext(captured);

        command.match("PARTY").get().execute(context);

        assertEquals("", captured.partyArgs.get());
    }

    // ── Metadata ───────────────────────────────────────────────────────

    @Test
    void nameIsParty() {
        assertEquals("party", command.name());
    }

    @Test
    void shortDescriptionMentionsPtellAlias() {
        String desc = command.shortDescription().toUpperCase(Locale.ROOT);
        assertTrue(desc.contains("PTELL"));
    }

    @Test
    void longDescriptionDocumentsChatUsage() {
        String desc = command.longDescription().toUpperCase(Locale.ROOT);
        assertTrue(desc.contains("PTELL"));
        assertTrue(desc.contains("PARTY <MESSAGE>"));
    }

    @Test
    void registersItselfWithRegistry() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        new PartyCommand(registry);
        assertTrue(registry.commands().stream().anyMatch(c -> c instanceof PartyCommand));
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static final class Captured {
        private final AtomicReference<String> partyArgs = new AtomicReference<>();
        private final AtomicReference<String> chatMessage = new AtomicReference<>();
    }

    private static class CapturingContext implements SocketCommandContext {
        private final Captured captured;

        CapturingContext(Captured captured) {
            this.captured = captured;
        }

        @Override public void executeParty(String args) { captured.partyArgs.set(args); }
        @Override public void partyChat(String message) { captured.chatMessage.set(message); }
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
