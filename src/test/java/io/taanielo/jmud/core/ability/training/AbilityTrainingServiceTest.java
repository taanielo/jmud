package io.taanielo.jmud.core.ability.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityRegistry;
import io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.prompt.PromptSettings;

/**
 * Unit tests for {@link AbilityTrainingService}: the ability level gate, practice-point spend
 * and the annotated {@code TRAIN LIST} view, all exercised without networking.
 */
class AbilityTrainingServiceTest {

    // second-wind requires level 3, taunt requires level 5 (see data/skills).
    private static final AbilityId SECOND_WIND = AbilityId.of("skill.second-wind");
    private static final AbilityId TAUNT = AbilityId.of("skill.taunt");
    private static final List<AbilityId> WARRIOR_POOL = List.of(SECOND_WIND, TAUNT);

    private AbilityTrainingService service;

    @BeforeEach
    void setUp() throws Exception {
        AbilityRegistry registry = new AbilityRegistry(new JsonAbilityRepository(Path.of("data")).findAll());
        service = new AbilityTrainingService(registry);
    }

    @Test
    void belowLevelAbilityIsRejectedAndPlayerUnchanged() {
        Player player = warrior(2, List.of(), 1);

        TrainingAttempt attempt = service.resolve(player, WARRIOR_POOL, "skill.second-wind");

        TrainingAttempt.LevelTooLow low = assertInstanceOf(TrainingAttempt.LevelTooLow.class, attempt);
        assertEquals(3, low.requiredLevel());
        assertEquals(2, low.playerLevel());
        // Nothing was spent or learned on the caller's player.
        assertEquals(1, player.getPracticePoints());
        assertFalse(player.getLearnedAbilities().contains(SECOND_WIND));
    }

    @Test
    void meetingLevelWithPointLearnsAbilityAndSpendsPoint() {
        Player player = warrior(3, List.of(), 1);

        TrainingAttempt attempt = service.resolve(player, WARRIOR_POOL, "skill.second-wind");

        TrainingAttempt.Success success = assertInstanceOf(TrainingAttempt.Success.class, attempt);
        assertEquals(0, success.updatedPlayer().getPracticePoints());
        assertTrue(success.updatedPlayer().getLearnedAbilities().contains(SECOND_WIND));
        // Original player is immutable and untouched.
        assertEquals(1, player.getPracticePoints());
        assertFalse(player.getLearnedAbilities().contains(SECOND_WIND));
    }

    @Test
    void freshLevelTwoCharacterCanSpendPointOnALevelTwoAbility() {
        // Acceptance #1: a character that has just reached level 2 (earning a practice point) can
        // immediately train a real level-2 ability such as bless.
        AbilityId bless = AbilityId.of("spell.bless");
        Player player = warrior(2, List.of(), 1);

        TrainingAttempt attempt = service.resolve(player, List.of(bless), "spell.bless");

        TrainingAttempt.Success success = assertInstanceOf(TrainingAttempt.Success.class, attempt);
        assertEquals(0, success.updatedPlayer().getPracticePoints());
        assertTrue(success.updatedPlayer().getLearnedAbilities().contains(bless));
    }

    @Test
    void withoutPointsTrainingIsRejected() {
        Player player = warrior(3, List.of(), 0);

        TrainingAttempt attempt = service.resolve(player, WARRIOR_POOL, "skill.second-wind");

        assertInstanceOf(TrainingAttempt.NoPracticePoints.class, attempt);
        assertFalse(player.getLearnedAbilities().contains(SECOND_WIND));
    }

    @Test
    void alreadyLearnedAbilityIsRejected() {
        Player player = warrior(3, List.of(SECOND_WIND), 1);

        TrainingAttempt attempt = service.resolve(player, WARRIOR_POOL, "skill.second-wind");

        assertInstanceOf(TrainingAttempt.AlreadyLearned.class, attempt);
        assertEquals(1, player.getPracticePoints());
    }

    @Test
    void abilityOutsidePoolIsNotTrainable() {
        Player player = warrior(3, List.of(), 1);

        TrainingAttempt attempt = service.resolve(player, WARRIOR_POOL, "spell.heal");

        assertInstanceOf(TrainingAttempt.NotTrainable.class, attempt);
    }

    @Test
    void listingAnnotatesLearnedAvailableAndLocked() {
        Player player = warrior(3, List.of(), 1);

        List<TrainableAbilityStatus> rows = service.listing(player, WARRIOR_POOL);

        assertEquals(2, rows.size());
        TrainableAbilityStatus secondWind = rowFor(rows, SECOND_WIND);
        TrainableAbilityStatus taunt = rowFor(rows, TAUNT);
        assertEquals(TrainableAbilityStatus.Status.AVAILABLE, secondWind.status());
        assertEquals(TrainableAbilityStatus.Status.REQUIRES_LEVEL, taunt.status());
    }

    @Test
    void listingMarksLearnedAbilities() {
        Player player = warrior(5, List.of(SECOND_WIND), 1);

        List<TrainableAbilityStatus> rows = service.listing(player, WARRIOR_POOL);

        assertEquals(TrainableAbilityStatus.Status.LEARNED, rowFor(rows, SECOND_WIND).status());
        assertEquals(TrainableAbilityStatus.Status.AVAILABLE, rowFor(rows, TAUNT).status());
    }

    private static TrainableAbilityStatus rowFor(List<TrainableAbilityStatus> rows, AbilityId id) {
        return rows.stream()
            .filter(r -> r.ability().id().equals(id))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No row for " + id));
    }

    private static Player warrior(int level, List<AbilityId> learned, int practicePoints) {
        User user = new User(Username.of("hero"), Password.of("pw"));
        return new Player(
            user,
            level,
            0,
            PlayerVitals.defaults(),
            List.of(),
            PromptSettings.defaultFormat(),
            false,
            learned,
            RaceId.of("human"),
            ClassId.of("warrior"),
            false,
            null,
            null,
            0,
            null,
            0L,
            practicePoints,
            0,
            null,
            null,
            null,
            null,
            null
        );
    }
}
