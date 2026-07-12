package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.Ability;
import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.ability.repository.json.JsonAbilityRepository;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;

/**
 * Data-integrity guard for issue #522: every class must have at least two trainable
 * (non-starting) abilities in the level 2–5 range so practice points always have something
 * meaningful to buy in the early game, and trainable ids must never overlap starting ids.
 */
class TrainablePoolCoverageTest {

    @Test
    void everyClassHasAtLeastTwoTrainableAbilitiesInLevelsTwoToFive() throws Exception {
        Map<AbilityId, Integer> levelById = levelsById();
        List<ClassDefinition> classes = new JsonClassRepository(Path.of("data")).findAll();

        for (ClassDefinition def : classes) {
            long midLevelTrainables = def.trainableAbilityIds().stream()
                .map(levelById::get)
                .filter(level -> level != null && level >= 2 && level <= 5)
                .count();
            assertTrue(midLevelTrainables >= 2,
                def.id().getValue() + " must have at least two trainable abilities in levels 2-5, found "
                    + midLevelTrainables);
        }
    }

    @Test
    void trainableAbilitiesNeverOverlapStartingAbilities() throws Exception {
        List<ClassDefinition> classes = new JsonClassRepository(Path.of("data")).findAll();

        for (ClassDefinition def : classes) {
            for (AbilityId trainable : def.trainableAbilityIds()) {
                assertFalse(def.startingAbilityIds().contains(trainable),
                    def.id().getValue() + " lists " + trainable.getValue()
                        + " as both starting and trainable");
            }
        }
    }

    private static Map<AbilityId, Integer> levelsById() throws Exception {
        Map<AbilityId, Integer> levels = new HashMap<>();
        for (Ability ability : new JsonAbilityRepository(Path.of("data")).findAll()) {
            levels.put(ability.id(), ability.level());
        }
        return levels;
    }
}
