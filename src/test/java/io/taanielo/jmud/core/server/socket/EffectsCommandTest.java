package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link EffectsCommand}.
 */
class EffectsCommandTest {

    private EffectsCommand command;

    @BeforeEach
    void setUp() {
        command = new EffectsCommand(new SocketCommandRegistry());
    }

    @Test
    void matchesEffectsToken() {
        assertTrue(command.match("EFFECTS").isPresent());
        assertTrue(command.match("effects").isPresent());
    }

    @Test
    void matchesAffectsAlias() {
        assertTrue(command.match("AFFECTS").isPresent());
        assertTrue(command.match("affects").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("abilities").isPresent());
        assertFalse(command.match("").isPresent());
    }

    @Test
    void invokesSendEffects() {
        AtomicBoolean invoked = new AtomicBoolean(false);
        CapturingContext context = new CapturingContext(invoked);

        Optional<SocketCommandMatch> match = command.match("EFFECTS");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertTrue(invoked.get());
    }

    @Test
    void hasShortDescription() {
        assertNotNull(command.shortDescription());
        assertFalse(command.shortDescription().isBlank());
    }

    @Test
    void longDescriptionMentionsAliases() {
        String desc = command.longDescription();
        assertNotNull(desc);
        assertTrue(desc.contains("EFFECTS"));
        assertTrue(desc.contains("AFFECTS"));
    }

    @Test
    void nameIsEffects() {
        assertEquals("effects", command.name());
    }

    private static class CapturingContext implements SocketCommandContext {
        private final AtomicBoolean invoked;

        CapturingContext(AtomicBoolean invoked) {
            this.invoked = invoked;
        }

        @Override public void sendEffects() { invoked.set(true); }
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
