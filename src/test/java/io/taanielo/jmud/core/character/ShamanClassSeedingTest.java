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
 * Verifies that the real {@code data/classes/class.shaman.json} definition loads correctly and that
 * a new player seeded from the Shaman class receives {@code spell.ancestral-ward}, {@code spell.cure}
 * and the offensive {@code spell.lightning-bolt}, while {@code spell.haste} is trained later
 * (issue #516).
 */
class ShamanClassSeedingTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final AbilityId ANCESTRAL_WARD = AbilityId.of("spell.ancestral-ward");
    private static final AbilityId CURE = AbilityId.of("spell.cure");
    private static final AbilityId HASTE = AbilityId.of("spell.haste");
    private static final AbilityId REGROWTH = AbilityId.of("spell.regrowth");
    private static final AbilityId LIGHTNING_BOLT = AbilityId.of("spell.lightning-bolt");
    private static final AbilityId FLAME_SHOCK = AbilityId.of("spell.flame-shock");
    private static final AbilityId STORMCALL_TOTEM = AbilityId.of("spell.stormcall-totem");
    private static final AbilityId HEALING_TOTEM = AbilityId.of("spell.healing-totem");
    private static final AbilityId CHAIN_HEAL = AbilityId.of("spell.chain-heal");
    private static final AbilityId EARTHBIND_TOTEM = AbilityId.of("spell.earthbind-totem");

    @Test
    void shamanClassJsonLoadsCorrectly() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);

        ClassDefinition shaman = repo.findById(ClassId.of("shaman"))
            .orElseThrow(() -> new AssertionError("Shaman class must be found in repository"));

        assertEquals("shaman", shaman.id().getValue());
        assertEquals("Shaman", shaman.name());
        assertEquals(List.of(ANCESTRAL_WARD, CURE, LIGHTNING_BOLT), shaman.startingAbilityIds());
        assertEquals(List.of(HASTE, REGROWTH, FLAME_SHOCK, STORMCALL_TOTEM, HEALING_TOTEM, CHAIN_HEAL, EARTHBIND_TOTEM), shaman.trainableAbilityIds());
    }

    @Test
    void shamanHealingModifierSitsBetweenRangerAndDruid() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);

        int shaman = repo.findById(ClassId.of("shaman")).orElseThrow().healingBaseModifier();
        int ranger = repo.findById(ClassId.of("ranger")).orElseThrow().healingBaseModifier();
        int druid = repo.findById(ClassId.of("druid")).orElseThrow().healingBaseModifier();

        assertTrue(ranger <= shaman && shaman <= druid,
            "Shaman healing modifier should sit between Ranger and Druid");
    }

    @Test
    void shamanAppearsInFindAll() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);

        boolean present = repo.findAll().stream()
            .anyMatch(cd -> cd.id().equals(ClassId.of("shaman")));

        assertTrue(present, "Shaman class must be discoverable via findAll for character creation");
    }

    @Test
    void playerSeededFromShamanClassGetsSupportKit() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);
        ClassDefinition shaman = repo.findById(ClassId.of("shaman"))
            .orElseThrow(() -> new AssertionError("Shaman class not found"));

        User user = User.of(Username.of("Thrall"), Password.hash("secret"));
        Player player = Player.of(user, "%h/%H hp>", false, List.of())
            .withLearnedAbilities(shaman.startingAbilityIds());

        List<AbilityId> learned = player.getLearnedAbilities();
        assertEquals(3, learned.size());
        assertTrue(learned.contains(ANCESTRAL_WARD), "Shaman starting abilities must include spell.ancestral-ward");
        assertTrue(learned.contains(CURE), "Shaman starting abilities must include spell.cure");
        assertTrue(learned.contains(LIGHTNING_BOLT), "Shaman starting abilities must include spell.lightning-bolt");
    }
}
