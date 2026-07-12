package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.AbilityTargeting;
import io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.combat.AttackDefinition;
import io.taanielo.jmud.core.combat.AttackId;
import io.taanielo.jmud.core.combat.CombatSettings;
import io.taanielo.jmud.core.combat.repository.json.JsonAttackRepository;
import io.taanielo.jmud.core.mob.MobTemplate;
import io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository;

/**
 * Early-game balance guardrails for issue #516: every playable class must have a level-1
 * offensive option usable against ordinary (non-undead) newbie-ring mobs, every class must
 * offer at least one trainable ability at the Master Trainer on day one, and an unarmed
 * level-1 character must win the first available fight (a lone goblin) on average.
 */
class EarlyGameBalanceTest {

    private static final Path DATA_ROOT = Path.of("data");

    /** Targeting kinds that let an ability damage an ordinary (non-undead) hostile mob. */
    private static final Set<AbilityTargeting> NON_UNDEAD_OFFENSE = EnumSet.of(
        AbilityTargeting.HARMFUL,
        AbilityTargeting.HARMFUL_OPENER,
        AbilityTargeting.AoE
    );

    @Test
    void everyClassHasALevelOneOffenseUsableAgainstNonUndeadMobs() throws Exception {
        JsonClassRepository classRepo = new JsonClassRepository(DATA_ROOT);
        JsonAbilityRepository abilityRepo = new JsonAbilityRepository(DATA_ROOT);

        for (ClassDefinition classDef : classRepo.findAll()) {
            boolean hasOffense = false;
            for (AbilityId abilityId : classDef.startingAbilityIds()) {
                Ability ability = abilityRepo.findById(abilityId).orElseThrow(
                    () -> new AssertionError("Starting ability " + abilityId.getValue()
                        + " referenced by class " + classDef.id().getValue() + " must exist in data"));
                if (ability.level() == 1 && NON_UNDEAD_OFFENSE.contains(ability.targeting())) {
                    hasOffense = true;
                    break;
                }
            }
            assertTrue(hasOffense,
                "Class " + classDef.id().getValue()
                    + " must have at least one level-1 ability usable against non-undead mobs");
        }
    }

    @Test
    void everyClassOffersAtLeastOneTrainableAbilityNotAlreadyGranted() throws Exception {
        JsonClassRepository classRepo = new JsonClassRepository(DATA_ROOT);

        for (ClassDefinition classDef : classRepo.findAll()) {
            List<AbilityId> trainable = classDef.trainableAbilityIds();
            assertFalse(trainable.isEmpty(),
                "Class " + classDef.id().getValue()
                    + " must offer at least one trainable ability so TRAIN works on day one");
            for (AbilityId abilityId : trainable) {
                assertFalse(classDef.startingAbilityIds().contains(abilityId),
                    "Trainable ability " + abilityId.getValue() + " for class "
                        + classDef.id().getValue()
                        + " must not already be auto-granted at creation");
            }
        }
    }

    @Test
    void unarmedLevelOnePlayerBeatsALoneGoblinOnAverage() throws Exception {
        JsonAttackRepository attackRepo = new JsonAttackRepository(DATA_ROOT);
        JsonMobTemplateRepository mobRepo = new JsonMobTemplateRepository(DATA_ROOT);

        AttackDefinition unarmed = attackRepo.findById(AttackId.of("attack.unarmed")).orElseThrow();
        MobTemplate goblin = mobRepo.findAll().stream()
            .filter(m -> "goblin".equals(m.id().jsonValue()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("goblin mob template must exist"));
        AttackDefinition goblinAttack = attackRepo.findById(goblin.attackId()).orElseThrow();

        double hit = CombatSettings.baseHitChance() / 100.0;
        int playerHp = 20; // fresh level-1 vitals

        double playerDps = hit * average(unarmed.minDamage(), unarmed.maxDamage());
        double goblinDps = hit * average(goblinAttack.minDamage(), goblinAttack.maxDamage());

        double ticksToKillGoblin = goblin.maxHp() / playerDps;
        double ticksToKillPlayer = playerHp / goblinDps;

        assertTrue(ticksToKillGoblin < ticksToKillPlayer,
            "An unarmed level-1 player must kill a lone goblin before dying: "
                + "player needs ~" + round(ticksToKillGoblin) + " ticks, goblin needs ~"
                + round(ticksToKillPlayer) + " ticks");
    }

    private static double average(int min, int max) {
        return (min + max) / 2.0;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
