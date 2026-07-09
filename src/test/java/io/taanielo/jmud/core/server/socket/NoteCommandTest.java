package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link NoteCommand} token matching, metadata, and argument delegation.
 */
class NoteCommandTest {

    @Test
    void matchesNoteToken() {
        NoteCommand command = new NoteCommand(new SocketCommandRegistry());
        assertTrue(command.match("NOTE POST hello").isPresent());
        assertTrue(command.match("note delete 1").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        NoteCommand command = new NoteCommand(new SocketCommandRegistry());
        assertFalse(command.match("board").isPresent());
        assertFalse(command.match("").isPresent());
        assertFalse(command.match("notebook").isPresent());
    }

    @Test
    void passesSubCommandArgsThrough() {
        NoteCommand command = new NoteCommand(new SocketCommandRegistry());
        TrackingContext context = new TrackingContext();
        command.match("NOTE POST hello all").get().execute(context);
        assertEquals("POST hello all", context.noteArgs);
    }

    @Test
    void hasNameAndDescriptions() {
        NoteCommand command = new NoteCommand(new SocketCommandRegistry());
        assertTrue(command.name().equalsIgnoreCase("note"));
        assertFalse(command.shortDescription().isBlank());
        assertFalse(command.longDescription().isBlank());
    }

    private static class TrackingContext implements SocketCommandContext {
        String noteArgs = null;

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
        @Override public void manageNote(String a) { noteArgs = a; }
        @Override public void sendMessage(io.taanielo.jmud.core.messaging.Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}
