package io.taanielo.jmud.core.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository;
import io.taanielo.jmud.core.ability.training.AbilityTrainingService;
import io.taanielo.jmud.core.ability.training.TrainingAttempt;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.ClassDefinition;
import io.taanielo.jmud.core.character.ClassId;
import io.taanielo.jmud.core.character.RaceId;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.effects.EffectEngine;
import io.taanielo.jmud.core.effects.EffectId;
import io.taanielo.jmud.core.effects.repository.json.JsonEffectRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.player.PlayerVitals;
import io.taanielo.jmud.core.prompt.PromptSettings;

/**
 * Verifies the level-85 epic ability tier added in issue #765: one new trainable ability per class,
 * gated at level 85, so the long 75-96 endgame stretch opened up by The Encore (#727) raising the cap
 * to 96 is not flat past the level-75 mythic tier. The epic tier lands mid-Coda (81-88), one zone
 * before the level-96 cap, mirroring how the mythic tier landed one zone before The Coda.
 *
 * <p>The tests assert, without any networking (AGENTS.md §10), that (a) every epic ability loads at
 * level 85 and is reachable by exactly one class's trainable pool, (b) a level-85+ character can train
 * each one while a level-84 character is refused with {@link TrainingAttempt.LevelTooLow}, and (c) a
 * representative epic ability (the ranger's serpent sting) can actually be cast, dealing damage and
 * applying its damage-over-time effect.
 */
class EpicAbilityTierTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final int EPIC_LEVEL = 85;

    /** The eleven epic abilities, one per class, added by issue #765. */
    private static final List<AbilityId> EPIC_ABILITIES = List.of(
        AbilityId.of("skill.berserker-rage"),
        AbilityId.of("skill.battle-standard"),
        AbilityId.of("spell.lullaby"),
        AbilityId.of("spell.holy-fire"),
        AbilityId.of("spell.hibernate"),
        AbilityId.of("spell.ignite"),
        AbilityId.of("spell.curse-of-tongues"),
        AbilityId.of("spell.crusade"),
        AbilityId.of("skill.serpent-sting"),
        AbilityId.of("skill.kidney-shot"),
        AbilityId.of("spell.cleansing-waters")
    );

    private JsonAbilityRepository abilityRepository;
    private AbilityTrainingService trainingService;

    @BeforeEach
    void setUp() throws Exception {
        abilityRepository = new JsonAbilityRepository(DATA_ROOT);
        trainingService = new AbilityTrainingService(new AbilityRegistry(abilityRepository.findAll()));
    }

    @Test
    void everyEpicAbilityLoadsAtLevelEightyFive() throws Exception {
        for (AbilityId id : EPIC_ABILITIES) {
            Ability ability = abilityRepository.findById(id)
                .orElseThrow(() -> new AssertionError(id.getValue() + " must load from data/skills"));
            assertEquals(EPIC_LEVEL, ability.level(),
                id.getValue() + " must be gated at the epic level (85)");
        }
    }

    @Test
    void everyEpicAbilityIsReachableByExactlyOneClass() throws Exception {
        List<ClassDefinition> classes = new JsonClassRepository(DATA_ROOT).findAll();
        for (AbilityId id : EPIC_ABILITIES) {
            long owningClasses = classes.stream()
                .filter(c -> c.trainableAbilityIds().contains(id))
                .count();
            assertEquals(1, owningClasses,
                id.getValue() + " must be trainable by exactly one class");
        }
    }

    @Test
    void everyClassHasExactlyOneEpicTierTrainableAbility() throws Exception {
        List<ClassDefinition> classes = new JsonClassRepository(DATA_ROOT).findAll();
        AbilityRegistry registry = new AbilityRegistry(abilityRepository.findAll());
        for (ClassDefinition definition : classes) {
            long epicCount = definition.trainableAbilityIds().stream()
                .map(id -> registry.findById(id).orElseThrow())
                .filter(ability -> ability.level() == EPIC_LEVEL)
                .count();
            assertEquals(1, epicCount,
                definition.id().getValue() + " must have exactly one level-85 trainable ability");
        }
    }

    @Test
    void levelEightyFiveCharacterCanTrainEachEpicAbility() {
        for (AbilityId id : EPIC_ABILITIES) {
            Player player = characterAtLevel(EPIC_LEVEL);

            TrainingAttempt attempt = trainingService.resolve(player, List.of(id), id.getValue());

            TrainingAttempt.Success success =
                assertInstanceOf(TrainingAttempt.Success.class, attempt,
                    id.getValue() + " must be trainable at level 85");
            assertTrue(success.updatedPlayer().getLearnedAbilities().contains(id),
                id.getValue() + " must be learned after training at level 85");
        }
    }

    @Test
    void levelEightyFourCharacterIsRefusedWithLevelTooLow() {
        for (AbilityId id : EPIC_ABILITIES) {
            Player player = characterAtLevel(EPIC_LEVEL - 1);

            TrainingAttempt attempt = trainingService.resolve(player, List.of(id), id.getValue());

            TrainingAttempt.LevelTooLow low =
                assertInstanceOf(TrainingAttempt.LevelTooLow.class, attempt,
                    id.getValue() + " must be refused below level 85");
            assertEquals(EPIC_LEVEL, low.requiredLevel(),
                id.getValue() + " must report a required level of 85");
            assertEquals(EPIC_LEVEL - 1, low.playerLevel());
        }
    }

    @Test
    void castingSerpentStingDealsDamageAndAppliesSerpentStung() throws Exception {
        AbilityId serpentSting = AbilityId.of("skill.serpent-sting");
        EffectId serpentStung = EffectId.of("serpent-stung");
        Ability ability = abilityRepository.findById(serpentSting).orElseThrow();
        int directDamage = ability.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.VITALS)
            .mapToInt(AbilityEffect::amount)
            .sum();

        EffectEngine effectEngine = new EffectEngine(new JsonEffectRepository());
        Player source = fullPlayer("Hawk");
        Player target = fullPlayer("Ogre");
        AbilityEngine engine = new AbilityEngine(
            new AbilityRegistry(List.of(ability)),
            new BasicAbilityCostResolver(),
            new DefaultAbilityEffectResolver(effectEngine, new NoopMessageSink(), AbilityEffectListener.noop()),
            new NoopMessageSink());
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityUseResult result = engine.use(
            source, "sting ogre", List.of(serpentSting), resolver, noCooldowns());

        assertEquals(target.getVitals().hp() - directDamage, result.target().getVitals().hp(),
            "serpent sting must reduce the target's HP by its direct-hit amount");
        assertTrue(result.target().effects().stream().anyMatch(e -> e.id().equals(serpentStung)),
            "serpent sting must leave the target poisoned");
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static Player characterAtLevel(int level) {
        User user = new User(Username.of("hero"), Password.of("pw"));
        return new Player(
            user,
            level,
            0,
            PlayerVitals.defaults(),
            List.of(),
            PromptSettings.defaultFormat(),
            false,
            List.of(),
            RaceId.of("human"),
            ClassId.of("warrior"),
            false,
            null,
            null,
            0,
            null,
            0L,
            1,
            0,
            null,
            null,
            null,
            null,
            null
        );
    }

    private static Player fullPlayer(String name) {
        return new Player(
            User.of(Username.of(name), Password.hash("pw")),
            1, 0,
            new PlayerVitals(50, 50, 30, 30, 30, 30),
            new ArrayList<>(), "prompt", false, List.of(), null, null);
    }

    private static AbilityCooldownTracker noCooldowns() {
        return new AbilityCooldownTracker() {
            @Override public boolean isOnCooldown(AbilityId id) { return false; }
            @Override public int remainingTicks(AbilityId id) { return 0; }
            @Override public void startCooldown(AbilityId id, int ticks) { }
        };
    }

    private static final class NoopMessageSink implements AbilityMessageSink {
        @Override public void sendToSource(Player source, String message) { }
        @Override public void sendToTarget(Player target, String message) { }
        @Override public void sendToRoom(Player source, Player target, String message) { }
    }
}
