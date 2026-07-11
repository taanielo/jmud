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
import io.taanielo.jmud.core.messaging.Message;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerRepository;
import io.taanielo.jmud.core.server.Client;
import io.taanielo.jmud.core.world.Direction;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Unit tests for {@link RankCommand}, covering matching and output via
 * {@link SocketCommandContext} (mirroring {@code WhoListingTest} / {@code ScoreCommandTest}).
 */
class RankCommandTest {

    @Test
    void matchesRankToken() {
        RankCommand cmd = new RankCommand(new SocketCommandRegistry(), new StubPlayerRepository(List.of()));
        assertTrue(cmd.match("RANK").isPresent());
        assertTrue(cmd.match("rank").isPresent());
    }

    @Test
    void doesNotMatchOtherTokens() {
        RankCommand cmd = new RankCommand(new SocketCommandRegistry(), new StubPlayerRepository(List.of()));
        assertFalse(cmd.match("WHO").isPresent());
        assertFalse(cmd.match("").isPresent());
    }

    @Test
    void listsAllPersistedPlayersSortedByKills() {
        Player alice = player("alice", 10);
        Player bob = player("bob", 25);
        RankCommand cmd = new RankCommand(new SocketCommandRegistry(), new StubPlayerRepository(List.of(alice, bob)));
        CapturingContext context = new CapturingContext(alice, true);

        cmd.match("RANK").orElseThrow().execute(context);

        assertTrue(context.lines.get(0).equals("Kill ranking:"));
        assertTrue(context.lines.stream().anyMatch(l -> l.contains("bob")));
        assertTrue(context.lines.stream().anyMatch(l -> l.contains("alice")));
    }

    @Test
    void rankDuelsListsPlayersByDuelWins() {
        Player alice = duelist("alice", 4, 1);
        Player bob = duelist("bob", 9, 2);
        Player pacifist = player("pacifist", 5);
        RankCommand cmd = new RankCommand(
            new SocketCommandRegistry(), new StubPlayerRepository(List.of(alice, bob, pacifist)));
        CapturingContext context = new CapturingContext(alice, true);

        cmd.match("RANK DUELS").orElseThrow().execute(context);

        assertTrue(context.lines.get(0).equals("Duel ranking:"));
        assertTrue(context.lines.stream().anyMatch(l -> l.contains("bob")));
        assertTrue(context.lines.stream().anyMatch(l -> l.contains("alice")));
        // A player with no recorded duels is omitted from the duel ranking.
        assertFalse(context.lines.stream().anyMatch(l -> l.contains("pacifist")));
    }

    @Test
    void unauthenticatedPlayerGetsErrorMessage() {
        RankCommand cmd = new RankCommand(new SocketCommandRegistry(), new StubPlayerRepository(List.of()));
        CapturingContext context = new CapturingContext(null, false);

        cmd.match("RANK").orElseThrow().execute(context);

        assertTrue(context.lines.isEmpty(), "No safe-written lines expected for unauthed player");
        assertTrue(context.promptMessage != null, "Prompt error message expected");
    }

    private static Player player(String username, long totalKills) {
        User user = User.of(Username.of(username), Password.hash("pw", 1));
        return Player.of(user, "%hp> ").withTotalKills(totalKills);
    }

    private static Player duelist(String username, int wins, int losses) {
        User user = User.of(Username.of(username), Password.hash("pw", 1));
        return Player.of(user, "%hp> ").withDuelWins(wins).withDuelLosses(losses);
    }

    private static class StubPlayerRepository implements PlayerRepository {
        private final List<Player> players;

        StubPlayerRepository(List<Player> players) {
            this.players = players;
        }

        @Override
        public void savePlayer(Player player) throws RepositoryException {
        }

        @Override
        public Optional<Player> loadPlayer(Username username) {
            return Optional.empty();
        }

        @Override
        public List<Player> findAll() {
            return players;
        }
    }

    private static class CapturingContext implements SocketCommandContext {
        private final Player player;
        private final boolean authenticated;
        final List<String> lines = new ArrayList<>();
        String promptMessage;

        CapturingContext(Player player, boolean authenticated) {
            this.player = player;
            this.authenticated = authenticated;
        }

        @Override public boolean isAuthenticated() { return authenticated; }
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
        @Override public void sendMessage(Message m) {}
        @Override public void close() {}
        @Override public void run() {}
    }
}
