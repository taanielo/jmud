package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.guild.Guild;
import io.taanielo.jmud.core.guild.GuildId;

class GuildRankingListingTest {

    @Test
    void sortsGuildsByLevelDescending() {
        // 5_000 lifetime gold -> level 4; 500 -> level 2; 0 -> level 1.
        Guild titans = guild("Titans", "bob", 5_000);
        Guild fledglings = guild("Fledglings", "alice", 0);
        Guild rising = guild("Ascendants", "carol", 500);

        List<String> lines = GuildRankingListing.format(List.of(titans, fledglings, rising));

        assertEquals("Guild ranking:", lines.get(0));
        assertTrue(lines.get(1).contains("Titans"));
        assertTrue(lines.get(2).contains("Ascendants"));
        assertTrue(lines.get(3).contains("Fledglings"));
        assertEquals("3 guilds ranked.", lines.get(lines.size() - 1));
    }

    @Test
    void breaksLevelTiesByLifetimeGoldDescending() {
        // Both guilds are level 2 (>= 500, < 2_000), so lifetime gold breaks the tie.
        Guild richer = guild("Richer", "bob", 1_500);
        Guild poorer = guild("Poorer", "alice", 700);

        List<String> lines = GuildRankingListing.format(List.of(poorer, richer));

        assertTrue(lines.get(1).contains("Richer"));
        assertTrue(lines.get(2).contains("Poorer"));
    }

    @Test
    void rendersLevelMemberCountAndLeader() {
        Guild guild = guild("Titans", "bob", 5_000);

        List<String> lines = GuildRankingListing.format(List.of(guild));

        assertTrue(lines.get(1).contains("L4"));
        assertTrue(lines.get(1).contains("1 members"));
        assertTrue(lines.get(1).contains("led by bob"));
    }

    @Test
    void capsAtLimitAndShowsRemainderCount() {
        List<Guild> guilds = List.of(
            guild("a", "a", 15_000), guild("b", "b", 5_000),
            guild("c", "c", 2_000), guild("d", "d", 500));

        List<String> lines = GuildRankingListing.format(guilds, 2);

        assertEquals("Guild ranking:", lines.get(0));
        assertTrue(lines.get(1).contains(" a "));
        assertTrue(lines.get(2).contains(" b "));
        assertEquals("  ... and 2 more.", lines.get(3));
        assertEquals("4 guilds ranked.", lines.get(4));
    }

    @Test
    void rendersWarWinsColumn() {
        Guild guild = Guild.found(GuildId.newId(), "Titans", Username.of("bob"))
            .withWarWin().withWarWin();

        List<String> lines = GuildRankingListing.format(List.of(guild));

        assertTrue(lines.get(1).contains("2 war wins"));
    }

    @Test
    void breaksLifetimeGoldTiesByWarWinsDescending() {
        // Both guilds are level 1 with 0 lifetime gold, so guild-war wins break the tie.
        Guild champions = Guild.found(GuildId.newId(), "Champions", Username.of("bob")).withWarWin();
        Guild rookies = Guild.found(GuildId.newId(), "Rookies", Username.of("alice"));

        List<String> lines = GuildRankingListing.format(List.of(rookies, champions));

        assertTrue(lines.get(1).contains("Champions"));
        assertTrue(lines.get(2).contains("Rookies"));
    }

    @Test
    void noGuildsShowsFriendlyMessage() {
        List<String> lines = GuildRankingListing.format(List.of());

        assertEquals(List.of("No guilds have been founded yet."), lines);
    }

    @Test
    void footerPluralisesByCount() {
        assertEquals("0 guilds ranked.", GuildRankingListing.footer(0));
        assertEquals("1 guild ranked.", GuildRankingListing.footer(1));
        assertEquals("5 guilds ranked.", GuildRankingListing.footer(5));
    }

    private static Guild guild(String name, String leader, int lifetimeGold) {
        Guild founded = Guild.found(GuildId.newId(), name, Username.of(leader));
        return lifetimeGold > 0 ? founded.depositTreasury(lifetimeGold) : founded;
    }
}
