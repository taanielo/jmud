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
 * Verifies that {@code class.paladin.json} loads correctly and that a new player seeded from the
 * Paladin class receives its self-heal ({@code spell.lay.on.hands}), undead-smite
 * ({@code spell.smite}) and signature ward ({@code spell.divine-shield}) abilities, and that the
 * class exposes its heavy-armour bonus.
 */
class PaladinClassSeedingTest {

    private static final AbilityId LAY_ON_HANDS = AbilityId.of("spell.lay.on.hands");
    private static final AbilityId SMITE = AbilityId.of("spell.smite");
    private static final AbilityId DIVINE_SHIELD = AbilityId.of("spell.divine-shield");

    @TempDir
    Path tempDir;

    @Test
    void paladinClassJsonLoadsCorrectly() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        Files.writeString(classesDir.resolve("class.paladin.json"), paladinClassJson());

        JsonClassRepository repo = new JsonClassRepository(tempDir);
        Optional<ClassDefinition> result = repo.findById(ClassId.of("paladin"));

        assertTrue(result.isPresent(), "Paladin class must be found in repository");
        ClassDefinition paladin = result.get();
        assertEquals("paladin", paladin.id().getValue());
        assertEquals("Paladin", paladin.name());
        assertEquals(1, paladin.healingBaseModifier());
        assertEquals(8, paladin.carryBonus());
        assertEquals(5, paladin.armorBonus());
        assertEquals(List.of(LAY_ON_HANDS, SMITE, DIVINE_SHIELD), paladin.startingAbilityIds());
    }

    @Test
    void playerSeededFromPaladinClassGetsAbilities() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        Files.writeString(classesDir.resolve("class.paladin.json"), paladinClassJson());

        JsonClassRepository repo = new JsonClassRepository(tempDir);
        ClassDefinition paladin = repo.findById(ClassId.of("paladin"))
            .orElseThrow(() -> new AssertionError("Paladin class not found"));

        User user = User.of(Username.of("Roland"), Password.hash("secret"));
        Player player = Player.of(user, "%h/%H hp>", false, List.of())
            .withLearnedAbilities(paladin.startingAbilityIds());

        List<AbilityId> learned = player.getLearnedAbilities();
        assertEquals(3, learned.size());
        assertTrue(learned.contains(LAY_ON_HANDS),
            "Paladin starting abilities must include spell.lay.on.hands");
        assertTrue(learned.contains(SMITE),
            "Paladin starting abilities must include spell.smite");
        assertTrue(learned.contains(DIVINE_SHIELD),
            "Paladin starting abilities must include spell.divine-shield");
    }

    @Test
    void repositoryPaladinFileHasArmorBonusAndAbilities() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(Path.of("data"));
        ClassDefinition paladin = repo.findById(ClassId.of("paladin"))
            .orElseThrow(() -> new AssertionError("Paladin class not found in data/classes"));

        assertEquals(5, paladin.armorBonus(), "Paladin should grant a heavy-armour AC bonus");
        assertTrue(paladin.startingAbilityIds().contains(LAY_ON_HANDS));
        assertTrue(paladin.startingAbilityIds().contains(SMITE));
        assertTrue(paladin.startingAbilityIds().contains(DIVINE_SHIELD));
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static String paladinClassJson() {
        return """
            {
              "schema_version": 3,
              "id": "paladin",
              "name": "Paladin",
              "healing": {"base_modifier": 1},
              "carry_bonus": 8,
              "armor_bonus": 5,
              "ability_ids": ["spell.lay.on.hands", "spell.smite", "spell.divine-shield"]
            }
            """;
    }
}
