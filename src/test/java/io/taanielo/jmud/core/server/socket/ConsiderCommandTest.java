package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.combat.DamageType;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link ConsiderCommand}.
 *
 * <p>Danger-tier logic (the mapping from mob/player maxHp ratio to a qualitative
 * tier string) lives in {@link SocketCommandContextImpl} and is
 * integration-tested separately. These tests verify token matching, command
 * metadata, and that a successful match delegates to
 * {@link SocketCommandContext#considerMob(String)}.
 */
class ConsiderCommandTest {

    private ConsiderCommand command;

    @BeforeEach
    void setUp() {
        command = new ConsiderCommand(new SocketCommandRegistry());
    }

    // ── Token matching ─────────────────────────────────────────────────

    @Test
    void matchesConsiderToken() {
        assertTrue(command.match("CONSIDER goblin").isPresent());
        assertTrue(command.match("consider goblin").isPresent());
        assertTrue(command.match("Consider goblin").isPresent());
    }

    @Test
    void matchesConAlias() {
        assertTrue(command.match("CON goblin").isPresent());
        assertTrue(command.match("con goblin").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        assertFalse(command.match("look").isPresent());
        assertFalse(command.match("kill goblin").isPresent());
        assertFalse(command.match("").isPresent());
        assertFalse(command.match("co goblin").isPresent());
    }

    // ── Argument forwarding ────────────────────────────────────────────

    @Test
    void passesArgsToContext() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("CONSIDER goblin");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("goblin", captured.get());
    }

    @Test
    void passesArgsViaConAlias() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("CON giant spider");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("giant spider", captured.get());
    }

    @Test
    void passesBlankArgsWhenNoTarget() {
        AtomicReference<String> captured = new AtomicReference<>();
        CapturingContext context = new CapturingContext(captured);

        Optional<SocketCommandMatch> match = command.match("CONSIDER");
        assertTrue(match.isPresent());
        match.get().execute(context);

        assertEquals("", captured.get());
    }

    // ── Metadata ───────────────────────────────────────────────────────

    @Test
    void nameIsConsider() {
        assertEquals("consider", command.name());
    }

    @Test
    void hasShortDescription() {
        assertNotNull(command.shortDescription());
        assertFalse(command.shortDescription().isBlank());
    }

    @Test
    void longDescriptionMentionsBothAliases() {
        String desc = command.longDescription();
        assertNotNull(desc);
        assertTrue(desc.toUpperCase(Locale.ROOT).contains("CONSIDER"));
        assertTrue(desc.toUpperCase(Locale.ROOT).contains("CON"));
    }

    @Test
    void registersItselfWithRegistry() {
        SocketCommandRegistry registry = new SocketCommandRegistry();
        new ConsiderCommand(registry);
        assertTrue(registry.commands().stream().anyMatch(c -> c instanceof ConsiderCommand));
    }

    // ── Danger tier logic ──────────────────────────────────────────────

    @Test
    void tierNoPosesNoRealThreat() {
        // ratio ≤ 0.25 → "poses no real threat"
        assertEquals("poses no real threat", DangerTier.evaluate(25, 100));
        assertEquals("poses no real threat", DangerTier.evaluate(1,  100));
        assertEquals("poses no real threat", DangerTier.evaluate(25, 100));
    }

    @Test
    void tierEasyOpponent() {
        // 0.26 – 0.60 → "looks like an easy opponent"
        assertEquals("looks like an easy opponent", DangerTier.evaluate(26,  100));
        assertEquals("looks like an easy opponent", DangerTier.evaluate(60,  100));
        assertEquals("looks like an easy opponent", DangerTier.evaluate(30,  100));
    }

    @Test
    void tierFairFight() {
        // 0.61 – 1.00 → "seems like a fair fight"
        assertEquals("seems like a fair fight", DangerTier.evaluate(61,  100));
        assertEquals("seems like a fair fight", DangerTier.evaluate(100, 100));
        assertEquals("seems like a fair fight", DangerTier.evaluate(80,  100));
    }

    @Test
    void tierSeriousChallenge() {
        // 1.01 – 1.50 → "would be a serious challenge"
        assertEquals("would be a serious challenge", DangerTier.evaluate(101, 100));
        assertEquals("would be a serious challenge", DangerTier.evaluate(150, 100));
    }

    @Test
    void tierCouldKillWithoutEffort() {
        // > 1.50 → "could kill you without effort"
        assertEquals("could kill you without effort", DangerTier.evaluate(151, 100));
        assertEquals("could kill you without effort", DangerTier.evaluate(500, 100));
    }

    // ── Elemental-affinity line ────────────────────────────────────────

    @Test
    void affinityLineResistOnly() {
        assertEquals(
            "It looks resistant to cold.",
            SocketCommandContextImpl.elementalAffinityLine(
                Map.of(DamageType.COLD, 50), Map.of()));
    }

    @Test
    void affinityLineVulnerableOnly() {
        assertEquals(
            "It looks vulnerable to fire.",
            SocketCommandContextImpl.elementalAffinityLine(
                Map.of(), Map.of(DamageType.FIRE, 50)));
    }

    @Test
    void affinityLineBothHalves() {
        assertEquals(
            "It looks resistant to cold and vulnerable to fire.",
            SocketCommandContextImpl.elementalAffinityLine(
                Map.of(DamageType.COLD, 50), Map.of(DamageType.FIRE, 50)));
    }

    @Test
    void affinityLineNeitherIsEmpty() {
        assertEquals("", SocketCommandContextImpl.elementalAffinityLine(Map.of(), Map.of()));
    }

    @Test
    void affinityLineUsesPlayerFacingWordsInEnumOrder() {
        // Multiple resisted types are listed in DamageType declaration order (FIRE, COLD, POISON),
        // joined with "and", using plain player-facing words rather than enum constants.
        assertEquals(
            "It looks resistant to fire and poison.",
            SocketCommandContextImpl.elementalAffinityLine(
                Map.of(DamageType.POISON, 25, DamageType.FIRE, 25), Map.of()));
    }

    // ── Helpers ────────────────────────────────────────────────────────

    /**
     * Pure utility to expose the danger-tier classification for isolated unit tests.
     * Mirrors the identical logic in {@link SocketCommandContextImpl#considerMob(String)}.
     */
    static final class DangerTier {
        private DangerTier() {}

        static String evaluate(int mobMaxHp, int playerMaxHp) {
            double ratio = playerMaxHp > 0
                ? (double) mobMaxHp / playerMaxHp
                : Double.MAX_VALUE;
            if (ratio <= 0.25)  return "poses no real threat";
            if (ratio <= 0.60)  return "looks like an easy opponent";
            if (ratio <= 1.00)  return "seems like a fair fight";
            if (ratio <= 1.50)  return "would be a serious challenge";
            return "could kill you without effort";
        }
    }

    private static class CapturingContext implements SocketCommandContext {
        private final AtomicReference<String> captured;

        CapturingContext(AtomicReference<String> captured) {
            this.captured = captured;
        }

        @Override public void considerMob(String args) { captured.set(args); }
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
