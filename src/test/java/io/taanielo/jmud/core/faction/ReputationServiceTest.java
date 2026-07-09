package io.taanielo.jmud.core.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.player.Player;

class ReputationServiceTest {

    private static final FactionId BANDITS = FactionId.of("bandits");
    private static final FactionId UNKNOWN = FactionId.of("unknown");

    private static final Faction BANDIT_FACTION = new Faction(
        BANDITS, "the Bandit Brotherhood", "Cutthroats.", -10, 0, 0.02);

    private ReputationService service() throws FactionRepositoryException {
        return new ReputationService(new StubFactionRepository(List.of(BANDIT_FACTION)));
    }

    private Player player() {
        User user = User.of(Username.of("hero"), Password.hash("pw", 1));
        return Player.of(user, "%hp> ");
    }

    @Test
    void recordKill_appliesFactionDeltaToKiller() throws FactionRepositoryException {
        Player killer = service().recordKill(player(), BANDITS);

        assertEquals(-10, killer.reputation().standing(BANDITS));
    }

    @Test
    void recordKill_unknownFaction_leavesPlayerUnchanged() throws FactionRepositoryException {
        Player original = player();
        Player after = service().recordKill(original, UNKNOWN);

        assertSame(original, after);
    }

    @Test
    void isHostile_whenStandingBelowThreshold() throws FactionRepositoryException {
        ReputationService service = service();
        PlayerReputation hostile = PlayerReputation.empty().adjust(BANDITS, -1);
        PlayerReputation neutral = PlayerReputation.empty();

        assertTrue(service.isHostile(hostile, BANDITS));
        assertFalse(service.isHostile(neutral, BANDITS),
            "A neutral player (standing 0, threshold 0) is not hostile");
    }

    @Test
    void isHostile_unknownFaction_isNeverHostile() throws FactionRepositoryException {
        PlayerReputation any = PlayerReputation.empty().adjust(UNKNOWN, -100);
        assertFalse(service().isHostile(any, UNKNOWN));
    }

    @Test
    void buyPrice_cheaperForFriendlyStanding_dearerForHostile() throws FactionRepositoryException {
        ReputationService service = service();
        Player friendly = player().withReputation(PlayerReputation.empty().adjust(BANDITS, 10));
        Player hostile = player().withReputation(PlayerReputation.empty().adjust(BANDITS, -10));

        // 100 * (1 - 10*0.02) = 80 for friendly; 100 * (1 + 10*0.02) = 120 for hostile.
        assertEquals(80, service.buyPrice(100, friendly, BANDITS));
        assertEquals(120, service.buyPrice(100, hostile, BANDITS));
    }

    @Test
    void sellValue_betterForFriendlyStanding() throws FactionRepositoryException {
        ReputationService service = service();
        Player friendly = player().withReputation(PlayerReputation.empty().adjust(BANDITS, 10));

        // 50 * (1 + 10*0.02) = 60 for a friendly seller.
        assertEquals(60, service.sellValue(50, friendly, BANDITS));
    }

    @Test
    void price_isUnchangedForNullOrUnknownFaction() throws FactionRepositoryException {
        ReputationService service = service();
        Player hostile = player().withReputation(PlayerReputation.empty().adjust(BANDITS, -10));

        assertEquals(100, service.buyPrice(100, hostile, null));
        assertEquals(100, service.buyPrice(100, hostile, UNKNOWN));
    }

    @Test
    void findFaction_returnsDefinitionOrEmpty() throws FactionRepositoryException {
        ReputationService service = service();
        assertEquals(Optional.of(BANDIT_FACTION), service.findFaction(BANDITS));
        assertTrue(service.findFaction(UNKNOWN).isEmpty());
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
