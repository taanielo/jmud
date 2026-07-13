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
 * Verifies that the real {@code data/classes/class.druid.json} definition loads correctly and that a
 * new player seeded from the Druid class receives its offensive {@code spell.moonfire} plus
 * {@code spell.cure}, while {@code spell.regrowth} and {@code spell.stoneskin} are trained later
 * (issue #516).
 */
class DruidClassSeedingTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final AbilityId REGROWTH = AbilityId.of("spell.regrowth");
    private static final AbilityId CURE = AbilityId.of("spell.cure");
    private static final AbilityId STONESKIN = AbilityId.of("spell.stoneskin");
    private static final AbilityId MOONFIRE = AbilityId.of("spell.moonfire");
    private static final AbilityId THORNLASH = AbilityId.of("spell.thornlash");
    private static final AbilityId BEAR_FORM = AbilityId.of("spell.bear-form");

    @Test
    void druidClassJsonLoadsCorrectly() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);

        ClassDefinition druid = repo.findById(ClassId.of("druid"))
            .orElseThrow(() -> new AssertionError("Druid class must be found in repository"));

        assertEquals("druid", druid.id().getValue());
        assertEquals("Druid", druid.name());
        assertEquals(List.of(CURE, MOONFIRE), druid.startingAbilityIds());
        assertEquals(List.of(REGROWTH, STONESKIN, THORNLASH, BEAR_FORM), druid.trainableAbilityIds());
    }

    @Test
    void druidHealingModifierSitsBetweenClericAndRanger() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);

        int druid = repo.findById(ClassId.of("druid")).orElseThrow().healingBaseModifier();
        int cleric = repo.findById(ClassId.of("cleric")).orElseThrow().healingBaseModifier();
        int ranger = repo.findById(ClassId.of("ranger")).orElseThrow().healingBaseModifier();

        assertTrue(ranger <= druid && druid <= cleric,
            "Druid healing modifier should sit between Ranger and Cleric");
    }

    @Test
    void druidAppearsInFindAll() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);

        boolean present = repo.findAll().stream()
            .anyMatch(cd -> cd.id().equals(ClassId.of("druid")));

        assertTrue(present, "Druid class must be discoverable via findAll for character creation");
    }

    @Test
    void playerSeededFromDruidClassGetsNatureKit() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);
        ClassDefinition druid = repo.findById(ClassId.of("druid"))
            .orElseThrow(() -> new AssertionError("Druid class not found"));

        User user = User.of(Username.of("Radagast"), Password.hash("secret"));
        Player player = Player.of(user, "%h/%H hp>", false, List.of())
            .withLearnedAbilities(druid.startingAbilityIds());

        List<AbilityId> learned = player.getLearnedAbilities();
        assertEquals(2, learned.size());
        assertTrue(learned.contains(CURE), "Druid starting abilities must include spell.cure");
        assertTrue(learned.contains(MOONFIRE), "Druid starting abilities must include spell.moonfire");
    }
}
