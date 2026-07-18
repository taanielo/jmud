package io.taanielo.jmud.core.guild.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.guild.GuildQuestPool;
import io.taanielo.jmud.core.guild.GuildRepositoryException;

/** Unit tests for {@link JsonGuildQuestPoolRepository} loading and validating guild-quest pool files. */
class JsonGuildQuestPoolRepositoryTest {

    private static Path writePool(Path dataRoot, String fileName, String json) throws Exception {
        Path dir = dataRoot.resolve("quests").resolve("guild");
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        Files.writeString(file, json);
        return file;
    }

    @Test
    void loadsObjectivesFromPoolFile(@TempDir Path dataRoot) throws Exception {
        writePool(dataRoot, "pool.json", """
            {
              "schema_version": 1,
              "type": "guild",
              "name": "Guild Hunts",
              "objectives": [
                {
                  "id": "guild-rat-purge",
                  "name": "Rat Purge",
                  "target_mob_id": "rat",
                  "target_name": "rats",
                  "required_kills": 20,
                  "gold_reward": 250,
                  "min_guild_level": 1
                },
                {
                  "id": "guild-void-vigil",
                  "name": "The Void Vigil",
                  "target_mob_id": "void-wraith",
                  "target_name": "void wraiths",
                  "required_kills": 10,
                  "gold_reward": 4000,
                  "min_guild_level": 5
                }
              ]
            }
            """);

        GuildQuestPool pool = new JsonGuildQuestPoolRepository(dataRoot).load();

        assertEquals(2, pool.objectives().size());
        assertEquals("guild-rat-purge", pool.objectives().get(0).questId());
        assertEquals("rats", pool.objectives().get(0).targetName());
        assertEquals(4000, pool.objectives().get(1).goldReward());
        assertEquals(1, pool.objectivesUpToLevel(1).size());
    }

    @Test
    void rejectsUnsupportedSchemaVersion(@TempDir Path dataRoot) throws Exception {
        writePool(dataRoot, "pool.json", """
            {
              "schema_version": 99,
              "objectives": [
                {
                  "id": "a", "name": "A", "target_mob_id": "rat", "target_name": "rats",
                  "required_kills": 5, "gold_reward": 100, "min_guild_level": 1
                }
              ]
            }
            """);

        assertThrows(GuildRepositoryException.class,
            () -> new JsonGuildQuestPoolRepository(dataRoot).load());
    }

    @Test
    void rejectsEmptyDirectory(@TempDir Path dataRoot) throws Exception {
        Files.createDirectories(dataRoot.resolve("quests").resolve("guild"));
        GuildRepositoryException ex = assertThrows(GuildRepositoryException.class,
            () -> new JsonGuildQuestPoolRepository(dataRoot).load());
        assertTrue(ex.getMessage().contains("No guild quest objectives"));
    }

    @Test
    void shippingPoolLoadsWithSpreadOfLevelBands() throws Exception {
        GuildQuestPool pool = new JsonGuildQuestPoolRepository(Path.of("data")).load();

        assertTrue(pool.objectives().size() >= 4, "at least 3-4 objectives expected");
        assertTrue(pool.objectives().stream().anyMatch(o -> o.minGuildLevel() == 1),
            "a level-1 objective must exist so low-level guilds get a target");
        assertTrue(pool.objectives().stream().anyMatch(o -> o.minGuildLevel() >= 4),
            "a high-band objective must exist so high-level guilds get a tougher target");
    }
}
