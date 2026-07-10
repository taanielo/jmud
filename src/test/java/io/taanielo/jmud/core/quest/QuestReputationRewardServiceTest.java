package io.taanielo.jmud.core.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.faction.Faction;
import io.taanielo.jmud.core.faction.FactionId;
import io.taanielo.jmud.core.faction.FactionRepository;
import io.taanielo.jmud.core.faction.FactionRepositoryException;
import io.taanielo.jmud.core.faction.ReputationService;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.prompt.PromptSettings;
import io.taanielo.jmud.core.quest.QuestReputationRewardService.ReputationRewardGrant;

/**
 * Unit tests for {@link QuestReputationRewardService}.
 */
class QuestReputationRewardServiceTest {

    private static final FactionId BANDITS = FactionId.of("bandits");
    private static final Faction BANDIT_FACTION = new Faction(
        BANDITS, "the Bandit Brotherhood", "Cutthroats.", -10, 0, 0.02);

    private ReputationService reputationService() throws FactionRepositoryException {
        return new ReputationService(new StubFactionRepository(List.of(BANDIT_FACTION)));
    }

    private QuestReputationRewardService service() throws FactionRepositoryException {
        return new QuestReputationRewardService(reputationService());
    }

    private Player player() {
        User user = new User(Username.of("hero"), Password.of("pass"));
        return Player.of(user, PromptSettings.defaultFormat());
    }

    private QuestTemplate quest(String factionId, int delta) {
        return new QuestTemplate(QuestId.of("bandit-hunter"), "Bandit Hunter", "Kill bandits.",
            "bandit", 6, 90, 220).withReputationReward(factionId, delta);
    }

    @Test
    void grantDocksReputationWithNegativeDelta() throws FactionRepositoryException {
        ReputationRewardGrant grant = service().grant(player(), quest("bandits", -25));
        assertEquals(-25, grant.player().reputation().standing(BANDITS));
        assertEquals(Optional.of("Your standing with the Bandit Brotherhood has fallen."),
            grant.messageText());
    }

    @Test
    void grantRaisesReputationWithPositiveDelta() throws FactionRepositoryException {
        ReputationRewardGrant grant = service().grant(player(), quest("bandits", 15));
        assertEquals(15, grant.player().reputation().standing(BANDITS));
        assertEquals(Optional.of("Your standing with the Bandit Brotherhood has risen."),
            grant.messageText());
    }

    @Test
    void grantStacksOnExistingStanding() throws FactionRepositoryException {
        Player existing = player().withReputation(player().reputation().adjust(BANDITS, -10));
        ReputationRewardGrant grant = service().grant(existing, quest("bandits", -25));
        assertEquals(-35, grant.player().reputation().standing(BANDITS));
    }

    @Test
    void grantWithoutReputationRewardLeavesPlayerUnchanged() throws FactionRepositoryException {
        Player original = player();
        QuestTemplate noReward = new QuestTemplate(QuestId.of("rat-catcher"), "Rat Catcher",
            "Kill rats.", "rat", 5, 30, 75);
        ReputationRewardGrant grant = service().grant(original, noReward);
        assertSame(original, grant.player());
        assertTrue(grant.messageText().isEmpty());
    }

    @Test
    void grantWithUnknownFactionIsSkipped() throws FactionRepositoryException {
        Player original = player();
        ReputationRewardGrant grant = service().grant(original, quest("orcs", -5));
        assertSame(original, grant.player());
        assertTrue(grant.messageText().isEmpty());
    }

    @Test
    void describeRewardSummarisesSignedDelta() throws FactionRepositoryException {
        assertEquals(Optional.of("-25 rep with the Bandit Brotherhood"),
            service().describeReward(quest("bandits", -25)));
        assertEquals(Optional.of("+15 rep with the Bandit Brotherhood"),
            service().describeReward(quest("bandits", 15)));
    }

    @Test
    void describeRewardEmptyWhenNoReputationReward() throws FactionRepositoryException {
        QuestTemplate noReward = new QuestTemplate(QuestId.of("rat-catcher"), "Rat Catcher",
            "Kill rats.", "rat", 5, 30, 75);
        assertTrue(service().describeReward(noReward).isEmpty());
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
