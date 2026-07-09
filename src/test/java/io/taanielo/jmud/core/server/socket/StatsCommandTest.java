package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.tick.TickMetrics;
import io.taanielo.jmud.core.tick.TickMetricsService;
import io.taanielo.jmud.core.tick.TickStatsSummary;
import io.taanielo.jmud.core.world.Direction;

/**
 * Unit tests for {@link StatsCommand}: token matching, wizard gating, and output formatting.
 */
class StatsCommandTest {

    private static TickMetricsService serviceWithOneTick() {
        TickMetricsService service = new TickMetricsService(10);
        service.recordTick(new TickMetrics(
            1L,
            TimeUnit.MILLISECONDS.toNanos(5),
            Map.of("PlayerCommandQueue", TimeUnit.MILLISECONDS.toNanos(4)),
            false));
        return service;
    }

    @Test
    void matchesStatsToken() {
        StatsCommand cmd = new StatsCommand(new SocketCommandRegistry(), new TickMetricsService(10), wizardPolicy("Al"));
        assertTrue(cmd.match("STATS").isPresent());
        assertTrue(cmd.match("stats").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        StatsCommand cmd = new StatsCommand(new SocketCommandRegistry(), new TickMetricsService(10), wizardPolicy("Al"));
        assertFalse(cmd.match("SCORE").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    @Test
    void nonWizardIsDenied() {
        CapturingContext context = new CapturingContext("Bob");
        StatsCommand cmd = new StatsCommand(new SocketCommandRegistry(), serviceWithOneTick(), wizardPolicy("Alice"));

        cmd.match("STATS").get().execute(context);

        assertTrue(context.promptMessage.toLowerCase().contains("denied"),
            "non-wizard should be denied");
        assertTrue(context.lines.isEmpty(), "no stats table should be shown to a non-wizard");
    }

    @Test
    void wizardSeesTickHealthTable() {
        CapturingContext context = new CapturingContext("Alice");
        StatsCommand cmd = new StatsCommand(new SocketCommandRegistry(), serviceWithOneTick(), wizardPolicy("Alice"));

        cmd.match("STATS").get().execute(context);

        assertTrue(context.lines.stream().anyMatch(l -> l.contains("Tick Health")),
            "wizard should see the tick health header");
        assertTrue(context.lines.stream().anyMatch(l -> l.contains("PlayerCommandQueue")),
            "wizard should see the slowest tickable line");
    }

    @Test
    void formatRendersEmptySummary() {
        List<String> lines = StatsCommand.format(TickStatsSummary.empty());
        assertTrue(lines.stream().anyMatch(l -> l.contains("Total ticks: 0")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("none recorded")));
    }

    @Test
    void formatRendersSlowestTickableWithInvocations() {
        TickStatsSummary summary = new TickStatsSummary(
            1234L, 100, 5_200_000.0, 127_400_000L, "PlayerCommandQueue", 543_000_000L, 1234L, 0L);

        List<String> lines = StatsCommand.format(summary);

        assertTrue(lines.stream().anyMatch(l -> l.equals("Total ticks: 1234")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("Avg duration: 5.2 ms")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("Max: 127.4 ms")));
        assertTrue(lines.stream().anyMatch(l ->
            l.contains("PlayerCommandQueue (543.0 ms / 1234 invocations)")));
    }

    private static WizardPolicy wizardPolicy(String... names) {
        Set<Username> wizards = java.util.Arrays.stream(names).map(Username::of)
            .collect(java.util.stream.Collectors.toSet());
        return new WizardPolicy(wizards);
    }

    private static Player stubPlayer(String name) {
        User user = User.of(Username.of(name), Password.hash("secret"));
        return Player.of(user, "%h/%H hp>");
    }

    private static final class CapturingContext implements SocketCommandContext {
        final List<String> lines = new ArrayList<>();
        String promptMessage = "";
        private final Player player;

        CapturingContext(String playerName) {
            this.player = stubPlayer(playerName);
        }

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
