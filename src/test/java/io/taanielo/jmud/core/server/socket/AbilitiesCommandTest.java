package io.taanielo.jmud.core.server.socket;

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
 * Unit tests for {@link AbilitiesCommand}.
 */
class AbilitiesCommandTest {

    // ── token matching ─────────────────────────────────────────────────

    @Test
    void matchesAbilitiesToken() {
        AbilitiesCommand cmd = new AbilitiesCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("ABILITIES").isPresent());
        assertTrue(cmd.match("abilities").isPresent());
    }

    @Test
    void matchesAbAlias() {
        AbilitiesCommand cmd = new AbilitiesCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("AB").isPresent());
        assertTrue(cmd.match("ab").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        AbilitiesCommand cmd = new AbilitiesCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("SCORE").isPresent());
        assertFalse(cmd.match("LOOK").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    // ── delegation ─────────────────────────────────────────────────────

    @Test
    void executionDelegatesToSendAbilities() {
        CapturingContext context = new CapturingContext("Alice");
        AbilitiesCommand cmd = new AbilitiesCommand(new SocketCommandRegistry());

        cmd.match("ABILITIES").get().execute(context);

        assertTrue(context.sendAbilitiesCalled,
            "ABILITIES command must delegate to context.sendAbilities()");
    }

    @Test
    void abAliasDelegatesToSendAbilities() {
        CapturingContext context = new CapturingContext("Alice");
        AbilitiesCommand cmd = new AbilitiesCommand(new SocketCommandRegistry());

        cmd.match("AB").get().execute(context);

        assertTrue(context.sendAbilitiesCalled,
            "AB alias must delegate to context.sendAbilities()");
    }

    // ── metadata ───────────────────────────────────────────────────────

    @Test
    void shortDescriptionMentionsAlias() {
        AbilitiesCommand cmd = new AbilitiesCommand(new SocketCommandRegistry());
        assertTrue(cmd.shortDescription().contains("AB"),
            "shortDescription should mention AB alias");
    }

    @Test
    void longDescriptionContainsUsage() {
        AbilitiesCommand cmd = new AbilitiesCommand(new SocketCommandRegistry());
        String desc = cmd.longDescription();
        assertTrue(desc.contains("ABILITIES"), "longDescription should mention ABILITIES");
        assertTrue(desc.contains("Usage"), "longDescription should contain usage instructions");
    }

    // ── helpers ────────────────────────────────────────────────────────

    private static Player stubPlayer(String name) {
        User user = User.of(Username.of(name), Password.hash("secret"));
        return Player.of(user, "%h/%H hp>");
    }

    private static class CapturingContext implements SocketCommandContext {
        boolean sendAbilitiesCalled = false;
        final List<String> lines = new ArrayList<>();
        String promptMessage = "";
        private final Player player;

        CapturingContext(String playerName) {
            this.player = stubPlayer(playerName);
        }

        @Override public void sendAbilities() { sendAbilitiesCalled = true; }
        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
        @Override public void useAbility(String a) {}
        @Override public void updateAnsi(String a) {}
        @Override public void writeLineWithPrompt(String m) { promptMessage = m; }
        @Override public void writeLineSafe(String m) { lines.add(m); }
        @Override public void sendPrompt() {}
        @Override public void sendToUsername(Username u, String m) {}
        @Override public void sendToRoom(Player s, Player t, String m) {}
        @Override public void sendToRoom(Player s, String m) {}
        @Override public Optional<Player> resolveTarget(Player s, String i) { return Optional.empty(); }
        @Override public void executeAttack(String a) {}
        @Override public void getItem(String a) {}
        @Override public void dropItem(String a) {}
        @Override public void quaffItem(String a) {}
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
