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
 * Integration smoke-test confirming that the three Darkwood mob templates
 * (wolf, dire-wolf, forest-troll) are present and have correct attributes
 * when loaded from the production data directory.
 */
class DarkwoodMobSpawnSmokeTest {

    @Test
    void darkwood_mobTemplates_arePresent() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        List<MobTemplate> templates = repo.findAll();
        Set<String> ids = templates.stream()
            .map(t -> t.id().getValue())
            .collect(Collectors.toSet());

        assertTrue(ids.contains("wolf"), "Expected 'wolf' mob template");
        assertTrue(ids.contains("dire-wolf"), "Expected 'dire-wolf' mob template");
        assertTrue(ids.contains("forest-troll"), "Expected 'forest-troll' mob template");
    }

    @Test
    void wolf_template_hasCorrectAttributes() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate wolf = repo.findAll().stream()
            .filter(t -> "wolf".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(wolf, "Wolf template must be present");
        assertEquals("Wolf", wolf.name());
        assertEquals(30, wolf.maxHp());
        assertFalse(wolf.aggressive(), "Wolf should not be aggressive");
        assertEquals("hunters-clearing", wolf.spawnRoomId().getValue());
        assertEquals(3, wolf.maxCount());
        assertEquals(20, wolf.respawnTicks());
        assertFalse(wolf.lootTable().isEmpty(), "Wolf should have at least one loot entry");
        assertNotNull(wolf.goldDrop(), "Wolf should have a gold drop");
        assertEquals(2, wolf.goldDrop().min());
        assertEquals(6, wolf.goldDrop().max());
    }

    @Test
    void direWolf_template_hasCorrectAttributes() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate direWolf = repo.findAll().stream()
            .filter(t -> "dire-wolf".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(direWolf, "Dire Wolf template must be present");
        assertEquals("Dire Wolf", direWolf.name());
        assertEquals(55, direWolf.maxHp());
        assertTrue(direWolf.aggressive(), "Dire Wolf should be aggressive");
        assertEquals("wolf-den", direWolf.spawnRoomId().getValue());
        assertEquals(2, direWolf.maxCount());
        assertEquals(30, direWolf.respawnTicks());
        assertNotNull(direWolf.goldDrop(), "Dire Wolf should have a gold drop");
        assertEquals(8, direWolf.goldDrop().min());
        assertEquals(18, direWolf.goldDrop().max());
    }

    @Test
    void forestTroll_template_hasCorrectAttributes() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate troll = repo.findAll().stream()
            .filter(t -> "forest-troll".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(troll, "Forest Troll template must be present");
        assertEquals("Forest Troll", troll.name());
        assertEquals(90, troll.maxHp());
        assertTrue(troll.aggressive(), "Forest Troll should be aggressive");
        assertEquals("trolls-hollow", troll.spawnRoomId().getValue());
        assertEquals(1, troll.maxCount());
        assertEquals(60, troll.respawnTicks());
        assertNotNull(troll.goldDrop(), "Forest Troll should have a gold drop");
        assertEquals(20, troll.goldDrop().min());
        assertEquals(45, troll.goldDrop().max());
    }
}
