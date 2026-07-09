package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link TalkCommand}: verifies {@code TALK <npc>} routes the name to
 * {@link SocketCommandContext#talk(String)} and shows usage when empty.
 */
class TalkCommandTest {

    @Test
    void talkRoutesNpcNameArgument() {
        TalkCommand cmd = new TalkCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("TALK borin the blacksmith").orElseThrow().execute(context);

        assertEquals("borin the blacksmith", context.talkTarget);
    }

    @Test
    void talkWithoutArgumentShowsUsage() {
        TalkCommand cmd = new TalkCommand(new SocketCommandRegistry());
        CapturingContext context = new CapturingContext();

        cmd.match("TALK").orElseThrow().execute(context);

        assertTrue(context.promptMessage.contains("Usage"));
        assertNull(context.talkTarget);
    }

    @Test
    void talkDoesNotMatchOtherTokens() {
        TalkCommand cmd = new TalkCommand(new SocketCommandRegistry());

        assertFalse(cmd.match("TELL bob hi").isPresent());
        assertFalse(cmd.match("TRACK goblin").isPresent());
    }

    private static final class CapturingContext implements SocketCommandContext {
        String promptMessage = "";
        String talkTarget;

        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return null; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { promptMessage = m; }
        @Override public void writeLineSafe(String m) {}
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username u, String m) {}
        @Override public void sendToRoom(Player s, Player t, String m) {}
        @Override public void sendToRoom(Player s, String m) {}
        @Override public Optional<Player> resolveTarget(Player s, String i) { return Optional.empty(); }
        @Override public void executeAttack(String a) {}
        @Override public void getItem(String a) {}
        @Override public void dropItem(String a) {}
        @Override public void talk(String a) { this.talkTarget = a; }
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
