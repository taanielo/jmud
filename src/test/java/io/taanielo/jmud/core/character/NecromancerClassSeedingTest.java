package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.player.Player;

/**
 * Verifies that the real {@code data/classes/class.necromancer.json} definition loads correctly and
 * that a new player seeded from the Necromancer class receives {@code spell.death-coil},
 * {@code spell.summon} and {@code spell.cure}.
 */
class NecromancerClassSeedingTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final AbilityId DEATH_COIL = AbilityId.of("spell.death-coil");
    private static final AbilityId SUMMON = AbilityId.of("spell.summon");
    private static final AbilityId CURE = AbilityId.of("spell.cure");

    @Test
    void necromancerClassJsonLoadsCorrectly() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);

        ClassDefinition necromancer = repo.findById(ClassId.of("necromancer"))
            .orElseThrow(() -> new AssertionError("Necromancer class must be found in repository"));

        assertEquals("necromancer", necromancer.id().getValue());
        assertEquals("Necromancer", necromancer.name());
        assertEquals(List.of(DEATH_COIL, SUMMON, CURE), necromancer.startingAbilityIds());
    }

    @Test
    void necromancerIsNotAHealer() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);

        int necromancer = repo.findById(ClassId.of("necromancer")).orElseThrow().healingBaseModifier();
        int cleric = repo.findById(ClassId.of("cleric")).orElseThrow().healingBaseModifier();

        assertTrue(necromancer < 0, "Necromancer healing modifier should be negative like Mage");
        assertTrue(necromancer < cleric, "Necromancer should heal for far less than a Cleric");
    }

    @Test
    void necromancerAppearsInFindAll() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);

        boolean present = repo.findAll().stream()
            .anyMatch(cd -> cd.id().equals(ClassId.of("necromancer")));

        assertTrue(present, "Necromancer class must be discoverable via findAll for character creation");
    }

    @Test
    void playerSeededFromNecromancerClassGetsDarkCasterKit() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);
        ClassDefinition necromancer = repo.findById(ClassId.of("necromancer"))
            .orElseThrow(() -> new AssertionError("Necromancer class not found"));

        User user = User.of(Username.of("Malchor"), Password.hash("secret"));
        Player player = Player.of(user, "%h/%H hp>", false, List.of())
            .withLearnedAbilities(necromancer.startingAbilityIds());

        List<AbilityId> learned = player.getLearnedAbilities();
        assertEquals(3, learned.size());
        assertTrue(learned.contains(DEATH_COIL), "Necromancer starting abilities must include spell.death-coil");
        assertTrue(learned.contains(SUMMON), "Necromancer starting abilities must include spell.summon");
        assertTrue(learned.contains(CURE), "Necromancer starting abilities must include spell.cure");
    }
}
