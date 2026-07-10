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
 * Verifies that the real {@code data/classes/class.bard.json} definition loads correctly and that a
 * new player seeded from the Bard class receives {@code spell.battle-hymn} and {@code spell.haste}.
 */
class BardClassSeedingTest {

    private static final Path DATA_ROOT = Path.of("data");
    private static final AbilityId BATTLE_HYMN = AbilityId.of("spell.battle-hymn");
    private static final AbilityId HASTE = AbilityId.of("spell.haste");

    @Test
    void bardClassJsonLoadsCorrectly() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);

        ClassDefinition bard = repo.findById(ClassId.of("bard"))
            .orElseThrow(() -> new AssertionError("Bard class must be found in repository"));

        assertEquals("bard", bard.id().getValue());
        assertEquals("Bard", bard.name());
        assertEquals(List.of(BATTLE_HYMN, HASTE), bard.startingAbilityIds());
    }

    @Test
    void playerSeededFromBardClassGetsHymnAndHaste() throws Exception {
        JsonClassRepository repo = new JsonClassRepository(DATA_ROOT);
        ClassDefinition bard = repo.findById(ClassId.of("bard"))
            .orElseThrow(() -> new AssertionError("Bard class not found"));

        User user = User.of(Username.of("Pippin"), Password.hash("secret"));
        Player player = Player.of(user, "%h/%H hp>", false, List.of())
            .withLearnedAbilities(bard.startingAbilityIds());

        List<AbilityId> learned = player.getLearnedAbilities();
        assertEquals(2, learned.size());
        assertTrue(learned.contains(BATTLE_HYMN), "Bard starting abilities must include spell.battle-hymn");
        assertTrue(learned.contains(HASTE), "Bard starting abilities must include spell.haste");
    }
}
