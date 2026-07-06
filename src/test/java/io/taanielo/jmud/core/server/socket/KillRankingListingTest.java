package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;

class KillRankingListingTest {

    @Test
    void sortsPlayersByKillsDescending() {
        Player alice = player("alice", 10);
        Player bob = player("bob", 25);
        Player carol = player("carol", 3);

        List<String> lines = KillRankingListing.format(List.of(alice, bob, carol));

        assertEquals("Kill ranking:", lines.get(0));
        assertTrue(lines.get(1).contains("bob"));
        assertTrue(lines.get(2).contains("alice"));
        assertTrue(lines.get(3).contains("carol"));
        assertEquals("3 players ranked.", lines.get(lines.size() - 1));
    }

    @Test
    void breaksTiesByUsername() {
        Player bob = player("bob", 5);
        Player alice = player("alice", 5);

        List<String> lines = KillRankingListing.format(List.of(bob, alice));

        assertTrue(lines.get(1).contains("alice"));
        assertTrue(lines.get(2).contains("bob"));
    }

    @Test
    void capsAtLimitAndShowsRemainderCount() {
        List<Player> players = List.of(
            player("a", 1), player("b", 2), player("c", 3), player("d", 4)
        );

        List<String> lines = KillRankingListing.format(players, 2);

        assertEquals("Kill ranking:", lines.get(0));
        assertTrue(lines.get(1).contains("d"));
        assertTrue(lines.get(2).contains("c"));
        assertEquals("  ... and 2 more.", lines.get(3));
        assertEquals("4 players ranked.", lines.get(4));
    }

    @Test
    void noPlayersStillRendersHeaderAndZeroCount() {
        List<String> lines = KillRankingListing.format(List.of());

        assertEquals(List.of("Kill ranking:", "0 players ranked."), lines);
    }

    @Test
    void footerPluralisesByCount() {
        assertEquals("0 players ranked.", KillRankingListing.footer(0));
        assertEquals("1 player ranked.", KillRankingListing.footer(1));
        assertEquals("5 players ranked.", KillRankingListing.footer(5));
    }

    private static Player player(String username, long totalKills) {
        User user = User.of(Username.of(username), Password.hash("pw", 1));
        return Player.of(user, "%hp> ").withTotalKills(totalKills);
    }
}
