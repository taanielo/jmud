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
 * Unit tests for {@link CastCommand}.
 */
class CastCommandTest {

    // ── token matching ──────────────────────────────────────────────────

    @Test
    void matchesCastToken() {
        CastCommand cmd = new CastCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("CAST fireball").isPresent());
        assertTrue(cmd.match("cast fireball").isPresent());
    }

    @Test
    void matchesCastWithNoArgs() {
        CastCommand cmd = new CastCommand(new SocketCommandRegistry());
        assertTrue(cmd.match("CAST").isPresent());
    }

    @Test
    void doesNotMatchUseOrOtherTokens() {
        CastCommand cmd = new CastCommand(new SocketCommandRegistry());
        assertFalse(cmd.match("USE fireball").isPresent());
        assertFalse(cmd.match("SCORE").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    // ── command name / metadata ─────────────────────────────────────────

    @Test
    void nameIsCast() {
        CastCommand cmd = new CastCommand(new SocketCommandRegistry());
        assertEquals("cast", cmd.name());
    }

    @Test
    void shortDescriptionMentionsCast() {
        CastCommand cmd = new CastCommand(new SocketCommandRegistry());
        String desc = cmd.shortDescription();
        assertFalse(desc.isBlank(), "shortDescription must not be blank");
        assertTrue(desc.toLowerCase().contains("cast") || desc.toLowerCase().contains("spell"),
            "shortDescription should mention cast or spell");
    }

    @Test
    void longDescriptionContainsUsage() {
        CastCommand cmd = new CastCommand(new SocketCommandRegistry());
        String desc = cmd.longDescription();
        assertTrue(desc.contains("CAST"), "longDescription must contain CAST");
        assertTrue(desc.contains("Usage"), "longDescription must contain usage instructions");
    }

    @Test
    void longDescriptionMentionsSpellOnlyRestriction() {
        CastCommand cmd = new CastCommand(new SocketCommandRegistry());
        String desc = cmd.longDescription();
        assertTrue(desc.toLowerCase().contains("spell"),
            "longDescription must explain spell-only restriction");
    }

    // ── delegation ──────────────────────────────────────────────────────

    @Test
    void executionDelegatesToCastSpell() {
        CapturingContext context = new CapturingContext("Alice");
        CastCommand cmd = new CastCommand(new SocketCommandRegistry());

        cmd.match("CAST fireball goblin").get().execute(context);

        assertTrue(context.castSpellCalled,
            "CAST command must delegate to context.castSpell()");
        assertEquals("fireball goblin", context.castSpellArgs,
            "args passed to castSpell must strip the CAST token");
    }

    @Test
    void executionDelegatesToCastSpellWithNoArgs() {
        CapturingContext context = new CapturingContext("Alice");
        CastCommand cmd = new CastCommand(new SocketCommandRegistry());

        cmd.match("CAST").get().execute(context);

        assertTrue(context.castSpellCalled,
            "CAST with no args must still delegate to context.castSpell()");
        assertEquals("", context.castSpellArgs);
    }

    // ── use vs cast separation ──────────────────────────────────────────

    @Test
    void useCommandDoesNotDelegateToCastSpell() {
        CapturingContext context = new CapturingContext("Alice");
        AbilityCommand useCmd = new AbilityCommand(new SocketCommandRegistry());

        useCmd.match("USE bash").get().execute(context);

        assertFalse(context.castSpellCalled,
            "USE command must not delegate to castSpell");
        assertTrue(context.useAbilityCalled,
            "USE command must delegate to useAbility");
    }

    @Test
    void castCommandDoesNotDelegateToUseAbility() {
        CapturingContext context = new CapturingContext("Alice");
        CastCommand cmd = new CastCommand(new SocketCommandRegistry());

        cmd.match("CAST fireball").get().execute(context);

        assertFalse(context.useAbilityCalled,
            "CAST command must not delegate to useAbility");
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static Player stubPlayer(String name) {
        User user = User.of(Username.of(name), Password.hash("secret"));
        return Player.of(user, "%h/%H hp>");
    }

    private static class CapturingContext implements SocketCommandContext {
        boolean castSpellCalled = false;
        boolean useAbilityCalled = false;
        String castSpellArgs = null;
        final List<String> lines = new ArrayList<>();
        String promptMessage = "";
        private final Player player;

        CapturingContext(String playerName) {
            this.player = stubPlayer(playerName);
        }

        @Override public void castSpell(String args) { castSpellCalled = true; castSpellArgs = args; }
        @Override public void useAbility(String args) { useAbilityCalled = true; }
        @Override public boolean isAuthenticated() { return true; }
        @Override public Player getPlayer() { return player; }
        @Override public List<Client> clients() { return List.of(); }
        @Override public List<Username> onlinePlayerNames() { return List.of(); }
        @Override public void sendLook() {}
        @Override public void sendLookAt(String t) {}
        @Override public void sendMove(Direction d) {}
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
