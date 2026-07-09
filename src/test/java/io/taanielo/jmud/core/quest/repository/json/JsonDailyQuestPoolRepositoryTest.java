package io.taanielo.jmud.core.quest.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.quest.DailyQuestPool;
import io.taanielo.jmud.core.quest.QuestRepositoryException;
import io.taanielo.jmud.core.quest.QuestTemplate;

/**
 * Unit tests for {@link JsonDailyQuestPoolRepository} (daily quest schema version 5).
 */
class JsonDailyQuestPoolRepositoryTest {

    private Path writePool(Path dataRoot, String fileName, String content) throws IOException {
        Path dailyDir = Files.createDirectories(dataRoot.resolve("quests").resolve("daily"));
        Path path = dailyDir.resolve(fileName);
        Files.writeString(path, content);
        return path;
    }

    @Test
    void loadsDailyPoolWithVariants(@TempDir Path dataRoot) throws IOException, QuestRepositoryException {
        writePool(dataRoot, "daily-slayer-pool.json", """
            {
              "schema_version": 5,
              "type": "daily",
              "pool_id": "slayer",
              "name": "Daily Slayer",
              "quests": [
                {"id": "daily-slayer-rats", "name": "Rats", "description": "Slay rats.",
                 "target_mob_id": "rat", "required_kills": 8, "gold_reward": 60, "xp_reward": 120},
                {"id": "daily-slayer-goblins", "name": "Goblins", "description": "Slay goblins.",
                 "target_mob_id": "goblin", "required_kills": 6, "gold_reward": 75, "xp_reward": 150}
              ]
            }
            """);

        JsonDailyQuestPoolRepository repository = new JsonDailyQuestPoolRepository(dataRoot);
        List<DailyQuestPool> pools = repository.findAll();

        assertEquals(1, pools.size());
        DailyQuestPool pool = pools.getFirst();
        assertEquals("slayer", pool.poolId());
        assertEquals(2, pool.quests().size());
        QuestTemplate first = pool.quests().getFirst();
        assertEquals("daily-slayer-rats", first.id().getValue());
        assertEquals("slayer", first.dailyPoolId());
        assertTrue(first.isDaily());
        assertEquals(8, first.requiredKills());
        assertEquals("rat", first.targetMobId());
    }

    @Test
    void rejectsUnsupportedSchemaVersion(@TempDir Path dataRoot) throws IOException, QuestRepositoryException {
        writePool(dataRoot, "bad.json", """
            {
              "schema_version": 4,
              "pool_id": "slayer",
              "name": "Daily Slayer",
              "quests": [
                {"id": "x", "name": "X", "description": "d", "target_mob_id": "rat", "required_kills": 1}
              ]
            }
            """);

        JsonDailyQuestPoolRepository repository = new JsonDailyQuestPoolRepository(dataRoot);
        assertThrows(QuestRepositoryException.class, repository::findAll);
    }

    @Test
    void emptyDirectoryYieldsNoPools(@TempDir Path dataRoot) throws IOException, QuestRepositoryException {
        Files.createDirectories(dataRoot.resolve("quests").resolve("daily"));
        JsonDailyQuestPoolRepository repository = new JsonDailyQuestPoolRepository(dataRoot);
        assertTrue(repository.findAll().isEmpty());
    }
}
