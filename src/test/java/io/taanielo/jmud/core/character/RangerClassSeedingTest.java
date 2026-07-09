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
 * Verifies that {@code class.ranger.json} loads correctly and that a new player seeded from the
 * Ranger class receives {@code skill.track}.
 */
class RangerClassSeedingTest {

    private static final AbilityId TRACK = AbilityId.of("skill.track");

    @TempDir
    Path tempDir;

    @Test
    void rangerClassJsonLoadsCorrectly() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        Files.writeString(classesDir.resolve("class.ranger.json"), rangerClassJson());

        JsonClassRepository repo = new JsonClassRepository(tempDir);
        Optional<ClassDefinition> result = repo.findById(ClassId.of("ranger"));

        assertTrue(result.isPresent(), "Ranger class must be found in repository");
        ClassDefinition ranger = result.get();
        assertEquals("ranger", ranger.id().getValue());
        assertEquals("Ranger", ranger.name());
        assertEquals(0, ranger.healingBaseModifier());
        assertEquals(6, ranger.carryBonus());
        assertEquals(List.of(TRACK), ranger.startingAbilityIds());
    }

    @Test
    void playerSeededFromRangerClassGetsTrack() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        Files.writeString(classesDir.resolve("class.ranger.json"), rangerClassJson());

        JsonClassRepository repo = new JsonClassRepository(tempDir);
        ClassDefinition ranger = repo.findById(ClassId.of("ranger"))
            .orElseThrow(() -> new AssertionError("Ranger class not found"));

        User user = User.of(Username.of("Aragorn"), Password.hash("secret"));
        Player player = Player.of(user, "%h/%H hp>", false, List.of())
            .withLearnedAbilities(ranger.startingAbilityIds());

        List<AbilityId> learned = player.getLearnedAbilities();
        assertEquals(1, learned.size());
        assertTrue(learned.contains(TRACK),
            "Ranger starting abilities must include skill.track");
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static String rangerClassJson() {
        return """
            {
              "schema_version": 2,
              "id": "ranger",
              "name": "Ranger",
              "healing": {"base_modifier": 0},
              "carry_bonus": 6,
              "ability_ids": ["skill.track"]
            }
            """;
    }
}
