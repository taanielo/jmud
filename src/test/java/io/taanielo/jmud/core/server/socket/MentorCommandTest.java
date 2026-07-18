package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link MentorCommand} parsing and dispatch.
 */
class MentorCommandTest {

    @Test
    void matchesMentorToken() {
        MentorCommand cmd = new MentorCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("MENTOR Bob").isPresent());
        assertTrue(cmd.match("mentor").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        MentorCommand cmd = new MentorCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("MENTORING Bob").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    @Test
    void passesProposalTargetThrough() {
        MentorCommand cmd = new MentorCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("MENTOR Bob").orElseThrow().execute(context);

        assertEquals("Bob", context.mentorArgs);
    }

    @Test
    void passesSubCommandThrough() {
        MentorCommand cmd = new MentorCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("MENTOR ACCEPT").orElseThrow().execute(context);

        assertEquals("ACCEPT", context.mentorArgs);
    }

    @Test
    void passesBlankArgumentsForStatus() {
        MentorCommand cmd = new MentorCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("MENTOR").orElseThrow().execute(context);

        assertEquals("", context.mentorArgs);
    }

    private static final class CapturingContext implements SocketCommandContext {
        String mentorArgs;
        private final Player player = Player.of(
            User.of(Username.of("Alice"), Password.hash("secret")), "%h/%H hp>");

        @Override public void executeMentor(String args) {
            this.mentorArgs = args;
        }

        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return new ArrayList<>(); }
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
