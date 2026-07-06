package io.taanielo.jmud.core.mob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import io.taanielo.jmud.core.mob.repository.json.JsonMobTemplateRepository;
import io.taanielo.jmud.core.world.repository.RepositoryException;

/**
 * Integration smoke-test confirming that the two Sewers mob templates
 * (slime, plague-rat) are present and have correct attributes when loaded
 * from the production data directory.
 */
class SewersMobSpawnSmokeTest {

    @Test
    void sewers_mobTemplates_arePresent() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        List<MobTemplate> templates = repo.findAll();
        Set<String> ids = templates.stream()
            .map(t -> t.id().getValue())
            .collect(Collectors.toSet());

        assertTrue(ids.contains("slime"), "Expected 'slime' mob template");
        assertTrue(ids.contains("plague-rat"), "Expected 'plague-rat' mob template");
    }

    @Test
    void slime_template_hasCorrectAttributes() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate slime = repo.findAll().stream()
            .filter(t -> "slime".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(slime, "Slime template must be present");
        assertEquals("Slime", slime.name());
        assertEquals(48, slime.maxHp());
        assertTrue(slime.aggressive(), "Slime should be aggressive");
        assertEquals("sewers-tunnel", slime.spawnRoomId().getValue());
        assertEquals(2, slime.maxCount());
        assertEquals(35, slime.respawnTicks());
        assertFalse(slime.lootTable().isEmpty(), "Slime should have at least one loot entry");
        assertNotNull(slime.goldDrop(), "Slime should have a gold drop");
        assertEquals(10, slime.goldDrop().min());
        assertEquals(24, slime.goldDrop().max());
        // Slime should be tougher than the low-level Darkwood mobs (Goblin: 15 HP).
        assertTrue(slime.maxHp() > 15, "Slime should be tougher than a Goblin");
    }

    @Test
    void plagueRat_template_hasCorrectAttributes() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate plagueRat = repo.findAll().stream()
            .filter(t -> "plague-rat".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(plagueRat, "Plague Rat template must be present");
        assertEquals("Plague Rat", plagueRat.name());
        assertEquals(38, plagueRat.maxHp());
        assertTrue(plagueRat.aggressive(), "Plague Rat should be aggressive");
        assertEquals("sewers-cistern", plagueRat.spawnRoomId().getValue());
        assertEquals(2, plagueRat.maxCount());
        assertEquals(30, plagueRat.respawnTicks());
        assertFalse(plagueRat.lootTable().isEmpty(), "Plague Rat should have at least one loot entry");
        assertNotNull(plagueRat.goldDrop(), "Plague Rat should have a gold drop");
        assertEquals(8, plagueRat.goldDrop().min());
        assertEquals(20, plagueRat.goldDrop().max());
        // Plague Rat should be tougher than the low-level Darkwood mobs (Kobold: 12 HP).
        assertTrue(plagueRat.maxHp() > 12, "Plague Rat should be tougher than a Kobold");
    }
}
