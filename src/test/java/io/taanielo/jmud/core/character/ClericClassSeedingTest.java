package io.taanielo.jmud.core.character;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.ability.AbilityId;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.player.Player;

/**
 * Verifies that {@code class.cleric.json} loads correctly and that a new player seeded
 * from the Cleric class receives {@code spell.heal.group} and {@code spell.turn.undead}.
 */
class ClericClassSeedingTest {

    private static final AbilityId GROUP_HEAL = AbilityId.of("spell.heal.group");
    private static final AbilityId TURN_UNDEAD = AbilityId.of("spell.turn.undead");

    @TempDir
    Path tempDir;

    @Test
    void clericClassJsonLoadsCorrectly() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        Files.writeString(classesDir.resolve("class.cleric.json"), clericClassJson());

        JsonClassRepository repo = new JsonClassRepository(tempDir);
        Optional<ClassDefinition> result = repo.findById(ClassId.of("cleric"));

        assertTrue(result.isPresent(), "Cleric class must be found in repository");
        ClassDefinition cleric = result.get();
        assertEquals("cleric", cleric.id().getValue());
        assertEquals("Cleric", cleric.name());
        assertEquals(2, cleric.healingBaseModifier());
        assertEquals(10, cleric.carryBonus());
        assertEquals(List.of(GROUP_HEAL, TURN_UNDEAD), cleric.startingAbilityIds());
    }

    @Test
    void playerSeededFromClericClassGetsGroupHealAndTurnUndead() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        Files.writeString(classesDir.resolve("class.cleric.json"), clericClassJson());

        JsonClassRepository repo = new JsonClassRepository(tempDir);
        ClassDefinition cleric = repo.findById(ClassId.of("cleric"))
            .orElseThrow(() -> new AssertionError("Cleric class not found"));

        User user = User.of(Username.of("Priestess"), Password.hash("secret"));
        Player player = Player.of(user, "%h/%H hp>", false, List.of())
            .withLearnedAbilities(cleric.startingAbilityIds());

        List<AbilityId> learned = player.getLearnedAbilities();
        assertEquals(2, learned.size());
        assertTrue(learned.contains(GROUP_HEAL),
            "Cleric starting abilities must include spell.heal.group");
        assertTrue(learned.contains(TURN_UNDEAD),
            "Cleric starting abilities must include spell.turn.undead");
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static String clericClassJson() {
        return """
            {
              "schema_version": 2,
              "id": "cleric",
              "name": "Cleric",
              "healing": {"base_modifier": 2},
              "carry_bonus": 10,
              "ability_ids": ["spell.heal.group", "spell.turn.undead"]
            }
            """;
    }
}
