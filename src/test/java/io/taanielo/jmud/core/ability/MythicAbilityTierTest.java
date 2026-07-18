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
 * Verifies the level-75 mythic ability tier added in issue #715: one new trainable ability per
 * class, gated at level 75, so the endgame stretch opened up by The Undersong (65-72), The Interval
 * (73-80) and The Coda (81-88) is not flat past the level-60 legendary tier.
 *
 * <p>The tests assert, without any networking (AGENTS.md §10), that (a) every mythic ability loads at
 * level 75 and is reachable by exactly one class's trainable pool, (b) a level-75+ character can train
 * each one while a level-74 character is refused with {@link TrainingAttempt.LevelTooLow}, and (c) a
 * representative mythic ability (the ranger's pinning shot) can actually be cast, dealing damage and
 * applying its control effect.
 */
class MythicAbilityTierTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final int MYTHIC_LEVEL = 75;

    /** The eleven mythic abilities, one per class, added by issue #715. */
    private static final List<AbilityId> MYTHIC_ABILITIES = List.of(
        AbilityId.of("skill.intimidating-shout"),
        AbilityId.of("skill.sweeping-strike"),
        AbilityId.of("skill.blade-flurry"),
        AbilityId.of("skill.pinning-shot"),
        AbilityId.of("spell.meteor-swarm"),
        AbilityId.of("spell.doom"),
        AbilityId.of("spell.mass-exorcism"),
        AbilityId.of("spell.entangling-roots"),
        AbilityId.of("spell.earthbind-totem"),
        AbilityId.of("spell.virtuoso"),
        AbilityId.of("spell.hammer-of-justice")
    );

    private JsonAbilityRepository abilityRepository;
    private AbilityTrainingService trainingService;

    @BeforeEach
    void setUp() throws Exception {
        abilityRepository = new JsonAbilityRepository(DATA_ROOT);
        trainingService = new AbilityTrainingService(new AbilityRegistry(abilityRepository.findAll()));
    }

    @Test
    void everyMythicAbilityLoadsAtLevelSeventyFive() throws Exception {
        for (AbilityId id : MYTHIC_ABILITIES) {
            Ability ability = abilityRepository.findById(id)
                .orElseThrow(() -> new AssertionError(id.getValue() + " must load from data/skills"));
            assertEquals(MYTHIC_LEVEL, ability.level(),
                id.getValue() + " must be gated at the mythic level (75)");
        }
    }

    @Test
    void everyMythicAbilityIsReachableByExactlyOneClass() throws Exception {
        List<ClassDefinition> classes = new JsonClassRepository(DATA_ROOT).findAll();
        for (AbilityId id : MYTHIC_ABILITIES) {
            long owningClasses = classes.stream()
                .filter(c -> c.trainableAbilityIds().contains(id))
                .count();
            assertEquals(1, owningClasses,
                id.getValue() + " must be trainable by exactly one class");
        }
    }

    @Test
    void everyClassHasExactlyOneMythicTierTrainableAbility() throws Exception {
        List<ClassDefinition> classes = new JsonClassRepository(DATA_ROOT).findAll();
        AbilityRegistry registry = new AbilityRegistry(abilityRepository.findAll());
        for (ClassDefinition definition : classes) {
            long mythicCount = definition.trainableAbilityIds().stream()
                .map(id -> registry.findById(id).orElseThrow())
                .filter(ability -> ability.level() == MYTHIC_LEVEL)
                .count();
            assertEquals(1, mythicCount,
                definition.id().getValue() + " must have exactly one level-75 trainable ability");
        }
    }

    @Test
    void levelSeventyFiveCharacterCanTrainEachMythicAbility() {
        for (AbilityId id : MYTHIC_ABILITIES) {
            Player player = characterAtLevel(MYTHIC_LEVEL);

            TrainingAttempt attempt = trainingService.resolve(player, List.of(id), id.getValue());

            TrainingAttempt.Success success =
                assertInstanceOf(TrainingAttempt.Success.class, attempt,
                    id.getValue() + " must be trainable at level 75");
            assertTrue(success.updatedPlayer().getLearnedAbilities().contains(id),
                id.getValue() + " must be learned after training at level 75");
        }
    }

    @Test
    void levelSeventyFourCharacterIsRefusedWithLevelTooLow() {
        for (AbilityId id : MYTHIC_ABILITIES) {
            Player player = characterAtLevel(MYTHIC_LEVEL - 1);

            TrainingAttempt attempt = trainingService.resolve(player, List.of(id), id.getValue());

            TrainingAttempt.LevelTooLow low =
                assertInstanceOf(TrainingAttempt.LevelTooLow.class, attempt,
                    id.getValue() + " must be refused below level 75");
            assertEquals(MYTHIC_LEVEL, low.requiredLevel(),
                id.getValue() + " must report a required level of 75");
            assertEquals(MYTHIC_LEVEL - 1, low.playerLevel());
        }
    }

    @Test
    void castingPinningShotDealsDamageAndAppliesPinned() throws Exception {
        AbilityId pinningShot = AbilityId.of("skill.pinning-shot");
        EffectId pinned = EffectId.of("pinned");
        Ability ability = abilityRepository.findById(pinningShot).orElseThrow();
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
            source, "pin ogre", List.of(pinningShot), resolver, noCooldowns());

        assertEquals(target.getVitals().hp() - directDamage, result.target().getVitals().hp(),
            "pinning shot must reduce the target's HP by its direct-hit amount");
        assertTrue(result.target().effects().stream().anyMatch(e -> e.id().equals(pinned)),
            "pinning shot must leave the target pinned");
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
