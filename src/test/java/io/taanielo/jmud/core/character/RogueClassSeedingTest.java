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
import io.taanielo.jmud.core.character.repository.json.JsonClassRepository;
import io.taanielo.jmud.core.player.Player;
import io.taanielo.jmud.core.authentication.Password;
import io.taanielo.jmud.core.authentication.User;
import io.taanielo.jmud.core.authentication.Username;

/**
 * Verifies that {@code class.rogue.json} loads correctly and that a new player seeded
 * from the Rogue class receives the {@code skill.backstab} ability.
 */
class RogueClassSeedingTest {

    private static final AbilityId BACKSTAB = AbilityId.of("skill.backstab");

    @TempDir
    Path tempDir;

    @Test
    void rogueClassJsonLoadsCorrectly() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        Files.writeString(classesDir.resolve("class.rogue.json"), rogueClassJson());

        JsonClassRepository repo = new JsonClassRepository(tempDir);
        Optional<ClassDefinition> result = repo.findById(ClassId.of("rogue"));

        assertTrue(result.isPresent(), "Rogue class must be found in repository");
        ClassDefinition rogue = result.get();
        assertEquals("rogue", rogue.id().getValue());
        assertEquals("Rogue", rogue.name());
        assertEquals(1, rogue.healingBaseModifier());
        assertEquals(8, rogue.carryBonus());
        assertEquals(List.of(BACKSTAB), rogue.startingAbilityIds());
    }

    @Test
    void playerSeededFromRogueClassGetsBackstab() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        Files.writeString(classesDir.resolve("class.rogue.json"), rogueClassJson());

        JsonClassRepository repo = new JsonClassRepository(tempDir);
        ClassDefinition rogue = repo.findById(ClassId.of("rogue"))
            .orElseThrow(() -> new AssertionError("Rogue class not found"));

        User user = User.of(Username.of("Shadow"), Password.hash("secret"));
        Player player = Player.of(user, "%h/%H hp>", false, List.of())
            .withLearnedAbilities(rogue.startingAbilityIds());

        List<AbilityId> learned = player.getLearnedAbilities();
        assertEquals(1, learned.size());
        assertTrue(learned.contains(BACKSTAB),
            "Rogue starting abilities must include skill.backstab");
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static String rogueClassJson() {
        return """
            {
              "schema_version": 2,
              "id": "rogue",
              "name": "Rogue",
              "healing": {"base_modifier": 1},
              "carry_bonus": 8,
              "ability_ids": ["skill.backstab"]
            }
            """;
    }
}
