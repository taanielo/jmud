package io.taanielo.jmud.core.combat.flavor.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.combat.flavor.CombatFlavor;

/**
 * Loads the real {@code data/combat/} flavor files, exercising the JSON schema and mapper end to end.
 */
class JsonCombatFlavorRepositoryTest {

    private CombatFlavor load() throws Exception {
        return new JsonCombatFlavorRepository().load();
    }

    @Test
    void loadsDamageVerbTiersFromData() throws Exception {
        var verbs = load().damageVerbs();
        assertEquals("mauls", verbs.verbFor(30, 100).thirdPerson());
        assertEquals("maul", verbs.verbFor(30, 100).secondPerson());
        assertEquals("scratches", verbs.verbFor(1, 100).thirdPerson());
        assertEquals("does === UNSPEAKABLE === damage to", verbs.verbFor(120, 100).thirdPerson());
    }

    @Test
    void loadsConditionTiersFromData() throws Exception {
        var conditions = load().conditions();
        assertEquals("is in perfect condition", conditions.describe(100, 100));
        assertEquals("has quite a few wounds", conditions.describe(60, 100));
        assertEquals("is in awful condition", conditions.describe(10, 100));
        assertTrue(conditions.isPerfect(20, 20));
        assertFalse(conditions.isPerfect(19, 20));
    }
}
