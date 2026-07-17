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
 * Verifies the level-60 legendary ability tier added in issue #665: one new trainable ability per
 * class, gated at level 60, so the endgame stretch opened up by The Unsung (57-64) and The Undersong
 * (65-72) is not flat past the level-45 grandmaster tier.
 *
 * <p>The tests assert, without any networking (AGENTS.md §10), that (a) every legendary ability loads
 * at level 60 and is reachable by exactly one class's trainable pool, (b) a level-60+ character can
 * train each one while a level-59 character is refused with {@link TrainingAttempt.LevelTooLow}, and
 * (c) a representative legendary ability can actually be cast, dealing damage and applying its effect.
 */
class LegendaryAbilityTierTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final int LEGENDARY_LEVEL = 60;

    /** The eleven legendary abilities, one per class, added by issue #665. */
    private static final List<AbilityId> LEGENDARY_ABILITIES = List.of(
        AbilityId.of("skill.whirlwind"),
        AbilityId.of("skill.field-dressing"),
        AbilityId.of("skill.garrote"),
        AbilityId.of("skill.evasive-maneuvers"),
        AbilityId.of("spell.mana-shield"),
        AbilityId.of("spell.bone-shield"),
        AbilityId.of("spell.holy-nova"),
        AbilityId.of("spell.hurricane"),
        AbilityId.of("spell.chain-heal"),
        AbilityId.of("spell.siren-song"),
        AbilityId.of("spell.avenging-wrath")
    );

    private JsonAbilityRepository abilityRepository;
    private AbilityTrainingService trainingService;

    @BeforeEach
    void setUp() throws Exception {
        abilityRepository = new JsonAbilityRepository(DATA_ROOT);
        trainingService = new AbilityTrainingService(new AbilityRegistry(abilityRepository.findAll()));
    }

    @Test
    void everyLegendaryAbilityLoadsAtLevelSixty() throws Exception {
        for (AbilityId id : LEGENDARY_ABILITIES) {
            Ability ability = abilityRepository.findById(id)
                .orElseThrow(() -> new AssertionError(id.getValue() + " must load from data/skills"));
            assertEquals(LEGENDARY_LEVEL, ability.level(),
                id.getValue() + " must be gated at the legendary level (60)");
        }
    }

    @Test
    void everyLegendaryAbilityIsReachableByExactlyOneClass() throws Exception {
        List<ClassDefinition> classes = new JsonClassRepository(DATA_ROOT).findAll();
        for (AbilityId id : LEGENDARY_ABILITIES) {
            long owningClasses = classes.stream()
                .filter(c -> c.trainableAbilityIds().contains(id))
                .count();
            assertEquals(1, owningClasses,
                id.getValue() + " must be trainable by exactly one class");
        }
    }

    @Test
    void everyClassHasExactlyOneLegendaryTierTrainableAbility() throws Exception {
        List<ClassDefinition> classes = new JsonClassRepository(DATA_ROOT).findAll();
        AbilityRegistry registry = new AbilityRegistry(abilityRepository.findAll());
        for (ClassDefinition definition : classes) {
            long legendaryCount = definition.trainableAbilityIds().stream()
                .map(id -> registry.findById(id).orElseThrow())
                .filter(ability -> ability.level() == LEGENDARY_LEVEL)
                .count();
            assertEquals(1, legendaryCount,
                definition.id().getValue() + " must have exactly one level-60 trainable ability");
        }
    }

    @Test
    void levelSixtyCharacterCanTrainEachLegendaryAbility() {
        for (AbilityId id : LEGENDARY_ABILITIES) {
            Player player = characterAtLevel(LEGENDARY_LEVEL);

            TrainingAttempt attempt = trainingService.resolve(player, List.of(id), id.getValue());

            TrainingAttempt.Success success =
                assertInstanceOf(TrainingAttempt.Success.class, attempt,
                    id.getValue() + " must be trainable at level 60");
            assertTrue(success.updatedPlayer().getLearnedAbilities().contains(id),
                id.getValue() + " must be learned after training at level 60");
        }
    }

    @Test
    void levelFiftyNineCharacterIsRefusedWithLevelTooLow() {
        for (AbilityId id : LEGENDARY_ABILITIES) {
            Player player = characterAtLevel(LEGENDARY_LEVEL - 1);

            TrainingAttempt attempt = trainingService.resolve(player, List.of(id), id.getValue());

            TrainingAttempt.LevelTooLow low =
                assertInstanceOf(TrainingAttempt.LevelTooLow.class, attempt,
                    id.getValue() + " must be refused below level 60");
            assertEquals(LEGENDARY_LEVEL, low.requiredLevel(),
                id.getValue() + " must report a required level of 60");
            assertEquals(LEGENDARY_LEVEL - 1, low.playerLevel());
        }
    }

    @Test
    void castingGarroteDealsDamageAndAppliesGarrote() throws Exception {
        AbilityId garrote = AbilityId.of("skill.garrote");
        EffectId garroted = EffectId.of("garrote");
        Ability ability = abilityRepository.findById(garrote).orElseThrow();
        int directDamage = ability.effects().stream()
            .filter(e -> e.kind() == AbilityEffectKind.VITALS)
            .mapToInt(AbilityEffect::amount)
            .sum();

        EffectEngine effectEngine = new EffectEngine(new JsonEffectRepository());
        Player source = fullPlayer("Sly");
        Player target = fullPlayer("Ogre");
        AbilityEngine engine = new AbilityEngine(
            new AbilityRegistry(List.of(ability)),
            new BasicAbilityCostResolver(),
            new DefaultAbilityEffectResolver(effectEngine, new NoopMessageSink(), AbilityEffectListener.noop()),
            new NoopMessageSink());
        AbilityTargetResolver resolver = (p, input) -> Optional.of(target);

        AbilityUseResult result = engine.use(
            source, "garrote ogre", List.of(garrote), resolver, noCooldowns());

        assertEquals(target.getVitals().hp() - directDamage, result.target().getVitals().hp(),
            "garrote must reduce the target's HP by its direct-hit amount");
        assertTrue(result.target().effects().stream().anyMatch(e -> e.id().equals(garroted)),
            "garrote must leave the target garroted");
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
