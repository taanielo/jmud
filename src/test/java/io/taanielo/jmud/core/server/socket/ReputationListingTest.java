package io.taanielo.jmud.core.server.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.faction.Faction;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.faction.FactionRepository;
import io.taanielo.jmud.core.faction.FactionRepositoryException;
import io.taanielo.jmud.core.faction.PlayerReputation;
import io.taanielo.jmud.core.faction.ReputationService;

/**
 * Unit tests for {@link ReputationListing}.
 */
class ReputationListingTest {

    private static final FactionId BANDITS = FactionId.of("bandits");
    private static final FactionId MERCHANTS = FactionId.of("merchants");

    private ReputationService service() throws FactionRepositoryException {
        Faction bandits = new Faction(BANDITS, "the Bandit Brotherhood", "Cutthroats.", -10, 0, 0.02);
        Faction merchants = new Faction(MERCHANTS, "the Merchant Guild", "Traders.", 5, -5, 0.01);
        return new ReputationService(new StubFactionRepository(List.of(bandits, merchants)));
    }

    @Test
    void listsFactionsWithTrackedStandingSortedByName() throws FactionRepositoryException {
        PlayerReputation reputation = PlayerReputation.empty()
            .adjust(BANDITS, -25)
            .adjust(MERCHANTS, 12);

        List<String> lines = ReputationListing.format(reputation, service());

        assertEquals("Faction reputation:", lines.get(0));
        // Sorted by display name: "the Bandit Brotherhood" before "the Merchant Guild".
        assertTrue(lines.get(1).contains("the Bandit Brotherhood"), lines.get(1));
        assertTrue(lines.get(1).contains("-25"), lines.get(1));
        assertTrue(lines.get(1).contains("Hostile"), lines.get(1));
        assertTrue(lines.get(2).contains("the Merchant Guild"), lines.get(2));
        assertTrue(lines.get(2).contains("+12"), lines.get(2));
        assertTrue(lines.get(2).contains("Friendly"), lines.get(2));
        assertEquals(3, lines.size());
    }

    @Test
    void omitsZeroStandings() throws FactionRepositoryException {
        PlayerReputation reputation = PlayerReputation.empty()
            .adjust(BANDITS, -10)
            .adjust(BANDITS, 10);

        List<String> lines = ReputationListing.format(reputation, service());

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("not yet made a name"), lines.get(0));
    }

    @Test
    void reportsNoStandingsForFreshPlayer() throws FactionRepositoryException {
        List<String> lines = ReputationListing.format(PlayerReputation.empty(), service());

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("not yet made a name"), lines.get(0));
    }

    private record StubFactionRepository(List<Faction> factions) implements FactionRepository {
        @Override
        public List<Faction> findAll() {
            return factions;
        }

        @Override
        public Optional<Faction> findById(FactionId factionId) {
            return factions.stream().filter(f -> f.id().equals(factionId)).findFirst();
        }
    }
}
