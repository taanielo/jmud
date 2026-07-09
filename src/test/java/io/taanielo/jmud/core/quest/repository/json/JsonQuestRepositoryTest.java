package io.taanielo.jmud.core.quest.repository.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.taanielo.jmud.core.quest.QuestId;
import io.taanielo.jmud.core.quest.QuestRepositoryException;
import io.taanielo.jmud.core.quest.QuestTemplate;
import io.taanielo.jmud.core.quest.QuestType;

/**
 * Unit tests for {@link JsonQuestRepository} exploration-quest loading (schema version 4).
 */
class JsonQuestRepositoryTest {

    @Test
    void loadsExplorationQuestWithRequiredRooms(@TempDir Path dataRoot) throws IOException, QuestRepositoryException {
        Path questsDir = Files.createDirectories(dataRoot.resolve("quests"));
        Files.writeString(questsDir.resolve("quest.explore-catacombs.json"), """
            {
              "schema_version": 4,
              "type": "exploration",
              "id": "explore-catacombs",
              "name": "Into the Catacombs",
              "description": "Chart every chamber of the Catacombs.",
              "required_room_ids": ["catacombs-entrance", "ossuary-hall", "burial-alcove"],
              "gold_reward": 60,
              "xp_reward": 150,
              "title_reward": "Catacomb Cartographer"
            }
            """);

        JsonQuestRepository repository = new JsonQuestRepository(dataRoot);
        QuestTemplate template = repository.findById(QuestId.of("explore-catacombs")).orElseThrow();

        assertEquals(QuestType.EXPLORATION, template.type());
        assertTrue(template.isExplorationQuest());
        assertEquals(
            java.util.List.of("catacombs-entrance", "ossuary-hall", "burial-alcove"),
            template.requiredRoomIds());
        assertEquals(60, template.goldReward());
        assertEquals(150, template.xpReward());
        assertEquals("Catacomb Cartographer", template.titleReward());
    }
}
