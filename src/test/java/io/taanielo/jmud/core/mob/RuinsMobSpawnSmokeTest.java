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
 * Integration smoke-test confirming that the two Ruins mob templates
 * (bandit, bandit-captain) are present and have correct attributes when
 * loaded from the production data directory.
 */
class RuinsMobSpawnSmokeTest {

    @Test
    void ruins_mobTemplates_arePresent() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        List<MobTemplate> templates = repo.findAll();
        Set<String> ids = templates.stream()
            .map(t -> t.id().getValue())
            .collect(Collectors.toSet());

        assertTrue(ids.contains("bandit"), "Expected 'bandit' mob template");
        assertTrue(ids.contains("bandit-captain"), "Expected 'bandit-captain' mob template");
    }

    @Test
    void bandit_template_hasCorrectAttributes() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate bandit = repo.findAll().stream()
            .filter(t -> "bandit".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(bandit, "Bandit template must be present");
        assertEquals("Bandit", bandit.name());
        assertEquals(50, bandit.maxHp());
        assertTrue(bandit.aggressive(), "Bandit should be aggressive");
        assertEquals("crumbling-courtyard", bandit.spawnRoomId().getValue());
        assertEquals(2, bandit.maxCount());
        assertEquals(30, bandit.respawnTicks());
        assertFalse(bandit.lootTable().isEmpty(), "Bandit should have at least one loot entry");
        assertNotNull(bandit.goldDrop(), "Bandit should have a gold drop");
        assertEquals(10, bandit.goldDrop().min());
        assertEquals(22, bandit.goldDrop().max());
        // Bandit should be on par with mid-tier Darkwood mobs (Dire Wolf: 55 HP).
        assertTrue(bandit.maxHp() > 15, "Bandit should be tougher than a Goblin");
    }

    @Test
    void banditCaptain_template_hasCorrectAttributes() throws RepositoryException {
        JsonMobTemplateRepository repo = new JsonMobTemplateRepository(Path.of("data"));

        MobTemplate banditCaptain = repo.findAll().stream()
            .filter(t -> "bandit-captain".equals(t.id().getValue()))
            .findFirst()
            .orElse(null);

        assertNotNull(banditCaptain, "Bandit Captain template must be present");
        assertEquals("Bandit Captain", banditCaptain.name());
        assertTrue(banditCaptain.aggressive(), "Bandit Captain should be aggressive");
        assertEquals("bandit-captains-den", banditCaptain.spawnRoomId().getValue());
        assertEquals(1, banditCaptain.maxCount(), "Bandit Captain is a unique boss");
        assertNotNull(banditCaptain.specialAttackId(), "Bandit Captain should have a special attack");
        assertEquals("attack.rally-cry", banditCaptain.specialAttackId().getValue());
        assertNotNull(banditCaptain.goldDrop(), "Bandit Captain should have a gold drop");

        MobTemplate bandit = repo.findAll().stream()
            .filter(t -> "bandit".equals(t.id().getValue()))
            .findFirst()
            .orElseThrow();

        assertTrue(banditCaptain.maxHp() > bandit.maxHp(),
            "Bandit Captain should have more HP than a regular Bandit");
        assertTrue(banditCaptain.goldDrop().max() > bandit.goldDrop().max(),
            "Bandit Captain should drop more gold than a regular Bandit");
    }
}
