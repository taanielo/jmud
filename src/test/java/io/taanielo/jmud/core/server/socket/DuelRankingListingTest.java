package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;

class DuelRankingListingTest {

    @Test
    void sortsPlayersByWinsDescending() {
        Player alice = player("alice", 4, 1);
        Player bob = player("bob", 9, 3);
        Player carol = player("carol", 1, 5);

        List<String> lines = DuelRankingListing.format(List.of(alice, bob, carol));

        assertEquals("Duel ranking:", lines.get(0));
        assertTrue(lines.get(1).contains("bob"));
        assertTrue(lines.get(2).contains("alice"));
        assertTrue(lines.get(3).contains("carol"));
        assertEquals("3 duelists ranked.", lines.get(lines.size() - 1));
    }

    @Test
    void breaksWinTiesByFewerLossesThenUsername() {
        Player bob = player("bob", 5, 4);
        Player alice = player("alice", 5, 4);
        Player carol = player("carol", 5, 2);

        List<String> lines = DuelRankingListing.format(List.of(bob, alice, carol));

        // carol has the same wins but fewest losses -> ranks first.
        assertTrue(lines.get(1).contains("carol"));
        // alice and bob tie on wins and losses -> username breaks the tie.
        assertTrue(lines.get(2).contains("alice"));
        assertTrue(lines.get(3).contains("bob"));
    }

    @Test
    void omitsPlayersWithNoRecordedDuels() {
        Player fighter = player("fighter", 2, 1);
        Player pacifist = player("pacifist", 0, 0);

        List<String> lines = DuelRankingListing.format(List.of(fighter, pacifist));

        assertTrue(lines.stream().anyMatch(line -> line.contains("fighter")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("pacifist")));
        assertEquals("1 duelist ranked.", lines.get(lines.size() - 1));
    }

    @Test
    void rendersWinLossAndWinPercentage() {
        Player alice = player("alice", 3, 1);

        List<String> lines = DuelRankingListing.format(List.of(alice));

        assertTrue(lines.get(1).contains("3W / 1L"));
        assertTrue(lines.get(1).contains("75% win"));
    }

    @Test
    void capsAtLimitAndShowsRemainderCount() {
        List<Player> players = List.of(
            player("a", 1, 0), player("b", 2, 0), player("c", 3, 0), player("d", 4, 0)
        );

        List<String> lines = DuelRankingListing.format(players, 2);

        assertEquals("Duel ranking:", lines.get(0));
        assertTrue(lines.get(1).contains("d"));
        assertTrue(lines.get(2).contains("c"));
        assertEquals("  ... and 2 more.", lines.get(3));
        assertEquals("4 duelists ranked.", lines.get(4));
    }

    @Test
    void noDuelistsStillRendersHeaderAndZeroCount() {
        List<String> lines = DuelRankingListing.format(List.of());

        assertEquals(List.of("Duel ranking:", "0 duelists ranked."), lines);
    }

    @Test
    void winPercentRoundsAndHandlesEmptyRecord() {
        assertEquals(0, DuelRankingListing.winPercent(0, 0));
        assertEquals(100, DuelRankingListing.winPercent(5, 0));
        assertEquals(50, DuelRankingListing.winPercent(1, 1));
        assertEquals(67, DuelRankingListing.winPercent(2, 1));
    }

    @Test
    void footerPluralisesByCount() {
        assertEquals("0 duelists ranked.", DuelRankingListing.footer(0));
        assertEquals("1 duelist ranked.", DuelRankingListing.footer(1));
        assertEquals("5 duelists ranked.", DuelRankingListing.footer(5));
    }

    private static Player player(String username, int wins, int losses) {
        User user = User.of(Username.of(username), Password.hash("pw", 1));
        return Player.of(user, "%hp> ").withDuelWins(wins).withDuelLosses(losses);
    }
}
